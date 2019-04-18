#!/usr/bin/groovy
//execute make target

def call(body) {
    Map config = [:]
    String[] methodParameters = ["target"]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    config.target = config.target ? config.target : "install-dry-run"

    timestamps {
        ArrayList<String> parameters = new ArrayList<String>()
        config.keySet().each { key ->
            if (! (key in methodParameters)) {
                parameters.add("$key=${config[key]}")
            }
        }
        sh "make ${config.target} ${parameters.join(" ")}"
    }
}