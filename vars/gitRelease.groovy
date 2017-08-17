#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    sh "git remote set-url origin ${config.remote}"
    sh "git config user.email admin@stakater.com"
    sh "git config user.name stakater-release"
    
    sh "chmod 600 /root/.ssh-git/ssh-key"
    sh "chmod 600 /root/.ssh-git/ssh-key.pub"
    sh "chmod 700 /root/.ssh-git"
    
    sh "git checkout -b release-${config.version}"
    sh "git tag -fa v${config.version} -m 'Release version ${config.version}'"
    sh "git push origin v${config.version}"
    sh "git push origin release-${config.version}"
  }
