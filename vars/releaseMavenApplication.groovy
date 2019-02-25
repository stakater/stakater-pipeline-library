#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    timestamps {
        toolsNode(toolsImage: 'stakater/builder-maven:3.5.4-jdk1.8-apline8-v0.0.3') {

            def builder = new io.stakater.builder.Build()
            def docker = new io.stakater.containers.Docker()
            def stakaterCommands = new io.stakater.StakaterCommands()
            def git = new io.stakater.vc.Git()
            def slack = new io.stakater.notifications.Slack()
            def common = new io.stakater.Common()
            def utils = new io.fabric8.Utils()
            def templates = new io.stakater.charts.Templates()
            def nexus = new io.stakater.repository.Nexus()   
            def chartManager = new io.stakater.charts.ChartManager()
            def chartRepositoryURL =  config.chartRepositoryURL ?: common.getEnvValue('CHART_REPOSITORY_URL')
            def javaRepositoryURL = config.javaRepositoryURL ?: common.getEnvValue('JAVA_REPOSITORY_URL')
            def rdlmURL = config.rdlmURL ?: "http://restful-distributed-lock-manager.release:8080/locks/mock"
            def runE2ETests = config.runE2ETests ?: true
            def deployDevApps = config.deployDevApps ?: true
            def helm = new io.stakater.charts.Helm()
            String chartPackageName = ""
            String helmVersion = ""

            // Slack variables
            def slackChannel = "${env.SLACK_CHANNEL}"
            def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

            def dockerRepositoryURL = config.dockerRepositoryURL ?: common.getEnvValue('DOCKER_REPOSITORY_URL')
            def appName = config.appName ?: ""
            def e2eTestJob = config.e2eTestJob ?: ""
            def performanceTestsJob = config.performanceTestsJob ?: "carbook/performance-tests-manual/add-initial-implementation"
            def mockAppsJobName = config.mockAppsJobName ?: ""
            def devAppsJobName = config.devAppsJobName ?: ""
            def gitUser = config.gitUser ?: "stakater-user"
            def gitEmailID = config.gitEmail ?: "stakater@gmail.com"

            def dockerImage = ""
            def version = ""

            container(name: 'tools') {
                withCurrentRepo(gitUsername: gitUser, gitEmail: gitEmailID) { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                    def kubernetesDir = WORKSPACE + "/deployments/kubernetes"
                    def chartTemplatesDir = kubernetesDir + "/templates/chart"
                    def chartDir = kubernetesDir + "/chart"
                    def manifestsDir = kubernetesDir + "/manifests"

                    def imageName = repoName.split("dockerfile-").last().toLowerCase()
                    def fullAppNameWithVersion = ""
                    
                    def prNumber = "${env.REPO_BRANCH}"                        

                    echo "Image NAME: ${imageName}"
                    if (repoOwner.startsWith('stakater-')){
                        repoOwner = 'stakater'
                    }
                    echo "Repo Owner: ${repoOwner}" 
                    try {
                        stage('Create Version'){
                            dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"
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
                        stage('Image build & push') {
                            sh """
                                export DOCKER_IMAGE=${dockerImage}
                                export DOCKER_TAG=${version}
                            """
                            docker.buildImageWithTagCustom(dockerImage, version)
                            docker.pushTagCustom(dockerImage, version)
                        }
                        stage('Publish & Upload Helm Chart'){
                            echo "Rendering Chart & generating manifests"
                            helm.init(true)
                            helm.lint(chartDir, repoName.toLowerCase())
                            
                            if (version.contains("SNAPSHOT")) {
                                helmVersion = "0.0.0"
                            }else{
                                helmVersion = version.substring(1)
                            }
                            echo "Helm Version: ${helmVersion}"
                            // Render chart from templates
                            templates.renderChart(chartTemplatesDir, chartDir, repoName.toLowerCase(), version, helmVersion, dockerImage)
                            // Generate manifests from chart
                            templates.generateManifests(chartDir, repoName.toLowerCase(), manifestsDir)
                            chartPackageName = helm.package(chartDir, repoName.toLowerCase(),helmVersion)                        
                            
                            String cmUsername = common.getEnvValue('CHARTMUSEUM_USERNAME')
                            String cmPassword = common.getEnvValue('CHARTMUSEUM_PASSWORD')
                            chartManager.uploadToChartMuseum(chartDir, repoName.toLowerCase(), chartPackageName, cmUsername, cmPassword, chartRepositoryURL)                        
                        }
                        if (runE2ETests) {
                            stage('Run Synthetic/E2E Tests') {                        
                                echo "Running synthetic tests for Maven application:  ${e2eTestJob}"   
                                if (!e2eTestJob.equals("")){                     
                                    e2eTestStage(appName: appName, e2eJobName: e2eTestJob, performanceTestJobName: performanceTestsJob, chartName: repoName.toLowerCase(), chartVersion: helmVersion, repoUrl: repoUrl, repoBranch: repoBranch, chartRepositoryURL: chartRepositoryURL, mockAppsJobName: mockAppsJobName, rdlmURL: rdlmURL, [
                                        microservice: [
                                                name   : repoName.toLowerCase(),
                                                version: helmVersion
                                        ]
                                    ])
                                }else{
                                    echo "No Job Name passed."
                                }
                            }
                        }
                        // If master
                        if (utils.isCD()) {
                            if (deployUsingMakeTarget){
                                echo "Deploying Chart using make target"   
                                builder.deployHelmChart(chartDir)
                            }
                            stage('Push Jar') {
                                nexus.pushAppArtifact(imageName, version, javaRepositoryURL)                      
                            }
                            stage("Push Changes") {
                                print "Pushing changes to Git"
                                git.commitChanges(WORKSPACE, "Update chart and version")
                            }
                            stage("Create Git Tag"){                          
                                print "Pushing Tag ${version} to Git"
                                git.createTagAndPush(WORKSPACE, version)
                            }
                            if (deployDevApps){
                                stage("Push to Dev-Apps Repo"){
                                    build job: devAppsJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartVersion', value: helmVersion ], [$class: 'StringParameterValue', name: 'chartName', value: repoName.toLowerCase() ], [$class: 'StringParameterValue', name: 'chartUrl', value: chartRepositoryURL ], [$class: 'StringParameterValue', name: 'chartAlias', value: repoName.toLowerCase() ]]
                                }
                            }
                        }
                        stage('Notify') {
                            def commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"
                            git.addCommentToPullRequest(commentMessage)

                            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")], prNumber)
                        }
                    }
                    catch (e) {
                        slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)], prNumber)

                        def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                        git.addCommentToPullRequest(commentMessage)

                        throw e
                    }
                }
            }
        }
    }
}