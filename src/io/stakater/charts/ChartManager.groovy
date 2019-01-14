#!/usr/bin/groovy
package io.stakater.charts

def uploadToChartMuseum(String location, String chartName, String fileName, String chartRepositoryURL) {
    def chartMuseum = new io.stakater.charts.ChartMuseum()
    chartRepositoryURL = chartRepositoryURL + '/api/charts'
    chartMuseum.upload(location, chartName, fileName, chartRepositoryURL)
}

def uploadToChartMuseum(String location, String chartName, String fileName, String cmUsername, String cmPassword, String chartRepositoryURL) {
    def chartMuseum = new io.stakater.charts.ChartMuseum()
    chartRepositoryURL = chartRepositoryURL + '/api/charts'
    chartMuseum.upload(location, chartName, fileName, chartRepositoryURL, cmUsername, cmPassword)
}

def uploadToStakaterCharts(String packagedChart, String publicChartRepositryURL, String publicChartGitURL) {
    def git = new io.stakater.vc.Git()
    def splittedGitURL = publicChartGitURL.split('/')
    
    def chartRepoName = splittedGitURL[splittedGitURL.length - 1]
    chartRepoName = chartRepoName.replace(".git", "")
    echo "Parsed chart repo name from GIT URL: "${chartRepoName}""
    
    git.checkoutRepo(publicChartGitURL, "master", chartRepoName)
    sh """
        mv ${packagedChart} ${chartRepoName}/docs
        cd ${chartRepoName}
        helm repo index docs --url ${publicChartRepositryURL}
    """

    git.commitChanges(chartRepoName, "Update charts")
}

return this