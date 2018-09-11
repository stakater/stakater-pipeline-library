#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def versionPrefix = config.versionPrefix ?: '1.0'

    toolsNode(toolsImage: 'stakater/pipeline-tools:1.5.2') {
        def docker = new io.stakater.containers.Docker()
        def stakaterCommands = new io.stakater.StakaterCommands()
        def git = new io.stakater.vc.Git()
        def slack = new io.stakater.notifications.Slack()
        def common = new io.stakater.Common()

        // Slack variables
        def slackChannel = "${env.SLACK_CHANNEL}"
        def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

        def dockerRegistryURL = config.dockerRegistryURL ?: common.getEnvValue('DOCKER_REGISTRY_URL')

        container(name: 'tools') {
            withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                def imageName = repoName.split("dockerfile-").last().toLowerCase()    
                def dockerImage = "${dockerRegistryURL}/${repoOwner.toLowerCase()}/${imageName}"
                def dockerImageVersion = config.imageVersion ?: stakaterCommands.getBranchedVersion("${versionPrefix}.${env.BUILD_NUMBER}")
                println "Hello Semi"
                println "${versionPrefix}"
                println "${env.BUILD_NUMBER}"

                try {
                    stage('Canary Release') {
                        echo "Version: ${dockerImageVersion}"                        
                        docker.buildImageWithTagCustom(dockerImage, dockerImageVersion)
                        docker.pushTagCustom(dockerImage, dockerImageVersion)

                    }
                }
                catch (e) {
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                    def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }

                stage('Notify') {
                    slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${dockerImageVersion}")])

                    def commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${dockerImageVersion}`"
                    git.addCommentToPullRequest(commentMessage)
                }
            }
        }
    }
}
