#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    toolsTemplate(parameters) {
        echo "inside tools template"
        node('master') {
            echo "inside node"
            body()
        }
    }
}
