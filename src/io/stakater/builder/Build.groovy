#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(){
    sh """
        mvn clean package
    """
}

def runSyntheticTestsForMavenApplication(){
    sh """
        make run-synthetic-tests
    """
}

return this