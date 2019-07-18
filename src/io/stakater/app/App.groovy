#!/usr/bin/groovy
package io.stakater.app

Map configure(Map parameters = [:]) {
    String appType = parameters.appType ?: "angular"

    configureByAppType(appType, parameters)

    Map container = createBuilderContainer(parameters.builderImage)
    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setDockerConfig(parameters)
    stakaterPod.addExtraContainer(parameters, container)

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

Map createBuilderContainer(String image) {
    def stakaterPod = new io.stakater.pods.Pod()
    return stakaterPod.createContainer("builder", image, "/bin/sh -c", "cat", true, '/home/jenkins', true, [])
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
    def builder = new io.stakater.builder.Build()

    switch(appType) {
        case "angular":
            builder.buildAngularApplication(version, goal)
        break
    }
}

return this