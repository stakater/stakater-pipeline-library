#!/usr/bin/groovy
import io.fabric8.Utils

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def utils = new Utils()
    def ns = config.environment
    kubeNS = "-Dkubernetes.namespace=${ns} -Dfabric8.use.existing=${ns}"
    echo "Running the integration tests in the namespace: ${ns}"

    if (utils.isDisabledITests()) {
        echo "WARNING: Integration tests are current DISABLED for these pipelines!"
    } else {
        sh "./mvnw org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:integration-test ${kubeNS} -Dit.test=${config.itestPattern} -DfailIfNoTests=${config.failIfNoTests} org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:verify"
        junitResults(body);
    }
  }