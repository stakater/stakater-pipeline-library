#!/usr/bin/groovy
package io.stakater.vc
import io.stakater.StakaterCommands
def ignoreFilesDefault = [".md", ".txt"]
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

def commitChangesUsingToken(String repoDir, String commitMessage) {
    String messageToCheck = "nothing to commit, working tree clean"
    sh """
        cd ${repoDir}
        git branch
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

def checkoutRepoUsingToken(String username, String tokenSecretName, String repoUrl, String branch, String dir) {
    def flow = new StakaterCommands()
    def tokenSecret = flow.getProviderTokenFromJenkinsSecret(tokenSecretName)
    echo "RepoURL: ${repoUrl}"
    String result = repoUrl.substring(repoUrl.indexOf('@')+1)
    result = result.replaceAll(":", '/')
    sh """
        git clone -b ${branch} https://${username}:${tokenSecret}@${result} ${dir}
    """
}

def checkoutRepoUsingTokenWithDefaults(String username, String repoUrl, String branch, String dir) {
    def flow = new StakaterCommands()
    def provider = flow.getProvider(repoUrl)
    def tokenSecret = flow.getProviderToken(provider)
    echo "RepoURL: ${repoUrl}"
    String result = repoUrl.substring(repoUrl.indexOf('@')+1)
    result = result.replaceAll(":", '/')
    sh """
        git clone -b ${branch} https://${username}:${tokenSecret}@${result} ${dir}
    """
}

def addCommentToPullRequest(String message, String user) {
    def flow = new StakaterCommands()
    def url = flow.getScmPushUrl()
    def provider = flow.getProvider(url)
    echo "provider: ${provider}"
    def project = flow.getProject(provider)

    def providerToken = flow.getProviderToken(provider)
    switch(provider) {
        case "github":
            flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken, user)
            break
        case "gitlab":
            def result = flow.getGitLabMergeRequestsByBranchName(project, env.BRANCH_NAME == null ? env.REPO_CLONE_BRANCH : env.BRANCH_NAME, providerToken)
            result.each{value ->
                def prMessage = "@${value.author.username} " + message
                echo "Commenting on MR with id: ${value.iid}, and message: ${prMessage}"
                flow.postPRComment(prMessage, value.iid, project, provider, providerToken, user)
            }
            break
        case "bitbucket":
            def result = flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken, user)
            break

        default:
            error "${provider} is not supported"
            break
    }
}
//Overloaded function to send the token if already got that
def addCommentToPullRequest(String message, String token, String user) {
    def flow = new StakaterCommands()
    def url = flow.getScmPushUrl()
    def provider = flow.getProvider(url)
    echo "provider: ${provider}"
    def project = flow.getProject(provider)

    def providerToken = token
    switch(provider) {
        case "github":
            flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken, user)
            break
        case "gitlab":
            def result = flow.getGitLabMergeRequestsByBranchName(project, env.BRANCH_NAME == null ? env.REPO_CLONE_BRANCH : env.BRANCH_NAME, providerToken)
            result.each{value ->
                def prMessage = "@${value.author.username} " + message
                echo "Commenting on MR with id: ${value.iid}, and message: ${prMessage}"
                flow.postPRComment(prMessage, value.iid, project, provider, providerToken, user)
            }
            break
        case "bitbucket":
            def result = flow.postPRComment(message, env.CHANGE_ID, "${env.REPO_OWNER}/${env.REPO_NAME}", provider, providerToken, user)
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
def createTagAndPushUsingToken(def repoDir, String version) {
    createTagAndPushUsingToken(repoDir, version, "By ${env.JOB_NAME}")
}
def createTagAndPushUsingToken(def repoDir, String version, String message) {
    sh """
        cd ${repoDir}
        git tag -am "${message}" ${version}
        git push origin ${version}
    """
}
def createAndPushTag(def repoDir, String version) {
    createTagAndPush(repoDir, version, "By ${env.JOB_NAME}")
}
def createAndPushTag(def repoDir, String version, String message) {
    sh """
        chmod 600 /root/.ssh-git/ssh-key
        eval `ssh-agent -s`
        ssh-add /root/.ssh-git/ssh-key
        cd ${repoDir}
        git tag -am "${message}" ${version}
        git push --tags
    """
}
def createAndPushTagUsingToken(def repoDir, String version) {
    createTagAndPushUsingToken(repoDir, version, "By ${env.JOB_NAME}")
}
def createAndPushTagUsingToken(def repoDir, String version, String message) {
    sh """
        cd ${repoDir}
        git tag -am "${message}" ${version}
        git push --tags
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

def cloneRepoWithCredentials(String repoURL, String username, String password, String branchName) {
    def common = new io.stakater.Common()
    String newURL = common.replaceCredentialsInHttpURL(repoURL, username, password)
    sh """
        git clone $newURL . 2>/dev/null
        git checkout $branchName
    """
}

def commitFileToRepo(String sourceFilePath, String destinationRepoUrl, String destinationPath, String commitMessage,
                     String username, String password) {
    String messageToCheck = "nothing to commit, working tree clean"
    def common = new io.stakater.Common()
    String newURL = common.replaceCredentialsInHttpURL(destinationRepoUrl, username, password)
    sh """
        cd ..
        mkdir destination-repo-temp
        cd destination-repo-temp
        git clone $newURL .
        yes | cp -rf $sourceFilePath $destinationPath
        git add .
        if ! git status | grep '${messageToCheck}' ; then
            git commit -m "${commitMessage}"
            git push
        else
            echo \\"nothing to do\\"
        fi
        cd $WORKSPACE
    """
}

def configureRepoWithCredentials(String repoURL, String username, String password) {
    def common = new io.stakater.Common()
    String newURL = common.replaceCredentialsInHttpURL(repoURL, username, password)
    sh """
        git remote set-url origin $newURL
    """
}

def ignoredFilesChanged(List<String> ignoreFiles) {

    def result = true
    def raw = sh(returnStdout: true, script: 'git diff --name-only HEAD $(git describe --tags --abbrev=0)').trim()
    def files = raw.split()
    echo "Files to ignore: ${ignoreFiles}"
    echo "Files Changed: ${files}"
    for (f in files){
        for (ext in ignoreFiles){
            if (f.toLowerCase().contains(ext.toLowerCase().trim())){
                result = true
                break
            }else{
                result = false
            }
        }
        if (!result) {
            return false
        }
    }
    return true
}
return this
