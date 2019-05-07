#!/usr/bin/groovy
package io.stakater


def shOutput(String command) {
    return sh(
        script: """
            ${command}
        """,
        returnStdout: true).toString().trim()
}

def getEnvValue(String key) {
    sh "echo \$${key} > ${key}"
    value = readFile(key).trim()
    sh "rm ${key}"
    return value
}

String replaceCredentialsInURL(String URL, String username, String password) {
    String protocol = URL.startsWith("https") ? "https" : "http"
    String[] parts = URL.split("$protocol://(.*@)?")
    if (parts.length < 2) {
        error "Unable to replace Credentials in URL."
        return URL
    }

    return protocol + "://" + username + ":" + password + "@" + parts[1]
}

return this
