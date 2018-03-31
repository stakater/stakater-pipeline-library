#!/usr/bin/groovy
package io.stakater.containers

def buildImageWithTag(def imageName, def tag) {
    sh """
        docker build -t docker.io/${imageName}:${tag} .
    """
}

def tagImage(def imageName, def currentTag, def newTag) {
    sh """
        docker tag docker.io/${imageName}:${currentTag} docker.io/${imageName}:${newTag}
    """
}

def pushTag(def imageName, def tag) {
    sh """
        docker push docker.io/${imageName}:${tag}
    """
}

def pushTag(def )

return this