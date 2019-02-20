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

Host bitbucket.org
    StrictHostKeyChecking no
EOF

        ssh-keyscan github.com > /root/.ssh/known_hosts
        echo "\n" >> /root/.ssh/known_hosts
        ssh-keyscan gitlab.com >> /root/.ssh/known_hosts
        echo "\n" >> /root/.ssh/known_hosts
        ssh-keyscan bitbucket.org >> /root/.ssh/known_hosts
        echo "\n" >> /root/.ssh/known_hosts
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
    def url = flow.getScmPushUrl()

    def provider = flow.getProvider(url)
    echo "provider: ${provider}"

    def project = flow.getProject(provider)
    echo "project name with organization: ${project}"

    def providerToken = flow.getProviderToken(provider)

    switch(provider) {
        case "github":
            flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken)
            break

        case "gitlab":
            def result = flow.getGitLabMergeRequestsByBranchName(project, env.BRANCH_NAME == null ? env.REPO_CLONE_BRANCH : env.BRANCH_NAME, providerToken)
            result.each{value -> 
                def prMessage = "@${value.author.username} " + message
                echo "Commenting on MR with id: ${value.iid}, and message: ${prMessage}"
                flow.postPRComment(prMessage, value.iid, project, provider, providerToken)
            }
            break

        case "bitbucket":
            def result = flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken)
            break
            
        default:
            error "${provider} is not supported" 
            break   
    }
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

def push(def repoDir, String branchName) {
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key

        cd ${repoDir}
        git push origin ${branchName}
    """
}

def runGoReleaser(String repoDir){
  sh """
    cd ${repoDir}
    goreleaser
  """
}

return this
