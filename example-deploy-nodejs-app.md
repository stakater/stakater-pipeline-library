# Jenkinsfile to deploy nodejs app

Here is a sample Jenkinsfile to deploy nodejs app

```
#!/usr/bin/groovy
@Library('github.com/stakater/fabric8-pipeline-library@master')

def dummy = ""

podTemplate(envVars: [envVar(key: 'FABRIC8_DOCKER_REGISTRY_SERVICE_HOST', value: 'dockerhub.io'),
                      envVar(key: 'FABRIC8_DOCKER_REGISTRY_SERVICE_PORT', value: '80')]){

    clientsK8sNode(clientsImage: 'stakater/docker-with-git:17.10') {
        def envStage = "apps"
        def envProd = "demo"
        def newVersion = ''
        def rc = ""

        checkout scm

        stage('Build Release') {
            echo 'NOTE: running pipelines for the first time will take longer as build and base docker images are pulled onto the node'
            if (!fileExists ('Dockerfile')) {
              writeFile file: 'Dockerfile', text: 'FROM node:5.3-onbuild'
            }

            newVersion = performCanaryReleaseK8s {}

            rc = getDeploymentResourcesK8s {
                port = 8080
                label = 'node'
                icon = 'https://cdn.rawgit.com/fabric8io/fabric8/dc05040/website/src/images/logos/nodejs.svg'
                version = newVersion
                imageName = clusterImageName
                dockerRegistrySecret = 'docker-registry-secret'
                readinessProbePath = "/readiness"
                livenessProbePath = "/health"
                ingressClass = "external-ingress"
            }
        }

        stage('Rollout to Apps') {
            kubernetesApply(file: rc, environment: envStage)
        }

        stage('Approve') {
            approve{
              room = null
              version = canaryVersion
              console = fabric8Console
              environment = envStage
            }
        }

        stage('Rollout to Demo') {
            kubernetesApply(file: rc, environment: envProd)
        }
    }
}
```