#!/usr/bin/groovy
package io.stakater.pods

def setToolsImage(Map parameters = [:], String image) {
    setDefaultContainer(parameters)
    parameters.podContainers.defaultContainer.image = image
}

Map setDockerConfig(Map parameters = [:]) {
    setPodVolumes(parameters)
    setDefaultContainerEnvVarsConfig(parameters)

    // Don't mount docker socket if useBuildah is true or isDockerMount is false
    if (!parameters.get('useBuildah', false ) && parameters.get('isDockerMount', true )) {
        parameters.podVolumes.isDockerMount = true
        parameters.podContainers.defaultContainer.envVarsConfig.isDocker = true
    }

    // Don't mount docker config if isDockerConfig exists and is false
    if (parameters.get('isDockerConfig', true )) {
        parameters.podVolumes.isDockerConfig = true
    }
    return parameters
}

def enableMavenSettings(Map parameters = [:]) {
    setPodVolumes(parameters)
    setDefaultContainerEnvVarsConfig(parameters)

    parameters.podVolumes.isMaven = true
    parameters.podVolumes.isMavenLocalRepo = true
    parameters.podContainers.defaultContainer.envVarsConfig.isMaven = true
}

def enableGradleSettings(Map parameters = [:]) {
    setPodVolumes(parameters)
    setDefaultContainerEnvVarsConfig(parameters)

    parameters.podVolumes.isGradleLocalRepo = true
}

def enableChartMuseum(Map parameters = [:]) {
    setPodEnvVars(parameters)
    parameters.podEnvVars.isChartMuseum = true
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

def setPodEnvVars(Map parameters) {
    if ( ! parameters.get('podEnvVars', false) ) {
        parameters.podEnvVars = [:]
        
        if (parameters.get('notifySlack', false )) {
            parameters.podEnvVars['isNotifySlack'] = true
        }
    }
}

def addExtraContainer(Map parameters, Map container) {
    if ( ! parameters.get('podContainers', false) ) {
        parameters.podContainers = [:]
    }
    if ( ! parameters.podContainers.get('additionalContainers', false) ) {
        parameters.podContainers.additionalContainers = []
    }
    parameters.podContainers.additionalContainers.add(container)
}

Map createContainer(String name, String image, String command, String args, Boolean privileged, String workingDir, Boolean ttyEnabled, ArrayList<Map> envVars) {
    return [
        name: name,
        image: image,
        command: command,
        args: args,
        privileged: privileged,
        workingDir: workingDir,
        ttyEnabled: ttyEnabled,
        envVars: envVars
    ]
}

return this