#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()

    def skipTests = config.skipTests ?: false

    def profile = '-P kubernetes'

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"



    sonarQubeScanner(body);

    def registry = utils.getDockerRegistry()
    if (flow.isSingleNode()) {
        echo 'Running on a single node, skipping docker push as not needed'
        def m = readMavenPom file: 'pom.xml'
        def groupId = m.groupId.split('\\.')
        def user = groupId[groupId.size() - 1].trim()
        def artifactId = m.artifactId

        sh "docker tag ${config.version} ${registry}/${config.version}"
    }

    contentRepository(body);
  }
