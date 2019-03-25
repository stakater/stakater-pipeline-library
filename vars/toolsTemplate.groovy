#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('tools')
    def label = parameters.get('label', defaultLabel)

    def toolsImage = parameters.get('toolsImage', 'stakater/pipeline-tools:1.5.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    echo 'Using toolsImage : ' + toolsImage
    echo 'Mounting docker socket to build docker images'
    podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins',
            containers: [
                    containerTemplate(
                            name: 'tools',
                            image: "${toolsImage}",
                            command: '/bin/sh -c',
                            args: 'cat',
                            privileged: false,
                            workingDir: '/home/jenkins/',
                            ttyEnabled: true
                    )]) {
        body.call(label)
    }
}