def call(Map parameters = [:], body) {

    def helm = new io.stakater.charts.Helm()
    def common = new io.stakater.Common()
    def chartManager = new io.stakater.charts.ChartManager()
    def utils = new io.fabric8.Utils()

    def chartName = parameters.get('chartName', '')

    if(chartName == '') {
        error "Parameter `chartName` is required"
    }

    stage("Init Helm: ${chartName}") {
        helm.init(true)
    }

    def packageName

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

    body()
}