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

return this
