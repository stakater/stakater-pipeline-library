#!/usr/bin/groovy
package io.stakater

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import io.fabric8.kubernetes.api.KubernetesHelper
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import jenkins.model.Jenkins

import java.util.regex.Pattern

def stageSonartypeRepo() {
    def flow = new io.fabric8.Fabric8Commands()

    try {
        sh "./mvnw clean -B"
        sh "./mvnw -V -B -e -U install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:deploy -s /root/.m2/setting.xml -P release -P openshift -DnexusUrl=http://nexus -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

    } catch (err) {
        hubot room: 'release', message: "Release failed when building and deploying to Nexus ${err}"
        currentBuild.result = 'FAILURE'
        error "ERROR Release failed when building and deploying to Nexus ${err}"
    }
    // the sonartype staging repo id gets written to a file in the workspace
    return flow.getRepoIds()
}

def releaseSonartypeRepo(String repoId) {
    try {
        // release the sonartype staging repo
        sh "./mvnw org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DnexusUrl=http://nexus -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"

    } catch (err) {
        sh "./mvnw org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DnexusUrl=http://nexus -DstagingRepositoryId=${repoId} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
        currentBuild.result = 'FAILURE'
        error "ERROR releasing sonartype repo ${repoId}: ${err}"
    }
}

def getProjectVersion() {
    def file = readFile('pom.xml')
    def project = new XmlSlurper().parseText(file)
    return project.version.text()
}

def updateGithub() {
    def releaseVersion = getProjectVersion()
    sh "git push origin release-v${releaseVersion}"
}

return this
