#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: 'stakater/pipeline-tools:1.16.0'
    chartName = config.chartName ?: 'global'
    runPreInstall = config.runPreInstall ?: false
    notifyOnSlack = config.notifyOnSlack ?: true

    toolsWithCurrentKubeNode(toolsImage: toolsImage) {
        container(name: 'tools') {
            withCurrentRepo(type: 'go') { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                def slackChannel = "${env.SLACK_CHANNEL}"
                def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"
                def slack = new io.stakater.notifications.Slack()
                def git = new io.stakater.vc.Git()
                def common = new io.stakater.Common()

                try {
                    def versionFile = ".version"
                    def version = common.shOutput("cat ${versionFile}")

                    stage('Run Prerequisites') {
                        sh """
                            echo "Importing keys"
                            gpg --import /usr/local/bin/pgp-configuration/*.asc
                            make setup
                            make install-helm-secrets
                            cd ~/${repoName}
                        """

                        if(runPreInstall){
                            sh """
                                make run-pre-install
                            """
                        }
                    }

                    print "Branch: ${repoBranch}"
                    if (repoBranch.equals("master")){
                        String repoDir = WORKSPACE
                        stage('Deploy Chart') {
                            sh """
                                make install CHART_NAME=${chartName}
                            """
                        }

                        stage('Tag and Release') {
                            print "Generating New Version"
                            version = common.shOutput("jx-release-version --gh-owner=${repoOwner} --gh-repository=${repoName} --version-file ${versionFile}")
                            sh """
                                echo "${version}" > ${versionFile}
                                cd ${repoDir}
                            """
                            git.commitChanges(repoDir, "Bump Version to ${version}")
                            print "Pushing Tag ${version} to Git"
                            git.createTagAndPush(repoDir, version)
                            git.createRelease(version)
                        }
                    } else {
                        stage('Dry Run Chart') {
                            print "Branch is not master so just dry running"
                            sh """
                                make install-dry-run CHART_NAME=${chartName}
                                echo "Dry run successful"
                            """
                        }
                    }

                    if (notifyOnSlack){
                        stage('Notify') {
                            def message
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
                } catch(e) {
                    if (notifyOnSlack){
                        slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])
                    }

                    def commentMessage = "[Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }
            }
        }
    }
}