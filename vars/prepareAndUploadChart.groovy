#!/usr/bin/groovy
def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // Slack variables
    def slackChannel = "${env.SLACK_CHANNEL}"
    def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

    def git = new io.stakater.vc.Git()
    def helm = new io.stakater.charts.Helm()
    def common = new io.stakater.Common()
    def chartManager = new io.stakater.charts.ChartManager()
    def utils = new io.fabric8.Utils()
    def slack = new io.stakater.notifications.Slack()

    def chartRepositoryURL =  config.chartRepositoryURL ?: common.getEnvValue('CHART_REPOSITORY_URL')
    def chartName = config.chartName
    def isPublic = config.isPublic ?: false
    def packageName

    if(chartName == '') {
        error "Parameter `chartName` is required"
    }

    try {
        stage("Init Helm: ${chartName}") {
            helm.init(true)
        }

        stage("Prepare Chart: ${chartName}") {
            helm.lint(WORKSPACE, chartName.toLowerCase())
            packageName = helm.package(WORKSPACE, chartName.toLowerCase())
        }

        if(utils.isCD()) {
            stage("Upload Chart: ${chartName}") {
                String cmUsername = common.getEnvValue('CHARTMUSEUM_USERNAME')
                String cmPassword = common.getEnvValue('CHARTMUSEUM_PASSWORD')
                String publicChartRepositoryURL = config.publicChartRepositoryURL
                String publicChartGitURL = config.publicChartGitURL

                if (config.chartRepositoryURL) {
                    echo "Uploading to custom chart repository: ${chartRepositoryURL}"
                    chartManager.uploadToChartMuseum(WORKSPACE, chartName.toLowerCase(), packageName, cmUsername, cmPassword, chartRepositoryURL)
                }

                if (publicChartRepositoryURL && publicChartGitURL) {
                    echo "Uploading to public chart repository: ${publicChartRepositoryURL}"
                    echo "Public chart repository Git URL: ${publicChartGitURL}"

                    def packagedChartLocation = WORKSPACE + "/" + chartName.toLowerCase() + "/" + packageName;
                    chartManager.uploadToStakaterCharts(packagedChartLocation, publicChartRepositoryURL, publicChartGitURL)
                }
            }
        }
    } catch(e) {
        slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])
                
        def commentMessage = "[Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
        git.addCommentToPullRequest(commentMessage)

        throw e
    }

    stage("Notify") {
        def message
        if (utils.isCD()) {
            def chartVersion = packageName.subSequence(packageName.lastIndexOf("-") + 1, packageName.lastIndexOf(".tgz"))
            message = "${chartName}:${chartVersion}";
        }
        else {
            message = "Chart: ${chartName} Dry Run successful"
        }

        slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createField("ChartMuseum", message, false)])

        git.addCommentToPullRequest(message)
    }
}