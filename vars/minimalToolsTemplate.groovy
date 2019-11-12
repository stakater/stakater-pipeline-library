#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)
    def serviceAccount = parameters.get('serviceAccount', 'jenkins')
     
    def toolsImage = parameters.get('toolsImage', 'stakater/pipeline-tools:v2.0.5')
    def notificationSecret = parameters.get('notificationSecret', 'slack-notification-hook')
    echo "===================="
    echo "${notificationSecret}"
    echo "===================="
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    echo 'Using toolsImage : ' + toolsImage
    echo 'Mounting docker socket to build docker images'
    echo 'Using serviceAccount : ' + serviceAccount
    podTemplate(cloud: cloud, label: label, serviceAccount: serviceAccount, inheritFrom: "${inheritFrom}",
            annotations: [
                podAnnotation(key: "scheduler.alpha.kubernetes.io/critical-pod", value: "true")
            ],
            envVars: [
                secretEnvVar(key: 'SLACK_CHANNEL', secretName: notificationSecret, secretKey: 'channel'),
                secretEnvVar(key: 'SLACK_WEBHOOK_URL', secretName: notificationSecret, secretKey: 'webHookURL'),
                envVar(key: 'DOCKER_REPOSITORY_URL', value: 'docker.delivery.stakater.com:443'),
                envVar(key: 'CHART_REPOSITORY_URL', value: 'https://stakater.github.io/stakater-charts'),
                envVar(key: 'JAVA_REPOSITORY_URL', value: 'http://nexus.release/repository/maven')
            ],
            containers: [
                    containerTemplate(
                            name: 'tools',
                            image: "${toolsImage}",
                            command: '/bin/sh -c',
                            args: 'cat',
                            privileged: true,
                            workingDir: '/home/jenkins/',
                            ttyEnabled: true,
                            envVars: [
                                    envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                    envVar(key: 'DOCKER_API_VERSION', value: '1.32'),
                                    envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'),
                                    envVar(key: 'KUBERNETES_MASTER', value: 'https://kubernetes.default:443')]
                    )],
            volumes: [
                    secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                    persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                    secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                    secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
        body()
    }
}
