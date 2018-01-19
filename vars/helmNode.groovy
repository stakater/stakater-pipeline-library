#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    
    def defaultLabel = buildId('helm')
    def label = parameters.get('label', defaultLabel)

    helmTemplate(parameters) {
        node(label) {
            body()
        }
    }
}