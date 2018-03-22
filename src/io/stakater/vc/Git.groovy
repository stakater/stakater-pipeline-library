#!/usr/bin/groovy
package io.stakater.vc
import io.stakater.StakaterCommands

def flow = new StakaterCommands()

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

def addCommentToPullRequest() {
    // We pass in empty token as well as empty project as it finds them automatically
    if (!flow.isAuthorCollaborator("", "")){
        error 'Change author is not a collaborator on the project, failing build until we support the [test] comment'
    }
}

return this