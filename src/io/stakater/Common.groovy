#!/usr/bin/groovy
package io.stakater

//Set the static reference in the script
Script.environment  = this

public class Script {
    public static environment
}

public class Common {
    static def shOutput(String command) {
        return Script.environment.sh(
            script: """
                ${command}
            """,
            returnStdout: true).toString().trim()
    }
}