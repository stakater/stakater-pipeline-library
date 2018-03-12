#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    toolsTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
