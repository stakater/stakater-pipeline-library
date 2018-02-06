#!/usr/bin/groovy
package io.stakater.charts

class Helm {

    String workspace
    String chartName

    Helm(String workspace, String chartName) {
        this.workspace = workspace
        this.chartName = chartName
    }

    def lint() {
        sh """
            cd ${this.workspace}/${this.chartName}
            helm lint
        """
    }

    def package() {
        result = io.stakater.Common.shOutput """
                    cd ${workspace}/${chartName}
                    helm package .
                """

        return result.substring(result.lastIndexOf('/') + 1, result.length())
    }

}
