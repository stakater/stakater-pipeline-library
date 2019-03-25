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
    def splittedGitURL = publicChartGitURL.split("/")
    
    def chartRepoName = splittedGitURL[splittedGitURL.length - 1]
    chartRepoName = chartRepoName.replace(".git", "")
    echo "Parsed chart repo name from GIT URL: ${chartRepoName}"
    
    git.checkoutRepo(publicChartGitURL, "master", chartRepoName)
    sh """
        mv ${packagedChart} ${chartRepoName}/docs
        cd ${chartRepoName}
        helm repo index docs --url ${publicChartRepositryURL}
    """

    git.commitChanges(chartRepoName, "Update charts")
}

def uploadToHostedNexusRawRepository(String nexusUsername, String nexusPassword, String packagedChartLocation, String nexusChartRepoURL, String nexusChartRepoName) {
    //////////////////////////////////////////////////////////////////////////////////
    // 1st step: Upload new chart to nexus
    //////////////////////////////////////////////////////////////////////////////////
    echo "1st step: Upload new chart to nexus"
    echo "Packaged Chart Location: ${packagedChartLocation}"

    sh "curl -u ${nexusUsername}:${nexusPassword} --upload-file ${packagedChartLocation} ${nexusChartRepoURL}/repository/${nexusChartRepoName}/ -v"

    //////////////////////////////////////////////////////////////////////////////////
    // 2nd step: Fetch all the assets from nexus repo to generate new index.yaml file
    /////////////////////////////////////////////////////////////////////////////////
    echo "2nd step: Fetch all the assets from nexus repo to generate new index.yaml file"

    def response = sh(script: "curl -u ${nexusUsername}:${nexusPassword} -X GET ${nexusChartRepoURL}/service/rest/v1/assets?repository=${nexusChartRepoName} -v", returnStdout: true)
    def responseJSON = new JsonSlurperClassic().parseText(response)

    sh "mkdir nexus-charts"

    responseJSON.items.each{item -> 
        echo "URL: ${item.downloadUrl}"
        sh """
            cd nexus-charts
            curl -u ${nexusUsername}:${nexusPassword} --remote-name ${item.downloadUrl} -v
        """
    }

    //////////////////////////////////////////////////////////////////////////////////
    // 3rd step: Generate new index.yaml file, and push to nexus chart repo
    /////////////////////////////////////////////////////////////////////////////////
    echo "3rd step: Generate new index.yaml file, and push to nexus chart repo"

    sh """
        cd nexus-charts
        helm repo index . --url ${nexusChartRepoURL}/repository/${nexusChartRepoName}
        curl -u ${nexusUsername}:${nexusPassword} --upload-file index.yaml ${nexusChartRepoURL}/repository/${nexusChartRepoName}/ -v 
    """
}

return this