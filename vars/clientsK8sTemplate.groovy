#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clientsK8s')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    echo 'Using clientsImage : ' + clientsImage
    echo 'Mounting docker socket to build docker images'
    podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [
                    containerTemplate(
                            name: 'clientsK8s',
                            image: "${clientsImage}",
                            command: '/bin/sh -c',
                            args: 'cat',
                            privileged: true,
                            workingDir: '/home/jenkins/',
                            ttyEnabled: true,
                            envVars: [
                                    envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                    envVar(key: 'DOCKER_API_VERSION', value: '1.23'),
                                    envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
                    )],
            volumes: [
                    secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                    secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                    secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                    secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
        body()
    }
}
