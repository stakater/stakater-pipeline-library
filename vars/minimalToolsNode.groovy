#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    if ( cloud == "openshift") {
        toolsTemplateOpenshift(parameters) {
            node(label) {
                body()
            }
        }
    }
    else {
        minimalToolsTemplate(parameters) {
            node(label) {
                body()
            }
        }
    }
}