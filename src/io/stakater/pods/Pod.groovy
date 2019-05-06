#!/usr/bin/groovy
package io.stakater.pods

def setToolsImage(Map parameters = [:], String image) {
    setDefaultContainer(parameters)
    parameters.podContainers.defaultContainer.image = image
}

def mountDockerSocket(Map parameters = [:]) {
    setPodVolumes(parameters)
    parameters.podVolumes.isDockerMount = true

}

def setDockerConfig(Map parameters = [:]) {
    mountDockerSocket(parameters)
    setDefaultContainerEnvVarsConfig(parameters)

    parameters.podVolumes.isDockerConfig = true
    parameters.podContainers.defaultContainer.envVarsConfig.isDocker = true
}

def setDefaultContainerEnvVarsConfig(Map parameters) {
    setDefaultContainer(parameters)

    if ( ! parameters.podContainers.defaultContainer.get('envVarsConfig', false) ) {
        parameters.podContainers.defaultContainer.envVarsConfig = [:]
    }
}

def setDefaultContainer(Map parameters) {
    setPodContainers(parameters)

    if ( ! parameters.get('podContainers').get('defaultContainer', false) ) {
        parameters.podContainers.defaultContainer = [:]
    }
}

def setPodContainers(Map parameters) {
    if ( ! parameters.get('podContainers', false) ) {
        parameters.podContainers = [:]
    }
}

def setPodVolumes(Map parameters) {
    if ( ! parameters.get('podVolumes', false) ) {
        parameters.podVolumes = [:]
    }
}

return this