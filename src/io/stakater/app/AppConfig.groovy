#!/usr/bin/groovy
package io.stakater.app

Map getBaseConfig(Map config, String repoName, String repoOwner, String workspace) {
    Map baseConfig = [:]

    baseConfig.appType = config.appType
    baseConfig.goal = config.goal
    baseConfig.name = config.appName ?: repoName
    baseConfig.imageName = baseConfig.name.split("dockerfile-").last().toLowerCase()
    baseConfig.kubernetesDir = workspace + "/deployments/kubernetes"
    if (repoOwner.startsWith('stakater-')){
        baseConfig.repoOwner = 'stakater'
    }
    baseConfig.repoOwner = repoOwner
    baseConfig.imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''

    return baseConfig
}

Map getPackageConfig(Map config) {
    Map packageConfig = [:]
    packageConfig.chartPackageName = ""
    packageConfig.helmVersion = ""
    packageConfig.dockerRepositoryURL = config.dockerRepositoryURL ?: ""

    packageConfig.javaRepositoryURL = config.javaRepositoryURL ?: ""
    packageConfig.publishArtifact = ! packageConfig.javaRepositoryURL.equals("")
    packageConfig.artifactType = config.artifactType ?: ".jar"

    packageConfig.chartRepositoryURL = config.chartRepositoryURL ?: ""
    packageConfig.publishChart = ! packageConfig.chartRepositoryURL.equals("")

    packageConfig.e2eTestJob = config.e2eTestJob ?: ""
    packageConfig.executeE2E = ! packageConfig.e2eTestJob.equals("")

    packageConfig.runIntegrationTest = config.runIntegrationTest ?: false

    packageConfig.performanceTestsJob = config.performanceTestsJob ?: ""
    packageConfig.mockAppsJobName = config.mockAppsJobName ?: ""

    return packageConfig
}

Map getDeploymentConfig(Map config) {
    Map deploymentConfig = [:]
    deploymentConfig.deployManifest = config.deployManifest ?: false
    deploymentConfig.namespace = config.namespace ?: ""
    deploymentConfig.devAppsJobName = config.devAppsJobName ?: ""
    deploymentConfig.pushToDevApps = ! deploymentConfig.devAppsJobName.equals("")

    return deploymentConfig
}

Map getGitConfig(Map config) {
    Map gitConfig = [:]
    gitConfig.cloneUsingToken = config.usePersonalAccessToken ?: false
    gitConfig.user = config.gitUser ?: "stakater-user"
    gitConfig.email = config.gitEmail ?: "stakater@gmail.com"

    if (gitConfig.cloneUsingToken) {
        def stakaterCommands = new io.stakater.StakaterCommands()
        gitConfig.tokenSecretName = config.tokenCredentialID ?: ""
        gitConfig.tokenSecret = stakaterCommands.getProviderTokenFromJenkinsSecret(gitConfig.tokenSecretName)
    } else {
        gitConfig.tokenSecretName = ""
        gitConfig.tokenSecret = ""
    }

    return gitConfig
}

Map getNotificationConfig(Map config) {
    Map notificationConfig = [:]

    notificationConfig.notifySlack = config.notifySlack == false ? false : true
    notificationConfig.slackChannel = "${env.SLACK_CHANNEL}"
    notificationConfig.slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"
    
    return notificationConfig
}
Map getEcrConfig(Map config) {
    Map ecrConfig = [:]

    ecrConfig.isEcr = config.isEcr ?: false
    ecrConfig.ecrRegion = config.ecrRegion ?: 'us-west-1'

    return ecrConfig
}
return this