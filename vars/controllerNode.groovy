#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    controllerTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
