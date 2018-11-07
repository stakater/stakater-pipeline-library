#!/usr/bin/groovy
package io.stakater.repository

def pushAppArtifact(String appName, String version) {
  def repositoryType = version.contains("SNAPSHOT") ? "snapshots" : "releases"
  sh """
    mvn deploy:deploy-file -DgeneratePom=false -DrepositoryId=nexus -Durl=http://nexus/repository/maven-${repositoryType} -DpomFile=application/pom.xml -Dfile=application/target/${appName}-${version}.jar
  """
}

return this