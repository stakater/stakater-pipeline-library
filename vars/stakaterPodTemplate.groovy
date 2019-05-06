#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('stakater-node')
    def label = parameters.get('label', defaultLabel)
    def serviceAccount = parameters.get('serviceAccount', 'jenkins')

    def inheritFrom = parameters.get('inheritFrom', 'base')

    def podAnnotations = getStakaterPodAnnotations(parameters.get('podAnnotations', [:]))
    def podEnvVars = getStakaterPodEnvVars(parameters.get('podEnvVars', [:]))
    def podVolumes = getStakaterPodVolumes(parameters.get('podVolumes', [:]))
    def podContainers = getStakaterPodContainers(parameters.get('podContainers', [:]))

    def cloud = flow.getCloudConfig()

    echo 'Using serviceAccount : ' + serviceAccount
    echo 'Inheriting pod template from : ' + inheritFrom

    podTemplate(cloud: cloud, label: label, serviceAccount: serviceAccount, inheritFrom: "${inheritFrom}",
        annotations: podAnnotations,
        envVars: podEnvVars,
        volumes: podVolumes,
        containers: podContainers
    ) {
        body()
    }
}

def getStakaterPodAnnotations(Map parameters = [:]) {
    def annotations = []

    Boolean isCritical = parameters.get('isCritical', true)
    def additionalAnnotations = parameters.get('additionalAnnotations', [])

    if (isCritical) {
        annotations.add(podAnnotation(key: "scheduler.alpha.kubernetes.io/critical-pod", value: "true"))
    }

    additionalAnnotations.each { annotation->
        annotations.add(podAnnotation(key: annotation.key, value: annotation.value))
    }

    return annotations
}

def getStakaterPodEnvVars(Map parameters = [:]) {
    def envVars = []

    Boolean isChartMuseum = parameters.get('isChartMuseum', false)
    Boolean isNotifySlack = parameters.get('isNotifySlack', false)
    Boolean isGithubToken = parameters.get('isGithubToken', false)
    Boolean isGitlabToken = parameters.get('isGitlabToken', false)
    Boolean isDockerRepostiory = parameters.get('isDockerRepostiory', false)
    Boolean isChartRepostiory = parameters.get('isChartRepostiory', false)
    Boolean isJavaRepostiory = parameters.get('isJavaRepostiory', false)
    def additionalEnvVars = parameters.get('additionalEnvVars', [])
    def additionalSecretEnvVars = parameters.get('additionalSecretEnvVars', [])

    if (isChartMuseum) {
        envVars.add(secretEnvVar(key: 'CHARTMUSEUM_USERNAME', secretName: 'chartmuseum-auth', secretKey: 'username'))
        envVars.add(secretEnvVar(key: 'CHARTMUSEUM_PASSWORD', secretName: 'chartmuseum-auth', secretKey: 'password'))
    }

    if (isNotifySlack) {
        envVars.add(secretEnvVar(key: 'SLACK_CHANNEL', secretName: 'slack-notification-hook', secretKey: 'channel'))
        envVars.add(secretEnvVar(key: 'SLACK_WEBHOOK_URL', secretName: 'slack-notification-hook', secretKey: 'webHookURL'))
    }

    if (isGithubToken) {
        envVars.add(secretEnvVar(key: 'GITHUB_AUTH_TOKEN', secretName: 'jenkins-hub-api-token', secretKey: 'hub'))
    }

    if (isGitlabToken) {
        envVars.add(secretEnvVar(key: 'GITLAB_AUTH_TOKEN', secretName: 'jenkins-hub-api-token', secretKey: 'gitlab.hub'))
    }

    additionalEnvVars.each { env ->
        envVars.add(envVar(key: env.key, value: env.value))
    }

    additionalSecretEnvVars.each { secretEnv ->
        envVars.add(secretEnvVar(key: secretEnv.key, secretName: secretEnv.secretName, secretKey: secretEnv.secretKey))
    }

    return envVars
}

def getStakaterPodVolumes(Map parameters = [:]) {
    def volumes = []

    Boolean isMaven = parameters.get('isMaven', false)
    Boolean isMavenLocalRepo = parameters.get('isMavenLocalRepo', false)
    Boolean isDockerConfig = parameters.get('isDockerConfig', false)
    Boolean isDockerMount = parameters.get('isDockerMount', false)
    Boolean isGitSsh = parameters.get('isGitSsh', false)
    Boolean isHubApiToken = parameters.get('isHubApiToken', false)
    Boolean isStkConfig = parameters.get('isStkConfig', false)
    Boolean isHelmPgpKey = parameters.get('isHelmPgpKey', false)
    def additionalSecretVolumes = parameters.get('additionalSecretVolumes', [])
    def additionalHostPathVolumes = parameters.get('additionalHostPathVolumes', [])
    def additionalPVCs = parameters.get('additionalPVCs', [])

    if (isMaven) {
        volumes.add(secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'))
    }

    if (isMavenLocalRepo) {
        volumes.add(persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'))
    }

    if (isDockerConfig) {
        volumes.add(secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'))
    }

    if (isDockerMount) {
        volumes.add(hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'))
    }

    if (isGitSsh) {
        volumes.add(secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'))
    }

    if (isHubApiToken) {
        volumes.add(secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'))
    }

    if (isStkConfig) {
        volumes.add(secretVolume(secretName: 'stk-config', mountPath: '/home/jenkins/.stk'))
    }

    if (isHelmPgpKey) {
        volumes.add(secretVolume(secretName: 'helm-pgp-key', mountPath: '/usr/local/bin/pgp-configuration/'))
    }

    additionalSecretVolumes.each { secret->
        volumes.add(secretVolume(secretName: secret.secretName, mountPath: secret.mountPath))
    }

    additionalHostPathVolumes.each { hostPathVolume->
        volumes.add(hostPathVolume(hostPath: hostPathVolume.hostPath, mountPath: hostPathVolume.mountPath))
    }

    additionalPVCs.each { pvc->
        volumes.add(persistentVolumeClaim(claimName: pvc.claimName, mountPath: pvc.mountPath))
    }

    return volumes
}

def getStakaterPodContainers(Map parameters = [:]) {
    def containers = []

    Map defaultContainer = parameters.get('defaultContainer', [:])
    Boolean isDefaultContainer = parameters.get('enableDefaultContainer', true)
    def additionalContainers = parameters.get('additionalContainers', [])

    if (isDefaultContainer) {
        containers.add(getStakaterPodDefaultContainer(defaultContainer))
    }

    additionalContainers.each { it->
        containers.add(containerTemplate(
            name: it.name,
            image: it.image,
            command: it.command,
            args: it.args,
            privileged: it.privileged,
            workingDir: it.workingDir,
            ttyEnabled: it.ttyEnabled,
            envVars: it.envVars
        ))
    }

    return containers
}

def getStakaterPodDefaultContainer(Map parameters = [:]) {
    String name = parameters.get('name', 'tools')
    String image = parameters.get('image', 'stakater/pipeline-tools:1.5.1')
    String command = parameters.get('command', '/bin/sh -c')
    String args = parameters.get('args', 'cat')
    Boolean privileged = parameters.get('privileged', true)
    String workingDir = parameters.get('workingDir', '/home/jenkins/')
    Boolean ttyEnabled = parameters.get('ttyEnabled', true)

    def envVars = []
    def envVarsConfig = parameters.get('envVarsConfig', [:])
    Boolean isDocker = envVarsConfig.get('isDocker', false)
    Boolean isKubernetes = envVarsConfig.get('isKubernetes', true)
    Boolean isMaven = envVarsConfig.get('isMaven', false)
    def extraEnvVars = envVarsConfig.get('extraEnvVars', [])

    if (isDocker) {
        envVars.add(envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'))
        envVars.add(envVar(key: 'DOCKER_API_VERSION', value: '1.32'))
    }

    if (isKubernetes) {
        envVars.add(envVar(key: 'KUBERNETES_MASTER', value: 'https://kubernetes.default:443'))
    }

    if (isMaven) {
        envVars.add(envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'))
    }

    extraEnvVars.each { it->
        envVars.add(envVar(key: it.key, value: it.value))
    }

    return containerTemplate(
        name: name,
        image: image,
        command: command,
        args: args,
        privileged: privileged,
        workingDir: workingDir,
        ttyEnabled: ttyEnabled,
        envVars: envVars
    )
}