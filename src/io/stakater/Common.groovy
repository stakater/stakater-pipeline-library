#!/usr/bin/groovy
package io.stakater


static def shOutput(def steps, String command) {
    return steps.sh(
        script: """
            ${command}
        """,
        returnStdout: true).toString().trim()
    }

return this