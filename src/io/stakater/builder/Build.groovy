#!/usr/bin/groovy
package io.stakater.builder

def buildMavenApplication(String version, String packageCommand = "package"){
    sh """
        mvn versions:set -DnewVersion=${version} -f pom.xml
        mvn clean ${packageCommand} -f pom.xml
    """
}

def buildAspNetApplication(String version){
    sh """
        dotnet restore
        dotnet publish -c Release -o out
    """
}

def buildNodeApplication(String version) {
    sh """
        npm --no-git-tag-version --allow-same-version version ${version}
        npm install
    """
}

def deployHelmChart(String chartDir){
    sh """        
        make install-chart
    """
}

return this