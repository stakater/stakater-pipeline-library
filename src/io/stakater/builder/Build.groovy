#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(String version){
    sh """
        mvn versions:set -DnewVersion=${version} -f pom.xml
        mvn clean package -f pom.xml
    """
}

def deployHelmChart(String chartDir){
    sh """        
        make install-chart
    """
}

return this