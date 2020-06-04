#!/usr/bin/groovy
package io.stakater.containers


def buildImageWithTagCustom(def imageName, def tag, def verifyTls) {
    buildImageWithTagCustom(".", imageName, tag, verifyTls)
}

def buildImageWithTagCustom(def buildContext, def imageName, def tag, def verifyTls) {
    sh """
        buildah --storage-driver=vfs build-using-dockerfile --tls-verify=${verifyTls} --layers -f ${buildContext} -t ${imageName}:${tag} .
    """
}

def pushTagCustom(def imageName, def tag, def verifyTls) {
    sh """
        buildah --storage-driver=vfs push --tls-verify=${verifyTls} ${imageName}:${tag} docker://${imageName}:${tag}
    """
}

return this