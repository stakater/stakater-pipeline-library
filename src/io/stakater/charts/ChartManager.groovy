#!/usr/bin/groovy
package io.stakater.charts

class ChartManager {

    def pushToChartMuseum(String workspace, String chartName) {
        def helm = new io.stakater.charts.Helm(workspace, chartName)
        helm.lint()
        String fileName = helm.package()

        def chartMuseum = new io.stakater.charts.ChartMuseum()
        chartMuseum.upload(workspace, chartName, fileName)
    }

}