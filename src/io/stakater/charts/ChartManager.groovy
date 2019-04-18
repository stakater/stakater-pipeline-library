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

/**
* @nexusUsername = Username for nexus repo
* @nexusPassword = Password for nexus repo
* @packagedChartLocation = Path for the newly created chart to push to nexus including file name. Must end with .tgz
* @nexusURL = Base URL of the nexus
* @nexusChartRepoName = Name of the nexus repository where we want to push our charts
**/

def uploadToHostedNexusRawRepository(String nexusUsername, String nexusPassword, String packagedChartLocation, String nexusURL, String nexusChartRepoName) {
    //////////////////////////////////////////////////////////////////////////////////
    // 1st step: Upload new chart to nexus
    //////////////////////////////////////////////////////////////////////////////////
    echo "Upload new chart to nexus"
    echo "Packaged Chart Location: ${packagedChartLocation}"

    sh "curl -u ${nexusUsername}:${nexusPassword} --upload-file ${packagedChartLocation} ${nexusURL}/repository/${nexusChartRepoName}/ -v"

    //////////////////////////////////////////////////////////////////////////////////
    // 2nd step: Fetch all the assets from nexus repo to generate new index.yaml file
    /////////////////////////////////////////////////////////////////////////////////
    echo "Fetch all the assets from nexus repo to generate new index.yaml file"

    sh """

        nexus_url=${nexusURL}/service/rest/v1/assets?repository=${nexusChartRepoName}
        auth=${nexusUsername}:${nexusPassword}

        getContinuationToken() {
            continuation_token=\$(cat output.json | jq -r '.continuationToken')
        }

        downloadItems() {
            cat output.json | jq -r '.items[].downloadUrl' | xargs -I % curl -u "\${auth}" --remote-name % -v
        }

        getItemList() {
            paginated_url=\${nexus_url}

            if [[ ! -z "\$continuation_token" ]]
            then
                paginated_url="\${nexus_url}&continuationToken=\${continuation_token}"
            fi

            curl -u "\${auth}" -X GET "\${paginated_url}" -v > output.json
        }

        mkdir -p nexus-charts
        cd nexus-charts

        while : ; do
            getItemList
            downloadItems
            getContinuationToken

            [[ ! -z "\$continuation_token" ]] || break
        done
    """

    //////////////////////////////////////////////////////////////////////////////////
    // 3rd step: Generate new index.yaml file, and push to nexus chart repo
    /////////////////////////////////////////////////////////////////////////////////
    echo "Generate new index.yaml file, and push to nexus chart repo"

    sh """
        cd nexus-charts
        helm repo index . --url ${nexusURL}/repository/${nexusChartRepoName}
        curl -u ${nexusUsername}:${nexusPassword} --upload-file index.yaml ${nexusURL}/repository/${nexusChartRepoName}/ -v 
    """
}

return this