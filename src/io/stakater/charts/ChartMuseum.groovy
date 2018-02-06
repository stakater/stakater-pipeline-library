#!/usr/bin/groovy
package io.stakater.charts

class ChartMuseum {

    private static String DEFAULT_URL = "http://chartmuseum/api/charts"
    String chartMuseumUrl

    ChartMuseum() {
        this.chartMuseumUrl = DEFAULT_URL
    }

    ChartMuseum(String chartMuseumUrl) {
        this.chartMuseumUrl = chartMuseumUrl
    }

    def upload(String workspace, String chartName, String fileName) {
        sh """
            cd ${workspace}/${chartName}
            curl -L --data-binary \"@${fileName}\" ${chartMuseumUrl}
        """
    }

}
