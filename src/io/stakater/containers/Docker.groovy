#!/usr/bin/groovy
package io.stakater.containers

def buildImageWithTag(def buildContext, def imageName, def tag) {
    sh """
        docker build -t docker.io/${imageName}:${tag} ${buildContext}
    """
}

def buildImageWithTag(def imageName, def tag) {
    buildImageWithTag(".", imageName, tag)
}

def buildImageWithTagCustom(def imageName, def tag) {
    buildImageWithTagCustom(".", imageName, tag)
}

def buildImageFromMakefile(def dockerRegistryURL,def repoOwner,def repoName){
    // def dockerImage = "${dockerRegistryURL}/${repoOwner.toLowerCase()}/${repoName.toLowerCase()}"
    
    sh """
        export REGISTRY_HOST=${dockerRegistryURL}
        export USERNAME=${repoOwner.toLowerCase()}
        export NAME=${repoName.toLowerCase()}
        make patch-release
    """
}

def buildImageWithTagCustom(def buildContext, def imageName, def tag) {
    sh """
        docker build -t ${imageName}:${tag} ${buildContext} --network=host
    """
}

def tagImageCustom(def imageName, def currentTag, def newTag) {
    sh """
        docker tag ${imageName}:${currentTag} ${imageName}:${newTag}
    """
}

def tagImage(def imageName, def currentTag, def newTag) {
    sh """
        docker tag docker.io/${imageName}:${currentTag} docker.io/${imageName}:${newTag}
    """
}

def pushTagCustom(def imageName, def tag) {
    sh """
        docker push ${imageName}:${tag}
    """
}

def pushTag(def imageName, def tag) {
    sh """
        docker push docker.io/${imageName}:${tag}
    """
}

return this