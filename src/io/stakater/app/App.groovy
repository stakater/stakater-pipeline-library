#!/usr/bin/groovy
package io.stakater.app

Map configure(Map parameters = [:]) {
    String appType = parameters.appType ?: "angular"

    configureByAppType(appType, parameters)

    Map container = createBuilderContainer(parameters.builderImage)
    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setDockerConfig(parameters)
    stakaterPod.addExtraContainer(parameters, container)
    stakaterPod.setPodEnvVars(parameters)

    return parameters
}

Map configureByAppType(String appType, Map parameters = [:]) {
    switch(appType) {
        case "angular":
            configureAngularApp(parameters)
        break
        case "gradle":
            configureGradleApp(parameters)
        break
        case "maven":
            configureMavenApp(parameters)
        break
        case "node":
            configureNodeApp(parameters)
        break
        case "dotnet":
            configureDotnetApp(parameters)
        break
    }
    return parameters
}

Map configureAngularApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "run build"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-angular:7.0.7-node8.16-alpine-v0.0.1"
}

Map configureGradleApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "clean build"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-gradle:3.5-jdk1.8-v2.0.1-v0.0.1"
}

Map configureMavenApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "run build"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-maven:3.5.4-jdk1.8-v2.0.1-v0.0.6"
}

Map configureNodeApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "install"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-node-8:v0.0.2"
}

Map configureDotnetApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "restore;publish -c Release -o out"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-node-8:v0.0.2"
}

Map createBuilderContainer(String image) {
    def stakaterPod = new io.stakater.pods.Pod()
    return stakaterPod.creatbuildAngularApplicationeContainer("builder", image, "/bin/sh -c", "cat", true, '/home/jenkins', true, [])
}

def createAndPushTag(Boolean cloneUsingToken, String gitDir, String version) {
    print "Pushing Tag ${version} to Git"
    if(cloneUsingToken) {
        git.createAndPushTagUsingToken(gitDir, version)
    } else {
        git.createAndPushTag(gitDir, version)
    }
}

def build(String appType, String version, String goal) {
    def builder = new io.stabuildAngularApplicationkater.builder.Build()

    switch(appType) {
        case "angular":
            builder.buildAngularApplication(version, goal)
        break
        case "gradle":
            builder.buildGradleApplication(version, goal)
        break
        case "maven":
            builder.buildMavenApplication(version, goal)
        break
        case "node":
            builder.buildNodeApplication(version, goal)
        break
        case "dotnet":
            builder.buildDotnetApplication(version, goal)
        break
    }
}

return this