#!/usr/bin/groovy
package io.stakater
import groovy.json.JsonSlurperClassic

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

def getGitHubToken(provider) {
    def tokenPath
    switch(provider) {
        case "github":
            tokenPath = '/home/jenkins/.apitoken/hub'

        case "gitlab":
            tokenPath = '/home/jenkins/.apitoken/gitlab.hub'
    }

    def githubToken = readFile tokenPath
    if (!githubToken?.trim()) {
        error "No GitHub token found in ${tokenPath}"
    }
    return githubToken.trim()
}

def isAuthorCollaborator(githubToken, project) {

    if (!githubToken){

        githubToken = getGitHubToken("github")

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

def getProject(provider){
    def url = getScmPushUrl()
    return extractOrganizationAndProjectFromUrl(url, provider)
}

/**
 * Should be called after checkout scm
 */
@NonCPS
def getScmPushUrl() {
    def url = sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()

    if (!url){
        error "no URL found for git config --get remote.origin.url "
    }
    return url
}

def getProvider(url) {
    if (url.contains("github.com")){
        return 'github'
    } else if (url.contains("gitlab.com")){
        return 'gitlab'
    } else {
         error "${url} is not a GitHub URL, neither a Gitlab URL"
    }
}

def extractOrganizationAndProjectFromUrl(url, provider) {
    switch(provider) {
        case "github": 
            return formatGithubUrl(url)

        case "gitlab":
            return formatGitlabUrl(url)

        default:
            error "${provider} is not supported"
    }
}

def formatGithubUrl(url) {
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

def formatGitlabUrl(url) {
    if (url.contains("https://gitlab.com/")){
        url = url.replaceAll("https://gitlab.com/", '')
    } else if (url.contains("git@gitlab.com:")){
        url = url.replaceAll("git@gitlab.com:", '')
    }

    if (url.contains(".git")){
        url = url.replaceAll("\\.git", '')
    }
    return url.trim()
}

def extractOrganizationAndProjectFromGitHubUrl(url) {
    if (!url.contains('github.com') && !url.contains('gitlab.com')){
        error "${url} is not a GitHub URL, neither a Gitlab URL"
    }

    if (url.contains("https://github.com/")){
        url = url.replaceAll("https://github.com/", '')
    } else if (url.contains("git@github.com:")){
        url = url.replaceAll("git@github.com:", '')
    } else if (url.contains("https://gitlab.com/")){
        url = url.replaceAll("https://gitlab.com/", '')
 //       url = url.replaceAll("/", "%2F")
    } else if (url.contains("git@gitlab.com:")){
        url = url.replaceAll("git@gitlab.com:", '')
//        url = url.replaceAll("/", "%2F")
    }

    if (url.contains(".git")){
        url = url.replaceAll("\\.git", '')
    }
    return url.trim()
}

def postPRComment(comment, pr, project, provider) {
    switch(provider){
        case "github":
            postPRCommentToGithub(comment, pr, project)
        
        case "gitlab":
            postPRCommentToGitlab(comment, pr, project)
        
        default: 
            error "${provider} is not supported"
    }
}

def postPRCommentToGitlab(comment, pr, project) {
    project = project.replaceAll("/", "%2F")
    echo "Inside project: ${project}"
    def gitlabToken = getGitHubToken("gitlab")
    echo "Gitlab-token : ${gitlabToken}"
    def apiUrl = new URL("https://gitlab.com/api/v4/projects/${project}/merge_requests/1/notes?body=${java.net.URLEncoder.encode(comment, 'UTF-8')}")
    
    echo "adding ${comment} to ${apiUrl}"
        try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (gitlabToken.length() > 0) {
            connection.setRequestProperty("PRIVATE-TOKEN", "${gitlabToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()
    } catch (err) {
        error "ERROR  ${err}"
    }
}

def getGitLabMergeRequestsByBranchName(project, branchName){
    def gitlabToken = getGitHubToken("gitlab")
    echo "Fetching all MRs for ${branchName}"
    def apiUrl = new URL("https://gitlab.com/api/v4/projects/${project}/merge_requests?state=opened&source_branch=${branchName}")
    
    try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (gitlabToken.length() > 0) {
            connection.setRequestProperty("PRIVATE-TOKEN", "${gitlabToken}")
        }
        connection.setRequestMethod("GET")
        connection.setDoOutput(true)
        connection.connect()

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.flush()

        // execute the POST request
        rs = new JsonSlurperClassic().parse(new InputStreamReader(connection.getInputStream()))

        echo "Result: ${rs}"

        return rs
    } catch (err) {
        error "ERROR  ${err}"
    } finally {
        connection.disconnect()
    }
}

def postPRCommentToGithub(comment, pr, project) {
    def githubToken = getGitHubToken("github")
    def apiUrl = new URL("https://api.github.com/repos/${project}/issues/${pr}/comments")
    echo "adding ${comment} to ${apiUrl}"
    try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = "{\"body\":\"${comment}\"}"

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()
    } catch (err) {
        error "ERROR  ${err}"
    }
}

// If branch other then master, append branch name to version
// in order to avoid conflicts in artifact releases
def getBranchedVersion(String version) {
    def utils = new io.fabric8.Utils()

    def branchName = utils.getBranch()

    if (!branchName.equalsIgnoreCase("master")){
        version = branchName + "-" + version
    }

    return version
}

/*
Checks the file passed as parameter, if present reads the file and returns the version
If not present, returns 1.0.0
*/
def ReadVersionFromFile(String file) {    
    def versionInFile = ''
    try {        
        versionInFile = readFile('.version').trim()    
    } catch (Exception e) {
        println "File Not Present, so starting from 0.0.1"
        versionInFile = '0.0.1'        
    }
    return versionInFile
}


/**
 * Returns the complete tagged string for CI (PRs) or CD(Master-Branch). In case of CD, it creates and push a release & a tag
 *
 * @param imagePrefix - passed from Jenkins file
 * @param prNumber - Used only in case of CI
 * @param buildNumber - Used only in case of CI
 * @return
 */
def createImageVersionForCiAndCd(String imagePrefix, String prNumber, String buildNumber) {
    def utils = new io.fabric8.Utils()
    def branchName = utils.getBranch()
    def git = new io.stakater.vc.Git()
    def imageVersion = ''

    // For CD
    if (branchName.equalsIgnoreCase("master")) {
        sh "stk generate version > commandResult"
        def version = readFile('commandResult').trim()
        version = 'v' + version
        git.createTagAndPush(WORKSPACE, version)
        git.createRelease(version)

        imageVersion = imagePrefix + version
    }
    // For CI
    else {
        imageVersion = imagePrefix + 'SNAPSHOT-' + prNumber + '-' + buildNumber
    }

    return imageVersion
}

def createGitHubRelease(def version) {
    def githubToken = getGitHubToken("github")
    def githubProject = getGitHubProject()

    def apiUrl = new URL("https://api.github.com/repos/${githubProject}/releases")
    echo "creating release ${version} on ${apiUrl}"

    try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if(githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = """
            {
            "tag_name": "${version}",
            "name": "${version}"
            }
        """
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()
    } catch (err) {
        error "ERROR  ${err}"
    }
}

return this
