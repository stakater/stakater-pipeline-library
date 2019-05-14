#!/usr/bin/groovy
// Used to build maven apps, then create & upload chart

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setToolsImage(config, "stakater/builder-maven:3.5.4-jdk1.8-apline8-v0.0.3")
    stakaterPod.setDockerConfig(config)
    stakaterPod.enableMavenSettings(config)

    timestamps {
        stakaterNode(config) {
            withSCM { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                checkout scm

                def builder = new io.stakater.builder.Build()
                def docker = new io.stakater.containers.Docker()
                def stakaterCommands = new io.stakater.StakaterCommands()
                def git = new io.stakater.vc.Git()
                def slack = new io.stakater.notifications.Slack()
                def common = new io.stakater.Common()
                def utils = new io.fabric8.Utils()
                def templates = new io.stakater.charts.Templates()
                def nexus = new io.stakater.repository.Nexus()   

                String javaRepositoryURL = config.javaRepositoryURL ?: ""
                String dockerRepositoryURL = config.dockerRepositoryURL ?: ""
                Boolean runIntegrationTest = config.runIntegrationTest ?: false
                String integrationTestParams = config.integrationTestParams ?: ""

                String appName = config.appName ?: ""
                String gitUser = config.gitUser ?: "stakater-user"
                String gitEmailID = config.gitEmail ?: "stakater@gmail.com"
                String artifactType = config.artifactType ?: ".jar"

                Boolean cloneUsingToken = config.usePersonalAccessToken ?: false
                String tokenSecretName = ""
                String tokenSecret = ""

                if (cloneUsingToken) {
                    tokenSecretName = config.tokenCredentialID ?: ""
                    tokenSecret = stakaterCommands.getProviderTokenFromJenkinsSecret(tokenSecretName)
                    git.configureRepoWithCredentials(repoUrl, gitUser, tokenSecret)
                }

                Boolean notifySlack = config.notifySlack == false ? false : true
                String slackChannel = ""
                String slackWebHookURL = ""
                if (notifySlack) {
                    // Slack variables
                    slackChannel = common.getEnvValue('SLACK_CHANNEL')
                    slackWebHookURL = common.getEnvValue('SLACK_WEBHOOK_URL')
                }

                String dockerImage = ""
                String version = ""

                container(name: 'tools') {
                    String kubernetesDir = WORKSPACE + "/deployments/kubernetes"
                    String chartTemplatesDir = kubernetesDir + "/templates/chart"
                    String chartDir = kubernetesDir + "/chart"
                    String manifestsDir = kubernetesDir + "/manifests"

                    //TODO: Get correct env names
                    String prNumber = "${env.REPO_BRANCH}"

                    String imageName = repoName.split("dockerfile-").last().toLowerCase()
                    String fullAppNameWithVersion = ""

                    git.setUserInfo(gitUser, gitEmailID)
                    
                    echo "Image NAME: ${imageName}"
                    if (repoOwner.startsWith('stakater-')) {
                        repoOwner = 'stakater'
                    }
                    echo "Repo Owner: ${repoOwner}"
                    try {
                        stage('Create Version') {
                            dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"
                            // If image Prefix is passed, use it, else pass empty string to create versions
                            String imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                            version = stakaterCommands.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                            echo "Version: ${version}"
                            fullAppNameWithVersion = imageName + '-'+ version
                        }

                        stage('Build Maven Application') {
                            echo "Building Maven application"
                            builder.buildMavenApplication(version)
                        }

                        stage('Build Image') {
                            sh """
                                export DOCKER_IMAGE=${dockerImage}
                                export DOCKER_TAG=${version}
                            """
                            docker.buildImageWithTagCustom(dockerImage, version)
                            docker.pushTagCustom(dockerImage, version)
                        }

                        // If master
                        if (utils.isCD()) {
                            if (!javaRepositoryURL.equals("")) {
                                stage('Publish Artifact') {
                                    nexus.pushAppArtifact(imageName, version, javaRepositoryURL, artifactType)
                                }
                            }

                            stage("Tag") {
                                print "Pushing changes to Git"
                                if(cloneUsingToken) {
                                    // git.commitChangesUsingToken(WORKSPACE, "Update chart and version")
                                    print "Pushing Tag ${version} to Git"
                                    git.createAndPushTagUsingToken(WORKSPACE, version)
                                } else {
                                    // git.commitChanges(WORKSPACE, "Update chart and version")
                                    print "Pushing Tag ${version} to Git"
                                    git.createAndPushTag(WORKSPACE, version)
                                }
                            }

                            stage("Deploy") {
                                sh """
                                    make deploy IMAGE_NAME=${dockerImage} IMAGE_TAG=${version} NAMESPACE=sma-dev
                                """
                            }
                        }
                    }
                    catch (e) {
                        if (notifySlack) {
                            slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)], prNumber)
                        }

                        String commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"

                        if(cloneUsingToken) {
                            git.addCommentToPullRequest(commentMessage, tokenSecret)
                        } else {
                            git.addCommentToPullRequest(commentMessage)
                        }
                        throw e
                    }
                    stage('Notify') {
                        if (notifySlack) {
                            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")], prNumber)
                        }

                        String commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"

                        if(cloneUsingToken){
                            git.addCommentToPullRequest(commentMessage, tokenSecret)
                        } else {
                            git.addCommentToPullRequest(commentMessage)
                        }
                    }
                }
            }
        }
    }
}
