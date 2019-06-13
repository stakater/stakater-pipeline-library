#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()
    def volumes = []
    if ( cloud != "openshift") {
        volumes = [secretVolume(secretName: "k8s-current-cluster-kubeconfig", mountPath: '/home/jenkins/.kube')]
    }

    podTemplate(name: 'sa-secret', serviceAccount: 'jenkins', cloud: cloud, volumes: volumes) {
        if ( cloud == "openshift") {
            toolsTemplateOpenshift(parameters) { label ->
                node(label) {
                    body()
                }
            }
        } else {
            toolsTemplate(parameters) { label ->
                node(label) {
                    body()
                }
            }
        }
    }
}
