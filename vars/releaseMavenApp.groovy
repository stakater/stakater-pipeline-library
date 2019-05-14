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
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm
                String parentDirectory = config.parentDirectory ?: WORKSPACE
                dir (parentDirectory) {
                    def builder = new io.stakater.builder.Build()
                    def docker = new io.stakater.containers.Docker()
                    def stakaterCommands = new io.stakater.StakaterCommands()
                    def git = new io.stakater.vc.Git()
                    def slack = new io.stakater.notifications.Slack()
                    def common = new io.stakater.Common()
                    def utils = new io.fabric8.Utils()
                    def templates = new io.stakater.charts.Templates()
                    def nexus = new io.stakater.repository.Nexus()
                    def chartManager = new io.stakater.charts.ChartManager()
                    def helm = new io.stakater.charts.Helm()

                    String chartRepositoryURL =  config.chartRepositoryURL ?: ""
                    String javaRepositoryURL = config.javaRepositoryURL ?: ""
                    String dockerRepositoryURL = config.dockerRepositoryURL ?: ""
                    Boolean runIntegrationTest = config.runIntegrationTest ?: false
                    String integrationTestParams = config.integrationTestParams ?: ""
                    String mockParams = config.mockParams ?: ""
                    String chartRepository = config.chartRepository ?: "nexus"
                    String nexusChartRepoName = config.nexusChartRepoName ?: "helm-charts"
                    String chartPackageName = ""
                    Boolean deployManifest = config.deployManifest ?: false
                    String packageCommand = config.packageCommand ?: "package"
                    String artifactType = config.artifactType ?: "jar"
                    String namespace = config.namespace ?: ""

                    String appName = config.appName ?: config.repoName
                    String gitUser = config.gitUser ?: "stakater-user"
                    String gitEmailID = config.gitEmail ?: "stakater@gmail.com"

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

                        //TODO: Get correct env names
                        String prNumber = "${env.REPO_BRANCH}"

                        String imageName = appName.split("dockerfile-").last().toLowerCase()
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
                                builder.buildMavenApplication(version, packageCommand)
                            }

                            stage('Build Image') {
                                sh """
                                    export DOCKER_IMAGE=${dockerImage}
                                    export DOCKER_TAG=${version}
                                """
                                docker.buildImageWithTagCustom(dockerImage, version)
                                docker.pushTagCustom(dockerImage, version)
                            }

                            if (! chartRepositoryURL.equals("")) {
                                stage('Package chart') {
                                    chartPackageName = chartManager.packageChart(appName, version, dockerImage, kubernetesDir)
                                }
                            }

                            if (runIntegrationTest) {
                                stage('Run Integration Tests') {
                                    echo "Installing in mock environment"
                                    sh """
                                        make install-mock IMAGE_NAME=${dockerImage} IMAGE_TAG=${version} ${mockParams}
                                    """

                                    echo "Running Integration tests for Maven application"
                                    sh """
                                        make run-integration-tests ${integrationTestParams}
                                    """
                                }
                            }

                            // If master
                            if (utils.isCD()) {
                                if (!javaRepositoryURL.equals("")) {
                                    stage('Publish Artifact') {
                                        nexus.pushAppArtifact(imageName, version, javaRepositoryURL, artifactType)
                                    }
                                }

                                if (! chartRepositoryURL.equals("")) {
                                    stage('Upload Helm Chart') {
                                        chartManager.uploadChart(chartRepository, chartRepositoryURL, kubernetesDir,
                                                    nexusChartRepoName, appName, chartPackageName)
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

                                if (deployManifest) {
                                    stage("Deploy") {
                                        sh """
                                            make deploy IMAGE_NAME=${dockerImage} IMAGE_TAG=${version} NAMESPACE=${namespace}
                                        """
                                    }
                                }
                            } else {
                                if (runIntegrationTest) {
                                    echo "As PR, so rolling back to stable version"
                                    sh """
                                        make rollback
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
}
