#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)
    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()
    podTemplate(name: 'sa-secret', cloud: cloud,
        volumes: [secretVolume(secretName: "k8s-current-cluster-kubeconfig", mountPath: '/home/jenkins/.kube')]) {
        toolsTemplate(parameters) {
            node(label) {
                body()
            }
        }
    }
}
