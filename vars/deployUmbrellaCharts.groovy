#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: 'stakater/pipeline-tools:v1.17.0'
    chartName = config.chartName
    runPreInstall = config.runPreInstall ?: false
    notifyOnSlack = !config.notifyOnSlack ? config.notifyOnSlack : true

    toolsWithCurrentKubeNode(toolsImage: toolsImage) { label ->
        node(label) {
            container ("maven") {
                sh """echo 'hello'"""
            }
        }
    }
}