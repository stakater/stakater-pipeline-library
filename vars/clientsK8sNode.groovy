#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('clientsK8s')
    def label = parameters.get('label', defaultLabel)

    clientsK8sTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
