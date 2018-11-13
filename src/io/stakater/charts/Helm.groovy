#!/usr/bin/groovy
package io.stakater.charts


def init() {
    init(false)
}

def init(boolean clientOnly) {
    String initCmd = "helm init"
    if(clientOnly) {
        initCmd += " --client-only"
    }
    sh """
        ${initCmd}
    """
}

def lint(String location, String chartName) {
    sh """
        cd ${location}/${chartName}
        helm lint
    """
}

def 'package'(String location, String chartName) {
    result = new io.stakater.Common().shOutput """
                cd ${location}/${chartName}
                helm package .
            """

    return result.substring(result.lastIndexOf('/') + 1, result.length())
}

def 'package'(String location, String chartName, String version) {
    result = new io.stakater.Common().shOutput """
                cd ${location}/${chartName}
                helm package --version ${version} .
            """

    return result.substring(result.lastIndexOf('/') + 1, result.length())
}

def addRepo(String name, String url) {
    sh "helm repo add ${name} ${url}"
    updateRepos()
}

def updateRepos() {
    sh "helm repo update"
}

return this
