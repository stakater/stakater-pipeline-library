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
    buildImageWithTagCustom(".", imageName, def tag)
}

def buildImageWithTagCustom(def buildContext, def imageName, def tag) {
    sh """
        docker build -t ${imageName}:${tag} ${buildContext}
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