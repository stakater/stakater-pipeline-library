#!/usr/bin/groovy
package io.stakater.charts


def pushToChartMuseum(String location, String chartName) {
    def helm = new io.stakater.charts.Helm()
    helm.lint(location, chartName)
    String fileName = helm.package(location, chartName)

    def chartMuseum = new io.stakater.charts.ChartMuseum()
    chartMuseum.upload(location, chartName, fileName)
}

return this
