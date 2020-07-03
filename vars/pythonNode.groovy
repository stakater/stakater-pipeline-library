#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('python')
    def label = parameters.get('label', defaultLabel)

    nodejsTemplate(parameters) {
        node(label) {
            body()
        }
    }
}