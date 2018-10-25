#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(String appName){
    sh """
        mvn clean package -f application/pom.xml
        mv application/target/*.jar application/target/${appName}.jar
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