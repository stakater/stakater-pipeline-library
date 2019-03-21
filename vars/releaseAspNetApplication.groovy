#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    timestamps {
        toolsNode(toolsImage: 'stakater/builder-dotnet:2.2-centos7') {

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
            def helm = new io.stakater.charts.Helm()            

            //Variables from Jenkinsfile config
            def chartRepositoryURL =  config.chartRepositoryURL ?: common.getEnvValue('CHART_REPOSITORY_URL')    
            def rdlmURL = config.rdlmURL ?: "http://restful-distributed-lock-manager.release:8080/locks/mock"
            def deployUsingMakeTarget = config.deployUsingMakeTarget ?: false
            def dockerRepositoryURL = config.dockerRepositoryURL ?: common.getEnvValue('DOCKER_REPOSITORY_URL')
            def appName = config.appName ?: ""
            def e2eTestJob = config.e2eTestJob ?: ""
            def performanceTestsJob = config.performanceTestsJob ?: "carbook/performance-tests-manual/add-initial-implementation"
            def mockAppsJobName = config.mockAppsJobName ?: ""
            def devAppsJobName = config.devAppsJobName ?: ""
            def gitUser = config.gitUser ?: "stakater-user"
            def gitEmailID = config.gitEmail ?: "stakater@gmail.com"
            def cloneUsingToken = config.usePersonalAccessToken ?: false
            def jenkinsSecretForToken = config.tokenCredential ?: "git-token"
            
            // Slack variables
            def slackChannel = "${env.SLACK_CHANNEL}"
            def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"  

            

            container(name: 'tools') {
                withCurrentRepo(gitUsername: gitUser, gitEmail: gitEmailID, useToken: cloneUsingToken ) { def repoUrl, def repoName, def repoOwner, def repoBranch ->                  
                    // Variables used in multiple stages                  
                    def dockerImage = ""
                    def version = ""
                    String helmVersion = ""
                    def kubernetesDir = WORKSPACE + "/deployments/kubernetes"
                    def chartTemplatesDir = kubernetesDir + "/templates/chart"
                    def chartDir = kubernetesDir + "/chart"
                    def manifestsDir = kubernetesDir + "/manifests"
                    def prNumber = "${env.REPO_BRANCH}"                        
                    def imageName = repoName.split("dockerfile-").last().toLowerCase()
                    def repoNameLowerCase = repoName.toLowerCase()
                                        
                    if (repoOwner.startsWith('stakater-')){
                        repoOwner = 'stakater'
                    }
                    echo "Image NAME: ${imageName}"
                    echo "Repo Owner: ${repoOwner}" 
                    try {
                        stage('Build'){   

                            echo "Creating Version"
                            dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"                            
                            // If image Prefix is passed, use it, else pass empty string to create versions
                            def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                            version = stakaterCommands.getImageVersionForCiAndCd(imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                            echo "Version: ${version}" 

                            echo "Building Asp.Net application"   
                            builder.buildAspNetApplication()                                                                         
                            
                            echo "Building Docker Image"   
                            sh """
                                export DOCKER_IMAGE=${dockerImage}
                                export DOCKER_TAG=${version}
                            """
                            docker.buildImage(dockerImage, version)
                        }
                        stage('Publish'){
                            echo "Publishing Docker Image"   
                            docker.pushImage(dockerImage, version)
                            echo "Rendering Chart & generating manifests"
                            helm.init(true)
                            helm.lint(chartDir, repoNameLowerCase)
                            
                            if (version.contains("SNAPSHOT")) {
                                helmVersion = "0.0.0"
                            }else{
                                helmVersion = version.substring(1)
                            }
                            echo "Helm Version: ${helmVersion}"                            
                            // Render chart from templates
                            templates.renderChart(chartTemplatesDir, chartDir, repoNameLowerCase, version, helmVersion, dockerImage)
                            // Generate manifests from chart
                            templates.generateManifests(chartDir, repoNameLowerCase, manifestsDir)
                            String chartPackageName = helm.package(chartDir, repoNameLowerCase, helmVersion)                        
                            
                            String cmUsername = "${env.CHARTMUSEUM_USERNAME}"
                            String cmPassword = "${env.CHARTMUSEUM_PASSWORD}"
                            chartManager.uploadToChartMuseum(chartDir, repoNameLowerCase, chartPackageName, cmUsername, cmPassword, chartRepositoryURL)                        
                        }
                        if (!e2eTestJob.equals("")){
                            stage('Run Synthetic/E2E Tests') {                        
                                echo "Running synthetic tests for Maven application:  ${e2eTestJob}"                                   
                                e2eTestStage(appName: appName, e2eJobName: e2eTestJob, performanceTestJobName: performanceTestsJob, chartName: repoNameLowerCase, chartVersion: helmVersion, repoUrl: repoUrl, repoBranch: repoBranch, chartRepositoryURL: chartRepositoryURL, mockAppsJobName: mockAppsJobName, rdlmURL: rdlmURL, [
                                    microservice: [
                                            name   : repoNameLowerCase,
                                            version: helmVersion
                                    ]
                                ])                                
                            }
                        }else{                            
                            echo "No E2E Job Name passed, so skipping e2e tests"
                        }                        
                        // If master
                        if (utils.isCD()) {
                            if (deployUsingMakeTarget == true) {
                                echo "Deploying Chart using make target"   
                                builder.deployHelmChart(chartDir)
                            }
                            stage("Tag") {
                                print "Pushing changes to Git"
                                git.commitChanges(WORKSPACE, "Update chart and version")                       
                                print "Pushing Tag ${version} to Git"
                                git.createTagAndPush(WORKSPACE, version)
                            }
                            if (!devAppsJobName.equals("")){
                                stage("Push to Dev-Apps Repo"){
                                    build job: devAppsJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartVersion', value: helmVersion ], [$class: 'StringParameterValue', name: 'chartName', value: repoNameLowerCase ], [$class: 'StringParameterValue', name: 'chartUrl', value: chartRepositoryURL ], [$class: 'StringParameterValue', name: 'chartAlias', value: repoNameLowerCase ]]
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