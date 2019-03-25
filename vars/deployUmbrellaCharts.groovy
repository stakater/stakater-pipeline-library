#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: 'stakater/pipeline-tools:v1.17.0'
    chartName = config.chartName
    runPreInstall = config.runPreInstall ?: false
    notifyOnSlack = !config.notifyOnSlack ? config.notifyOnSlack : true

    toolsWithCurrentKubeNode(toolsImage: toolsImage) { label ->
        node(label) {
            container(name: 'tools') {
                withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                    def slackChannel = "${env.SLACK_CHANNEL}"
                    def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"
                    def slack = new io.stakater.notifications.Slack()
                    def git = new io.stakater.vc.Git()
                    def common = new io.stakater.Common()
                    def utils = new io.fabric8.Utils()
                    def stakaterCommands = new io.stakater.StakaterCommands()

                    try {
                        def versionFile = ".version"
                        def imageName = repoName.split("dockerfile-").last().toLowerCase()                
                        def dockerImage = ""
                        def version = ""
                        def prNumber = "${env.REPO_BRANCH}"                        
                        def dockerRepositoryURL = config.dockerRepositoryURL ?: "docker.io"

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
                        if (utils.isCD()) {
                            String repoDir = WORKSPACE

                            stage('Create Version') {
                                dockerImage = "${repoOwner.toLowerCase()}/${imageName}"
                                // If image Prefix is passed, use it, else pass empty string to create versions
                                def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                                version = stakaterCommands.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                                echo "Version: ${version}"                       
                                fullAppNameWithVersion = imageName + '-'+ version
                                echo "Full App name: ${fullAppNameWithVersion}"
                            }
        
                            stage('Deploy Chart') {
                                if(chartName){
                                    sh """
                                        make install CHART_NAME=${chartName}
                                    """
                                } else {
                                    sh """
                                        make install-all
                                    """
                                }   
                            }

                            stage('Tag and Release') {
                                print "Generating New Version"

                                sh """
                                    echo "${version}" > ${versionFile}
                                    cd ${repoDir}
                                """
                                git.commitChanges(repoDir, "Bump Version to ${version}")
                                print "Pushing Tag ${version} to Git"
                                git.createTagAndPush(repoDir, version)
                                stakaterCommands.createGitHubRelease(version)
                            }
                        } else {
                            stage('Dry Run Chart') {
                                print "Branch is not master so just dry running"
                                if(chartName){
                                    sh """
                                        make install-dry-run CHART_NAME=${chartName}
                                        echo "Dry run successful"
                                    """
                                } else {
                                    sh """
                                        make install-all-dry-run
                                        echo "Dry run successful"
                                    """
                                }
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
}