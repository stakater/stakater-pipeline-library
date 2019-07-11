#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setDockerConfig(config)

    timestamps {
        stakaterNode(config) {

            def builder = new io.stakater.builder.Build()
            def docker = new io.stakater.containers.Docker()
            def stakaterCommands = new io.stakater.StakaterCommands()
            def git = new io.stakater.vc.Git()
            def slack = new io.stakater.notifications.Slack()
            def common = new io.stakater.Common()
            def utils = new io.fabric8.Utils()
            def templates = new io.stakater.charts.Templates()

            // Slack variables
            Boolean notifySlack = config.notifySlack == false ? false : true
            String slackChannel = ""
            String slackWebHookURL = ""
            if (notifySlack) {
                // Slack variables
                slackChannel = common.getEnvValue('SLACK_CHANNEL')
                slackWebHookURL = common.getEnvValue('SLACK_WEBHOOK_URL')
            }

            String dockerRepositoryURL = config.dockerRepositoryURL ?: ""
            String gitUser = config.gitUser ?: "stakater-user"
            String gitEmailID = config.gitEmail ?: "stakater@gmail.com"
            String appName = config.appName ?: ""

            Boolean cloneUsingToken = config.usePersonalAccessToken ?: false
            String tokenSecretName = ""
            String tokenSecret = ""
            String dockerImage = ""
            String version = ""

            git.setUserInfo(gitUser, gitEmailID)

            container(name: 'tools') {
                withSCM { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                    checkout scm

                    if (cloneUsingToken) {
                        tokenSecretName = config.tokenCredentialID ?: ""
                        tokenSecret = stakaterCommands.getProviderTokenFromJenkinsSecret(tokenSecretName)
                        git.configureRepoWithCredentials(repoUrl, gitUser, tokenSecret)
                    }
                
                    String kubernetesDir = WORKSPACE + "/deployments/kubernetes"
                    String chartTemplatesDir = kubernetesDir + "/templates/chart"
                    String chartDir = kubernetesDir + "/chart"
                    String manifestsDir = kubernetesDir + "/manifests"

                    String imageName = repoName.split("dockerfile-").last().toLowerCase()
                    String fullAppNameWithVersion = ""
                    
                    String prNumber = "${env.REPO_BRANCH}"                        

                    echo "Image NAME: ${imageName}"
                    if (repoOwner.startsWith('stakater-')){
                        repoOwner = 'stakater'
                    }
                    echo "Repo Owner: ${repoOwner}" 
                    try {
                        stage('Create Version'){
                            dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"
                            // If image Prefix is passed, use it, else pass empty string to create versions
                            String imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''
                            version = stakaterCommands.getImageVersionForNodeCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                            echo "Version: ${version}"                       
                            fullAppNameWithVersion = imageName + '-'+ version
                        }

                        stage('Image build & push') {
                            sh """
                                export DOCKER_IMAGE=${dockerImage}
                                export DOCKER_TAG=${version}
                            """
                            docker.buildImageWithTagCustom(dockerImage, version)
                            docker.pushTagCustom(dockerImage, version)
                        }
                        
                        // If master
                        if (utils.isCD()) {
                            stage("Create Git Tag"){
                                print "Pushing Tag ${version} to Git"
                                if(cloneUsingToken) {
                                    git.createAndPushTagUsingToken(WORKSPACE, version)
                                } else {
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
                        if(cloneUsingToken) {
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
