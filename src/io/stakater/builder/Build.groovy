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

def runPerformanceTestsForMavenApplication(){
    sh """
        make run-performance-tests
    """
}

def deployHelmChartForPR(String chartDir){
    sh """        
        make install-chart ENVIRONMENT='pr'
        sleep 10s
    """
}

return this