#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    toolsWithExternalKubeTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
