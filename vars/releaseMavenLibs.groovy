#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsNode(toolsImage: 'stakater/builder-maven:3.5.4-jdk1.8-apline8-v0.0.3') {

        def builder = new io.stakater.builder.Build()
        def stakaterCommands = new io.stakater.StakaterCommands()
        def git = new io.stakater.vc.Git()
        def slack = new io.stakater.notifications.Slack()
        def common = new io.stakater.Common()
        def utils = new io.fabric8.Utils()
        def templates = new io.stakater.charts.Templates()
        def nexus = new io.stakater.repository.Nexus()    

        // Slack variables
        def slackChannel = "${env.SLACK_CHANNEL}"
        def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"        

        def version = ""

        container(name: 'tools') {
            withCurrentRepo() { def repoUrl, def repoName, def repoOwner, def repoBranch ->                
                def imageName = repoName.toLowerCase()
                def fullAppNameWithVersion = ""
                def prNumber = "${env.REPO_BRANCH}"

                echo "Image NAME: ${imageName}"
                
                if (repoOwner.startsWith('stakater-')){
                    repoOwner = 'stakater'
                }
                echo "Repo Owner: ${repoOwner}" 
                try {
                    stage('Create Version'){
                        // If image Prefix is passed, use it, else pass empty string to create versions
                        def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                        version = stakaterCommands.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")                        
                        echo "Version: ${version}"                       
                        fullAppNameWithVersion = imageName + '-'+ version
                    }
                    stage('Build Maven Application') {
                        echo "Building Maven application"   
                        builder.buildMavenApplication(version)
                    }
                    stage('Push Jar') {
                        nexus.pushAppArtifact(imageName, version)                      
                    }
                    // If master
                    if (utils.isCD()) {
                        stage("Create Git Tag"){
                            print "Pushing Tag ${version} to Git"
                            git.createTagAndPush(WORKSPACE, version)
                            // echo "Creating Git Release"
                            // git.createRelease(version)
                        }                        
                    }
                }
                catch (e) {
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)], prNumber)                    

                    def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)

                    throw e
                }
                stage('Notify') {                    
                    slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createArtifactField("${fullAppNameWithVersion}")], prNumber)

                    def commentMessage = "Artifact is available for testing. `${fullAppNameWithVersion}`"
                    git.addCommentToPullRequest(commentMessage)
                }
            }
        }
    }
}
