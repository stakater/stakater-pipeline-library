#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

    podTemplate(name: 'sa-secret', serviceAccount: 'jenkins', cloud: cloud) {
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