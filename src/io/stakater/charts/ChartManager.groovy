#!/usr/bin/groovy
package io.stakater.charts

def uploadToChartMuseum(String location, String chartName, String fileName) {
    def chartMuseum = new io.stakater.charts.ChartMuseum()
    chartMuseum.upload(location, chartName, fileName)
}

def uploadToChartMuseum(String location, String chartName, String fileName, String cmUsername, String cmPassword) {
    def chartMuseum = new io.stakater.charts.ChartMuseum()
    chartMuseum.upload(location, chartName, fileName, cmUsername, cmPassword)
}

def uploadToGithub(String location, String packagedChart) {
    sh """
        mv ${packagedChart} ${location}/docs
        cd ${location}
        helm repo index docs --url https://stakater.github.com/charts
    """
    def git = new io.stakater.vc.Git()

    git.commitChanges(location, "Update charts")
}

return this
