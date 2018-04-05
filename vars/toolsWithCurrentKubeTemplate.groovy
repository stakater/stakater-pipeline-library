#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    def toolsImage = parameters.get('toolsImage', 'stakater/pipeline-tools:1.5.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()
    
    podTemplate(name: 'sa-secret',
        volumes: [secretVolume(secretName: "k8s-current-cluster-kubeconfig", mountPath: '/home/jenkins/.kube')]) {

        toolsNode(toolsImage: toolsImage) {
            body()
        }
    }
}