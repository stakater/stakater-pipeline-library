#!/usr/bin/groovy
package io.stakater.charts

import io.stakater.Common

String workspace
String chartName
def steps

Helm(def _steps, String _workspace, String _chartName) {
    this.steps = _steps
    this.workspace = _workspace
    this.chartName = _chartName
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