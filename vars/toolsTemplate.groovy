#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    // NOTE: this build-clients contains docker binaries which were giving auth credentials issue as they are from
    // RedHat! So, we pass on the clientsImage e.g. stakater/docker-with-git:17.1 which has latest docker binaries
    def toolsImage = parameters.get('toolsImage', 'stakater/pipeline-tools:1.2.0')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    echo 'Using toolsImage : ' + toolsImage
    echo 'Mounting docker socket to build docker images'
    podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            envVars: [
                secretEnvVar(key: 'CHARTMUSEUM_USERNAME', secretName: 'chartmuseum-auth', secretKey: 'username'),
                secretEnvVar(key: 'CHARTMUSEUM_PASSWORD', secretName: 'chartmuseum-auth', secretKey: 'password'),
                secretEnvVar(key: 'SLACK_CHANNEL', secretName: 'slack-notification-hook', secretKey: 'channel'),
                secretEnvVar(key: 'SLACK_WEBHOOK_URL', secretName: 'slack-notification-hook', secretKey: 'webHookURL')
            ],
            containers: [
                    containerTemplate(
                            // why can't I change this name? its registered somewhere?
                            name: 'tools',
                            image: "${toolsImage}",
                            command: '/bin/sh -c',
                            args: 'cat',
                            privileged: true,
                            workingDir: '/home/jenkins/',
                            ttyEnabled: true,
                            envVars: [
                                    envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                    envVar(key: 'DOCKER_API_VERSION', value: '1.23'),
                                    envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                    envVar(key: 'CONFIG_FILE_PATH', value: '/etc/ingress-monitor-controller/config.yaml'),
                                    envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'),
                                    envVar(key: 'KUBERNETES_MASTER', value: 'https://kubernetes.default:443')]
                    )],
            volumes: [
                    secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                    persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                    secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                    secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                    secretVolume(secretName: 'ingress-monitor-controller-test-config', mountPath: '/etc/ingress-monitor-controller'),
                    secretVolume(secretName: "k8s-apps-cluster-kubeconfig", mountPath: '/home/jenkins/.kube'),
                    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
        body()
    }
}
