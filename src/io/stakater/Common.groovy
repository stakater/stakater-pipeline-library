#!/usr/bin/groovy
package io.stakater


def shOutput(String command) {
    return sh(
        script: """
            ${command}
        """,
        returnStdout: true).toString().trim()
}

def getEnvValue(String key) {
    sh "echo \$${key} > ${key}"
    value = readFile(key).trim()
    sh "rm ${key}"
    return value
}

def setToolsImage(Map parameters = [:], String image) {
    if ( ! parameters.get('podContainers', false) ) {
        parameters.podContainers = [:]
    }
    if ( ! parameters.get('podContainers').get('defaultContainer', false) ) {
        parameters.podContainers.defaultContainer = [:]
    }

    parameters.podContainers.defaultContainer.image = image
}

return this
