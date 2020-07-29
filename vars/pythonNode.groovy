#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('python')
    def label = parameters.get('label', defaultLabel)

    pythonTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
