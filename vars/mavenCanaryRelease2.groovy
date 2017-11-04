#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def utils = new io.fabric8.Utils()

    def skipTests = config.skipTests ?: false

    def profile = config.profile ?: '-P prod'

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"

    sh "./mvnw org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=${config.version}"

    sh "./mvnw clean -B -e -U deploy -Dmaven.test.skip=${skipTests} ${profile}"

    junitResults(body)

    sonarQubeScanner(body)

    def registry = utils.getDockerRegistry()

    retry(5) {
        sh "./mvnw fabric8:push -Ddocker.push.registry=${registry}"
    }

    contentRepository(body)
  }
