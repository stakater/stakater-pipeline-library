#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

    def defaultLabel = buildId('stakater-node')
    def label = parameters.get('label', defaultLabel)

    stakaterPodTemplate(parameters) {
        node(label) {
            body()
        }
    }
}