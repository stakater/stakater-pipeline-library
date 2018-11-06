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
            def chartManager = new io.stakater.charts.ChartManager()
            def helm = new io.stakater.charts.Helm()
            String chartPackageName = ""
            String helmVersion = ""

            // Slack variables
            def slackChannel = "${env.SLACK_CHANNEL}"
            def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

            def dockerRegistryURL = config.dockerRegistryURL ?: common.getEnvValue('DOCKER_REGISTRY_URL')
            def appName = config.appName ?: ""
            def e2eTestJob = config.e2eTestJob ?: ""
            def performanceTestsJob = config.performanceTestsJob ?: "carbook/performance-tests-manual/add-initial-implementation"
            def mockAppsJobName = config.mockAppsJobName ?: ""
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
                    echo "Image NAME: ${imageName}"
                    if (repoOwner.startsWith('stakater-')){
                        repoOwner = 'stakater'
                    }
                    echo "Repo Owner: ${repoOwner}" 
                    try {
                        stage('Create Version'){
                            dockerImage = "${dockerRegistryURL}/${repoOwner.toLowerCase()}/${imageName}"
                            // If image Prefix is passed, use it, else pass empty string to create versions
                            def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                            def prNumber = "${env.BRANCH_NAME}"                        
                            if (env['BRANCH_NAME'] == null) {
                                echo "Branch Name Null"
                                prNumber = "MR-${env.gitlabMergeRequestIid}"                            
                            }
                            version = stakaterCommands.getImageVersionForCiAndCd(repoUrl,imagePrefix, "${prNumber}", "${env.BUILD_NUMBER}")
                            echo "Version: ${version}"                       
                            fullAppNameWithVersion = imageName + '-'+ version
                        }
                        stage('Build Maven Application') {
                            echo "Building Maven application"   
                            builder.buildMavenApplication(fullAppNameWithVersion)
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
                            chartManager.uploadToChartMuseum(chartDir, repoName.toLowerCase(), chartPackageName, cmUsername, cmPassword)                        
                        }
                        stage('Run Synthetic/E2E Tests') {                        
                            echo "Running synthetic tests for Maven application:  ${e2eTestJob}"   
                            if (!e2eTestJob.equals("")){                     
                                e2eTestStage(appName: appName, e2eJobName: e2eTestJob, performanceTestJobName: performanceTestsJob, chartName: repoName.toLowerCase(), chartVersion: helmVersion, repoUrl: repoUrl, repoBranch: repoBranch, mockAppsJobName: mockAppsJobName, [
                                    microservice: [
                                            name   : repoName.toLowerCase(),
                                            version: helmVersion
                                    ]
                                ])
                            }else{
                                echo "No Job Name passed."
                            }
                        }
                        // If master
                        if (utils.isCD()) {
                            stage("Create Git Tag"){
                                sh """
                                    echo "${version}" > ${versionFile}
                                """
                                git.commitChanges(WORKSPACE, "Bump Version to ${version}")
                                print "Pushing Tag ${version} to Git"
                                git.createTagAndPush(WORKSPACE, version)
                                git.createRelease(version)
                            }
                            stage("Push to Dev-Apps Repo"){

                            }
                        }
                    }
                    catch (e) {
                        slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                        def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                        git.addCommentToPullRequest(commentMessage)

                        throw e
                    }
                    stage('Notify') {
                        slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")])

                        def commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"
                        git.addCommentToPullRequest(commentMessage)
                    }
                }
            }
        }
    }
}
