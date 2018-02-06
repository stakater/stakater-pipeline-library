#!/usr/bin/groovy
package io.stakater

static def shOutput(String command) {
    return sh(
        script: """
            ${command}
        """,
        returnStdout: true).toString().trim()
}