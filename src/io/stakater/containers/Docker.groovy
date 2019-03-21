#!/usr/bin/groovy
package io.stakater.containers

def buildImage(def buildContext, def imageName, def tag) {
    sh """
        docker build -t ${imageName}:${tag} ${buildContext} --network=host
    """
}

def buildImage(def imageName, def tag) {
    buildImage(".", imageName, tag)
}

def buildImageWithTagCustom(def imageName, def tag) {
    buildImageWithTagCustom(".", imageName, tag)
}

def buildAndPushImageFromMakefile(def dockerRegistryURL,def repoOwner,def imageName){    
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key
        export REGISTRY_HOST=${dockerRegistryURL}
        export USERNAME=${repoOwner.toLowerCase()}
        export NAME=${imageName.toLowerCase()}
        git pull --tags
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

def pushImage(def imageName, def tag) {
    sh """
        docker push ${imageName}:${tag}
    """
}

return this