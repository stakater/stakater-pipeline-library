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
        echo -e "Host github.com\\n\\tStrictHostKeyChecking no\\n" > /root/.ssh/config
        echo -e "Host gitlab.com\\n\\tStrictHostKeyChecking no\\n" >> /root/.ssh/config
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

    def splitted = githubProject.split('/')

    def gOrganization = splitted[0], gRepo = splitted[1]

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

    // We pass in empty token as it finds it at /home/jenkins/.apitoken/hub
    if (!flow.isAuthorCollaborator("", githubProject)){
        echo 'Change author is not a collaborator on the project, failing build until we support the [test] comment'
        return
    }

    message = "@${changeAuthor} " + message
    flow.postPRCommentToGithub(message, pr, githubProject)
}

def getGitAuthor() {
    def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
    return sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

def getLastCommitMessage() {
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

def createTagAndPush(def repoDir, String version) {
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key

        cd ${repoDir}
        git tag ${version}
        git push --tags
    """
}

def createRelease(def version) {
    def flow = new io.stakater.StakaterCommands()
    flow.createGitHubRelease(version)
}

return this