#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(){
    sh """
        mvn clean package -f application/pom.xml
    """
}

def runSyntheticTestsForMavenApplication(){
    sh """
        make run-synthetic-tests
    """
}

return this