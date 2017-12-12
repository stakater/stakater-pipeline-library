#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.stakater.StakaterCommands

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def sflow = new StakaterCommands()
  def flow = new Fabric8Commands()
  def repoId
  def releaseVersion
  def extraStageImages = config.extraImagesToStage ?: []
  def extraSetVersionArgs = config.setVersionExtraArgs ?: ""
  def containerName = config.containerName ?: 'maven'

  container(name: containerName) {

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'
    sh 'chmod 600 /home/jenkins/.gnupg/pubring.gpg'
    sh 'chmod 600 /home/jenkins/.gnupg/secring.gpg'
    sh 'chmod 600 /home/jenkins/.gnupg/trustdb.gpg'
    sh 'chmod 700 /home/jenkins/.gnupg'
    sh 'eval "$(ssh-agent -s)" && ssh-add /root/.ssh-git/ssh-key'

    sh "git remote set-url origin https://github.com/${config.project}.git"

    def currentVersion = sflow.getProjectVersion()

    flow.setupWorkspaceForRelease(config.project, config.useGitTagForNextVersion, extraSetVersionArgs, currentVersion)

    repoId = sflow.stageSonartypeRepo()
    releaseVersion = sflow.getProjectVersion()

    // lets avoide the stash / unstash for now as we're not using helm ATM
    //stash excludes: '*/src/', includes: '**', name: "staged-${config.project}-${releaseVersion}".hashCode().toString()

    if (!config.useGitTagForNextVersion){
      sflow.updateGithub ()
    }
  }

  if (config.extraImagesToStage != null){
    stageExtraImages {
      images = extraStageImages
      tag = releaseVersion
    }
  }

  return [config.project, releaseVersion, repoId]
}
