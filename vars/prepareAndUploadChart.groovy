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

    def chartName = config.chartName
    def packageName

    if(chartName == '') {
        error "Parameter `chartName` is required"
    }

    try {
        stage("Init Helm: ${chartName}") {
            helm.init(true)
        }

        stage("Prepare Chart: ${chartName}") {
            helm.lint(WORKSPACE, chartName)
            packageName = helm.package(WORKSPACE, chartName)
        }

        if(utils.isCD()) {
            stage("Upload Chart: ${chartName}") {
                String cmUsername = common.getEnvValue('CHARTMUSEUM_USERNAME')
                String cmPassword = common.getEnvValue('CHARTMUSEUM_PASSWORD')
                chartManager.uploadToChartMuseum(WORKSPACE, chartName, packageName, cmUsername, cmPassword)
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
            def chartVersion = packageName.subSequence(packageName.indexOf("-") + 1, packageName.indexOf(".tgz"))
            message = "${chartName}:${chartVersion}";
        }
        else {
            message = "Chart Dry Run successful"
        }

        slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createField("ChartMuseum", message, false)])

        git.addCommentToPullRequest(message)
    }
}