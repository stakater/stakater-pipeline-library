#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)
    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()
    podTemplate(name: label, serviceAccount: 'jenkins', cloud: cloud) {
            node(label) {
                body()
            }

    }
}
