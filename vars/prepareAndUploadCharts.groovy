#!/usr/bin/groovy

def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsNode(toolsImage: 'stakater/pipeline-tools:1.5.1') {
        container(name: 'tools') {
            withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                def charts = config.charts.toArray()
                def templates = new io.stakater.charts.Templates()
                def common = new io.stakater.Common()
                def git = new io.stakater.vc.Git()
                def utils = new io.fabric8.Utils()


                def chartVersion = common.shOutput("jx-release-version --gh-owner=${repoOwner} --gh-repository=${repoName} --version-file=.version")

                for(int i = 0; i < charts.size(); i++) {
                    String chart = charts[i]

                    templates.renderChart("${chart}-templates", ".", chart, chartVersion)

                    prepareAndUploadChart {
                        chartName = chart
                    }
                }

                // Only commit and release if in CD
                if(utils.isCD()) {
                    sh """
                        echo -n "${chartVersion}" > .version
                    """

                    def commitMessage = "Bump Version to ${chartVersion}"
                    git.commitChanges(WORKSPACE, commitMessage)
                    print "Pushing Tag ${chartVersion} to Git"
                    git.createTagAndPush(WORKSPACE, chartVersion, commitMessage)
                    git.createRelease(chartVersion)
                }
            }
        }
    }
}