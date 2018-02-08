#!/usr/bin/groovy
package io.stakater.charts

import io.stakater.Common

String workspace
String chartName
def steps

Helm(def steps, String workspace, String chartName) {
    this.steps = steps
    this.workspace = workspace
    this.chartName = chartName
}

static def init(def steps) {
    init(steps, false)
}

static def init(def steps, boolean clientOnly) {
    String initCmd = "helm init"
    if(clientOnly) {
        initCmd += " --client-only"
    }
    steps.sh """
        ${initCmd}
    """
}

def lint() {
    steps.sh """
        cd ${this.workspace}/${this.chartName}
        helm lint
    """
}

return this