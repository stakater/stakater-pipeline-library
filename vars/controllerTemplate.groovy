#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = 'jenkinsci/jnlp-slave:2.62'

    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()
    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
        echo 'Mounting docker socket to build docker images'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 'clients',
                                image: "${clientsImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                privileged: true,
                                workingDir: '/home/jenkins/',
                                ttyEnabled: true,
                                envVars: [
                                        envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                        envVar(key: 'DOCKER_API_VERSION', value: '1.23'),
                                        envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')],
                                        envVar(key: 'CONFIG_FILE_PATH', value: '/etc/ingress-monitor-controller/config.yaml')
                        )],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                        secretVolume(secretName: 'ingress-monitor-controller-test-config', mountPath: '/etc/ingress-monitor-controller'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
            body()
        }
    } else {
        echo 'Mounting docker socket to build docker images'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        // [name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}',  workingDir: '/home/jenkins/'],
                        [name: 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', privileged: true,  workingDir: '/home/jenkins/', ttyEnabled: true, envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'], [key: 'CONFIG_FILE_PATH', value: '/etc/ingress-monitor-controller/config.yaml']]]],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'ingress-monitor-controller-test-config', mountPath: '/etc/ingress-monitor-controller'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
                envVars: [[key: 'DOCKER_API_VERSION', value: '1.23'],[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'],[key: 'CONFIG_FILE_PATH', value: '/etc/ingress-monitor-controller/config.yaml']]) {
            body()
        }
    }

}
