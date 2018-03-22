#!/usr/bin/groovy
package io.stakater

def setupWorkspaceForRelease(String project, String useGitTagOrBranchForNextVersion = "", String mvnExtraArgs = "", String currentVersion = "") {
    def flow = new io.fabric8.Fabric8Commands()

    sh "git config user.email admin@stakater.com"
    sh "git config user.name stakater-release"

    sh "git tag -d \$(git tag)"
    sh 'eval "$(ssh-agent -s)" && ssh-add /root/.ssh-git/ssh-key && git fetch --tags'

    if (!useGitTagOrBranchForNextVersion.equalsIgnoreCase("branch")) {
        def newVersion = flow.getNewVersionFromTag(currentVersion)
        echo "New release version ${newVersion}"
        sh "./mvnw -U versions:set -DnewVersion=${newVersion} " + mvnExtraArgs
        sh "git commit -a -m 'release ${newVersion}'"
        flow.pushTag(newVersion)
    } else {
        sh './mvnw build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion} ' + mvnExtraArgs
    }

    if (!useGitTagOrBranchForNextVersion.equalsIgnoreCase("tag")) {
        def releaseVersion = flow.getProjectVersion()

        // delete any previous branches of this release
        try {
            sh "git checkout -b release-v${releaseVersion}"
        } catch (err) {
            sh "git branch -D release-v${releaseVersion}"
            sh "git checkout -b release-v${releaseVersion}"
        }
    }
}

def stageSonartypeRepo() {
    def flow = new io.fabric8.Fabric8Commands()

    try {
        sh "./mvnw clean -B"
        sh "./mvnw -V -B -e -U install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:deploy -P release -P openshift -DnexusUrl=http://nexus -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

    } catch (err) {
        hubot room: 'release', message: "Release failed when building and deploying to Nexus ${err}"
        currentBuild.result = 'FAILURE'
        error "ERROR Release failed when building and deploying to Nexus ${err}"
    }
    // the sonartype staging repo id gets written to a file in the workspace
    return flow.getRepoIds()
}

def releaseSonartypeRepo(String repoId) {
    try {
        // release the sonartype staging repo
        sh "./mvnw org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DnexusUrl=http://nexus -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"

    } catch (err) {
        sh "./mvnw org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DnexusUrl=http://nexus -DstagingRepositoryId=${repoId} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
        currentBuild.result = 'FAILURE'
        error "ERROR releasing sonartype repo ${repoId}: ${err}"
    }
}

def getProjectVersion() {
    def file = readFile('pom.xml')
    def project = new XmlSlurper().parseText(file)
    return project.version.text()
}

def updateGithub() {
    def releaseVersion = getProjectVersion()
    sh "git push origin release-v${releaseVersion}"
}

def getGitHubToken() {
    def tokenPath = '/home/jenkins/.apitoken/hub'
    def githubToken = readFile tokenPath
    if (!githubToken?.trim()) {
        error "No GitHub token found in ${tokenPath}"
    }
    return githubToken.trim()
}

def isAuthorCollaborator(githubToken, project) {

    if (!githubToken){

        githubToken = getGitHubToken()

        if (!githubToken){
            echo "No GitHub api key found so trying annonynous GitHub api call"
        }
    }
    if (!project){
        project = getGitHubProject()
    }

    def changeAuthor = env.CHANGE_AUTHOR
    if (!changeAuthor){
        error "No commit author found.  Is this a pull request pipeline?"
    }
    echo "Checking if user ${changeAuthor} is a collaborator on ${project}"

    def apiUrl = new URL("https://api.github.com/repos/${project}/collaborators/${changeAuthor}")

    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken != null && githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("GET")
    connection.setDoOutput(true)

    try {
        connection.connect()
        new InputStreamReader(connection.getInputStream(), "UTF-8")
        return true
    } catch (FileNotFoundException e1) {
        return false
    } finally {
        connection.disconnect()
    }

    error "Error checking if user ${changeAuthor} is a collaborator on ${project}."

}

def getGitHubProject(){
    def url = getScmPushUrl()
    return extractOrganizationAndProjectFromGitHubUrl(url)
}

/**
 * Should be called after checkout scm
 */
@NonCPS
def getScmPushUrl() {
    def url = sh(returnStdout: true, script: 'cd ${WORKSPACE} && git config --get remote.origin.url').trim()

    if (!url){
        error "no URL found for git config --get remote.origin.url "
    }
    return url
}

def extractOrganizationAndProjectFromGitHubUrl(url) {
    if (!url.contains('github.com')){
        error "${url} is not a GitHub URL"
    }

    if (url.contains("https://github.com/")){
        url = url.replaceAll("https://github.com/", '')

    } else if (url.contains("git@github.com:")){
        url = url.replaceAll("git@github.com:", '')
    }

    if (url.contains(".git")){
        url = url.replaceAll("\\.git", '')
    }
    return url.trim()
}

return this
