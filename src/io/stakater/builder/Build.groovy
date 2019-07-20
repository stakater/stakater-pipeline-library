#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(String version, String mavenGoal = "clean package"){
    sh "mvn versions:set -DnewVersion=${version} -f pom.xml"
    mavenGoal.split(';').each { goal ->
        sh "mvn ${goal} -f pom.xml"
    }
}

def buildGradleApplication(String version, String gradleGoal = "clean package"){
    gradleGoal.split(';').each { goal ->
        sh "gradle -Pversion=${version} ${goal}"
    }
}

def buildDotnetApplication(String version, String dotnetGoal){
    // version???
    dotnetGoal.split(';').each { goal ->
        sh "dotnet ${goal}"
    }
}

def buildNodeApplication(String version, String nodeGoal="install") {
    sh """
        npm --no-git-tag-version --allow-same-version version ${version}
    """
    nodeGoal.split(';').each { goal ->
        sh "npm ${goal}"
    }
}

def buildAngularApplication(String version, String angularGoal="install;run build:stage") {
    sh """
        npm --no-git-tag-version --allow-same-version version ${version}
    """
    angularGoal.split(';').each { goal ->
        sh "npm ${goal}"
    }
}

def deployHelmChart(String chartDir){
    sh """        
        make install-chart
    """
}

return this