#!/usr/bin/groovy
package io.stakater.app

enum AppType {
    ANGULAR

    @Override
    static AppType valueOf(String value) {
        switch(value) {
            case "ANGULAR":
                return AppType.ANGULAR;
            break;
        }
        throw new IllegalArgumentException('Unknown AppType ' + value + '.')
    }
}

this.AppType = AppType

Map configure(Map parameters = [:]) {
    AppType appType = (parameters.appType ?: "ANGULAR") as AppType

    configureByAppType(appType, parameters)

    return parameters
}

Map configureByAppType(AppType appType, Map parameters = [:]) {
    switch(appType) {
        case AppType.ANGULAR:
            configureAngularApp(parameters)
        break
    }
    return parameters
}

Map configureAngularApp(Map parameters = [:]) {

    parameters.goal = parameters.goal ?: "run build"
    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setDockerConfig(parameters)

    parameters.builderImage = parameters.builderImage ?: "stakater/builder-angular:7.0.7-node8.16-alpine-v0.0.1"
    Map container = createAngularBuilderContainer(parameters.builderImage)
    stakaterPod.addExtraContainer(container)
}

Map createAngularBuilderContainer(String image) {
    return createBuilderContainer(image)
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

def build(AppType appType, String version, String goal) {
    def builder = new io.stakater.builder.Build()

    switch(appType) {
        case AppType.ANGULAR:
            builder.buildAngularApplication(version, goal)
        break
    }
}

return this