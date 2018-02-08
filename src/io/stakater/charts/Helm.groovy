#!/usr/bin/groovy
package io.stakater.charts

//Set the static reference in the script
Script.environment  = this

public class Script {
    public static environment
}

class Helm {

    String workspace
    String chartName

    Helm(String workspace, String chartName) {
        this.workspace = workspace
        this.chartName = chartName
    }

    static def init() {
        init(false)
    }

    static def init(boolean clientOnly) {
        String initCmd = "helm init"
        if(clientOnly) {
            initCmd += " --client-only"
        }
        Script.environment.sh """
            ${initCmd}
        """
    }

    def lint() {
        Script.environment.sh """
            cd ${this.workspace}/${this.chartName}
            helm lint
        """
    }

    def package() {
        result = io.stakater.Common.shOutput """
                    cd ${this.workspace}/${this.chartName}
                    helm package .
                """

        return result.substring(result.lastIndexOf('/') + 1, result.length())
    }

}
