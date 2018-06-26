#!/usr/bin/groovy
package io.stakater.vc
import io.stakater.StakaterCommands

def setUserInfo(String gitUserName, String gitUserEmail) {
    sh """
        git config --global user.name "${gitUserName}"
        git config --global user.email "${gitUserEmail}"
    """
}

def addHostsToKnownHosts() {
    sh """
        mkdir -p /root/.ssh/
        tee /root/.ssh/config <<EOF
Host github.com
    StrictHostKeyChecking no

Host gitlab.com
    StrictHostKeyChecking no
EOF

        ssh-keyscan github.com > /root/.ssh/known_hosts
        echo "\n" >> /root/.ssh/known_hosts
        ssh-keyscan gitlab.com >> /root/.ssh/known_hosts
    """
}

def commitChanges(String repoDir, String commitMessage) {
    String messageToCheck = "nothing to commit, working tree clean"
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key
        cd ${repoDir}
        git add .
        if ! git status | grep '${messageToCheck}' ; then
            git commit -m "${commitMessage}"
            git push
        else
            echo \"nothing to do\"
        fi
    """
}

def checkoutRepo(String repoUrl, String branch, String dir) {
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key

        rm -rf ${dir}
        git clone -b ${branch} ${repoUrl} ${dir}
    """
}

def addCommentToPullRequest(String message) {
    def flow = new StakaterCommands()

    def githubProject = flow.getGitHubProject()


    def changeAuthor = env.CHANGE_AUTHOR
    if (!changeAuthor){
        echo "no commit author found so cannot comment on PR"
        return
    }

    def pr = env.CHANGE_ID
    if (!pr){
        echo "no pull request number found so cannot comment on PR"
        return
    }

    message = "@${changeAuthor} " + message
    flow.postPRCommentToGithub(message, pr, "${env.REPO_OWNER}/${env.REPO_NAME}")
}

def getGitAuthor() {
    def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    return sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

def getLastCommitMessage() {
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

def createTagAndPush(def repoDir, String version) {
    createTagAndPush(repoDir, version, "By ${env.JOB_NAME}")
}

def createTagAndPush(def repoDir, String version, String message) {
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key

        cd ${repoDir}
        git tag -am "${message}" ${version}
        git push origin ${version}
    """
}

def createRelease(def version) {
    def flow = new io.stakater.StakaterCommands()
    flow.createGitHubRelease(version)
}

def tagAndRelease(def versionFile, def repoName, def repoOwner){
  echo "Generating New Version"
  def common = new io.stakater.Common()
  def version = common.shOutput("jx-release-version --gh-owner=${repoOwner} --gh-repository=${repoName} --version-file ${versionFile}")
  sh """
      echo "${version}" > ${versionFile}
  """
  commitChanges(WORKSPACE, "Bump Version to ${version}")

  echo "Pushing Tag ${version} to Git"
  createTagAndPush(WORKSPACE, version)
  createRelease(version)
}

def createBinary(def versionFile, def repoName, def repoOwner, String githubToken){
  echo "Generating New Version"
  def common = new io.stakater.Common()
  def version = common.shOutput("jx-release-version --gh-owner=${repoOwner} --gh-repository=${repoName} --version-file ${versionFile}")
  sh """
      echo -n "${version}" > ${versionFile}
  """
  commitChanges(WORKSPACE, "Bump Version to ${version}")
  echo "Pushing Tag ${version} to Git"
  createTagAndPush(WORKSPACE, version)
  runGoReleaser(WORKSPACE, githubToken)
}

def runGoReleaser(String repoDir, String githubToken){
  sh """
    cd ${repoDir}
    export GITHUB_TOKEN=${githubToken}
    goreleaser
  """
}

return this
