#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)
    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()
    echo "using cloud : " + cloud
    podTemplate(name: label, serviceAccount: 'jenkins', namespace: 'qa-1', cloud: cloud,
    containers: [containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat')
    ]) {
        body.call(label)
    }
}
