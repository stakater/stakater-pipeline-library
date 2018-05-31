#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def helmRepoName = config.helmRepoName ?: 'chartmuseum '
    def helmRepoUrl = config.helmRepoUrl ?: 'http://chartmuseum'

    def utils = new io.fabric8.Utils()

    toolsWithExternalKubeNode(toolsImage: 'stakater/pipeline-tools:1.8.1') {
        container(name: 'tools') {
            withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                String workspaceDir = WORKSPACE
                String manifestsDir = workspaceDir + "/manifests/"

                // Slack variables
                def slackChannel = "${env.SLACK_CHANNEL}"
                def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

                def git = new io.stakater.vc.Git()
                def slack = new io.stakater.notifications.Slack()
                def landscaper = new io.stakater.charts.Landscaper()
                def helm = new io.stakater.charts.Helm()
                def common = new io.stakater.Common()

                try {
                    stage('Init Helm') {
                        // Sleep is needed for the first time because tiller pod might not be ready instantly
                        helm.init(false)

                        sh "sleep 30s"

                        helm.addRepo(helmRepoName, helmRepoUrl)
                    }

                    stage('Dry Run Charts') {
                        landscaper.apply(manifestsDir, true)
                    }

                    if(utils.isCD()) {
                        stage('Install Charts') {
                            landscaper.apply(manifestsDir, false)
                        }

                        git.tagAndRelease(repoName, repoOwner)
                    }
                } catch(e) {
                    //TODO: Extract test result and send in notification
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                    def commentMessage = "[Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }

                stage('Notify') {
                    def message
                    def versionFile = ".version"
                    def version = common.shOutput("cat ${versionFile}")
                    if (utils.isCD()) {
                        message = "Release ${repoName} ${version}"
                    }
                    else {
                        message = "Dry Run successful"
                    }
                    slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createField("Message", message, false)])

                    git.addCommentToPullRequest(message)
                }
            }
        }
    }
}
