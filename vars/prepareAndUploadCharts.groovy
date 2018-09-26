#!/usr/bin/groovy

def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsNode(toolsImage: 'stakater/pipeline-tools:1.14.1') {
        container(name: 'tools') {
            withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                def charts = config.charts.toArray()
                def makePublic = config.isPublic ?: false
                def templates = new io.stakater.charts.Templates()
                def common = new io.stakater.Common()
                def git = new io.stakater.vc.Git()
                def utils = new io.fabric8.Utils()
                def slack = new io.stakater.notifications.Slack()
                def stakaterCommands = new io.stakater.StakaterCommands()

                // Slack variables
                def slackChannel = "${env.SLACK_CHANNEL}"
                def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"
                def versionInFile = stakaterCommands.ReadVersionFromFile('.version')

                def chartVersion = common.shOutput("stk generate version --version-file .version --version ${versionInFile}")
                println "Version generated from stk version:  ${chartversion}"

                for(int i = 0; i < charts.size(); i++) {
                    String chart = charts[i]

                    templates.renderChart("${chart}-templates", ".", chart, chartVersion)

                    prepareAndUploadChart {
                        chartName = chart
                        isPublic = makePublic
                    }
                    echo "Removing packaged chart"
                    sh """
                        cd ${chart}
                        rm -rf *.tgz
                    """
                }

                try {
                    // Only commit and release if in CD
                    if(utils.isCD()) {
                        sh """
                            echo -n "${chartVersion}" > .version
                            # Remove chartmuseum credential files
                            rm -rf CHARTMUSEUM_*
                        """

                        def commitMessage = "Bump Version to ${chartVersion}"
                        git.commitChanges(WORKSPACE, commitMessage)
                        print "Pushing Tag ${chartVersion} to Git"
                        git.createTagAndPush(WORKSPACE, chartVersion, commitMessage)
                        git.createRelease(chartVersion)
                    }
                }
                catch(e) {
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])
            
                    def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }
            }
        }
    }
}