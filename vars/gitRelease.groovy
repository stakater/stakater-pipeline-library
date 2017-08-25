#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def sFlow = new io.stakater.StakaterCommands()
    def repoId
    def releaseVersion
    def extraStageImages = config.extraImagesToStage ?: []
    def extraSetVersionArgs = config.setVersionExtraArgs ?: ""
    def containerName = config.containerName ?: 'maven'
    def useGitTagOrBranchForNextVersion = config.useGitTagOrBranchForNextVersion ?: ""

    container(name: containerName) {

        sh 'chmod 600 /root/.ssh-git/ssh-key'
        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
        sh 'chmod 700 /root/.ssh-git'
        sh 'chmod 600 /home/jenkins/.gnupg/pubring.gpg'
        sh 'chmod 600 /home/jenkins/.gnupg/secring.gpg'
        sh 'chmod 600 /home/jenkins/.gnupg/trustdb.gpg'
        sh 'chmod 700 /home/jenkins/.gnupg'

        sh "git remote set-url origin git@github.com:${config.project}.git"

        def currentVersion = flow.getProjectVersion()

        sFlow.setupWorkspaceForRelease(config.project, useGitTagOrBranchForNextVersion, extraSetVersionArgs, currentVersion)

        repoId = sFlow.stageSonartypeRepo()
        releaseVersion = flow.getProjectVersion()

        if (!useGitTagOrBranchForNextVersion.equalsIgnoreCase("tag")){
            flow.updateGithub()
        }

        echo "About to release ${name} repo ids ${repoIds}"
        sFlow.releaseSonartypeRepo(repoId)
    }

    return [config.project, releaseVersion, repoId]
}
