#!/usr/bin/groovy
package io.stakater.containers


def buildImageWithTagCustom(def imageName, def tag, def verifyTls) {
    sh """
        buildah --storage-driver=vfs build-using-dockerfile --format=docker --tls-verify=${verifyTls} -t ${imageName}:${tag} .
    """
}

def pushTagCustom(def imageName, def tag, def verifyTls) {
    sh """
        buildah --storage-driver=vfs push --format=docker --tls-verify=${verifyTls} ${imageName}:${tag} docker://${imageName}:${tag}
    """
}

return this