#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version: '']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    container('clients') {
        def newVersion = config.version
        if (newVersion == '') {
            newVersion = getNewVersion {}
        }
        env.setProperty('VERSION', newVersion)
        echo sh(returnStdout: true, script: 'env')
        dockerBuild(newVersion)
        return newVersion
    }
}

def dockerBuild(version){
    def newImageName = "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.JOB_NAME}:${version}"
    sh "docker build -t ${newImageName} ."
    sh "docker push ${newImageName}"
}