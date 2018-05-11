#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    podTemplate(name: 'sa-secret',
        volumes: [secretVolume(secretName: "k8s-current-cluster-kubeconfig", mountPath: '/home/jenkins/.kube')]) {
        toolsTemplate(parameters) {
            node(label) {
                body()
            }
        }
    }
}
