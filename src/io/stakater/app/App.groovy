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
    parameters.goal = parameters.goal ?: "clean package"
    parameters.isMaven = true
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-maven:3.5.4-jdk1.8-v2.0.1-v0.0.6"
}

Map configureNodeApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "install"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-node-8:v0.0.2"
}

Map configureDotnetApp(Map parameters = [:]) {
    parameters.goal = parameters.goal ?: "restore;publish -c Release -o out"
    parameters.builderImage = parameters.builderImage ?: "stakater/builder-dotnet:2.2-centos7"
}

Map createBuilderContainer(String image) {
    def stakaterPod = new io.stakater.pods.Pod()
    return stakaterPod.createContainer("builder", image, "/bin/sh -c", "cat", true, '/home/jenkins', true, [])
}

def createAndPushTag(Boolean cloneUsingToken, String gitDir, String version) {
    def git = new io.stakater.vc.Git()
    print "Pushing Tag ${version} to Git"
    if(cloneUsingToken) {
        git.createAndPushTagUsingToken(gitDir, version)
    } else {
        git.createAndPushTag(gitDir, version)
    }
}

def parseGoalEnvironment(String goal){
    String parsedGoal = goal
    String branch = env.BRANCH_NAME

    // In case of master branch replace any and all occurrences of #ENVIRONMENT with prod else replace them with stage
    if (branch == 'master'){
        parsedGoal = parsedGoal.replaceAll('#ENVIRONMENT','prod')
    }
    else {
        parsedGoal = parsedGoal.replaceAll('#ENVIRONMENT','stage')
    }
    echo "Parsed goal from ${goal} to ${parsedGoal}"
    echo "after parsedgoal"
    return parsedGoal
}

def build(String appType, String version, String goal) {
    def builder = new io.stakater.builder.Build()
    String parsedGoal = parseGoalEnvironment(goal)

    echo "TODO: build: $version , $parsedGoal"
    switch(appType) {
        case "angular":
            builder.buildAngularApplication(version, parsedGoal)
        break
        case "gradle":
            builder.buildGradleApplication(version, parsedGoal)
        break
        case "maven":
            builder.buildMavenApplication(version, parsedGoal)
        break
        case "node":
            builder.buildNodeApplication(version, parsedGoal)
        break
        case "dotnet":
            builder.buildDotnetApplication(version, parsedGoal)
        break
    }
}

def getImageVersion(String repoUrl, String imagePrefix, String prNumber, String buildNumber) {
    def utils = new io.fabric8.Utils()
    def branchName = utils.getBranch()
    def git = new io.stakater.vc.Git()
    def imageVersion = ''

    // For CD
    if (branchName.equalsIgnoreCase("master")) {
        sh "stk generate version > commandResult"
        def version = readFile('commandResult').trim()
        sh "rm commandResult .VERSION"
        version = 'v' + version        
        imageVersion = imagePrefix + version
    }
    // For CI
    else {
        if(imagePrefix == "") {
            imagePrefix = "0.0.0"
        }
        imageVersion = imagePrefix + "-" + prNumber + '-' + buildNumber + '-SNAPSHOT'
    }

    return imageVersion
}

return this