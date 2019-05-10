#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def stakaterPod = new io.stakater.pods.Pod()
    stakaterPod.setDockerConfig(config)

    timestamps {
        stakaterNode(config) {
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm

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

                Boolean cloneUsingToken = config.usePersonalAccessToken ?: false
                String tokenSecretName = ""
                String tokenSecret = ""

                if (cloneUsingToken) {
                    tokenSecretName = config.tokenCredentialID ?: ""
                    tokenSecret = stakaterCommands.getProviderTokenFromJenkinsSecret(tokenSecretName)
                    git.configureRepoWithCredentials(repoUrl, gitUser, tokenSecret)
                }

                Boolean notifySlack = config.notifySlack == false ? false : true
                String slackChannel = ""
                String slackWebHookURL = ""
                if (notifySlack) {
                    // Slack variables
                    slackChannel = common.getEnvValue('SLACK_CHANNEL')
                    slackWebHookURL = common.getEnvValue('SLACK_WEBHOOK_URL')
                }

                String dockerRepositoryURL = config.dockerRepositoryURL ?: ""
                String chartRepositoryURL =  config.chartRepositoryURL ?: ""
                String gitUser = config.gitUser ?: "stakater-user"
                String gitEmailID = config.gitEmail ?: "stakater@gmail.com"
                String devAppsJobName = config.devAppsJobName ?: ""
                String e2eTestJob = config.e2eTestJob ?: ""
                String appName = config.appName ?: ""
                String performanceTestsJob = config.performanceTestsJob ?: ""
                String mockAppsJobName = config.mockAppsJobName ?: ""

                String dockerImage = ""
                String version = ""

                container(name: 'tools') {
                    withCurrentRepo(gitUsername: gitUser, gitEmail: gitEmailID) { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                        String kubernetesDir = WORKSPACE + "/deployments/kubernetes"

                        String imageName = repoName.split("dockerfile-").last().toLowerCase()
                        String fullAppNameWithVersion = ""

                        String prNumber = "${env.REPO_BRANCH}"

                        echo "Image NAME: ${imageName}"
                        if (repoOwner.startsWith('stakater-')){
                            repoOwner = 'stakater'
                        }
                        echo "Repo Owner: ${repoOwner}"
                        try {
                            stage('Create Version'){
                                dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"
                                // If image Prefix is passed, use it, else pass empty string to create versions
                                String imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''
                                version = stakaterCommands.getImageVersionForNodeCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                                echo "Version: ${version}"
                                fullAppNameWithVersion = imageName + '-'+ version
                            }
                            stage('Build Node Application') {
                                echo "Building Node application"
                                builder.buildNodeApplication(version)
                            }
                            stage('Image build & push') {
                                sh """
                                    export DOCKER_IMAGE=${dockerImage}
                                    export DOCKER_TAG=${version}
                                """
                                docker.buildImageWithTagCustom(dockerImage, version)
                                docker.pushTagCustom(dockerImage, version)
                            }

                            if (! chartRepositoryURL.equals("")) {
                                stage('Package chart') {
                                    chartPackageName = chartManager.packageChart(repoName, version, dockerImage, kubernetesDir)
                                }
                            }

                            stage('Run Synthetic/E2E Tests') {
                                echo "Running synthetic tests for Node application:  ${e2eTestJob}"   
                                if (!e2eTestJob.equals("")){
                                    e2eTestStage(appName: appName, e2eJobName: e2eTestJob, performanceTestJobName: performanceTestsJob, chartName: repoName.toLowerCase(), chartVersion: helmVersion, repoUrl: repoUrl, repoBranch: repoBranch, chartRepositoryURL: chartRepositoryURL, mockAppsJobName: mockAppsJobName, [
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
                                stage("Push Changes") {
                                    print "Pushing changes to Git"
                                    git.commitChanges(WORKSPACE, "Update chart and version")
                                }
                                if (! chartRepositoryURL.equals("")) {
                                    stage('Upload Helm Chart') {
                                        chartManager.uploadChart(chartRepository, chartRepositoryURL, kubernetesDir,
                                                    nexusChartRepoName, repoName, chartPackageName)
                                    }
                                }

                                stage("Create Git Tag"){
                                    print "Pushing Tag ${version} to Git"
                                    git.createTagAndPush(WORKSPACE, version)
                                }

                                stage("Push to Dev-Apps Repo"){
                                    build job: devAppsJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartVersion', value: helmVersion ], [$class: 'StringParameterValue', name: 'chartName', value: repoName.toLowerCase() ], [$class: 'StringParameterValue', name: 'chartUrl', value: chartRepositoryURL ], [$class: 'StringParameterValue', name: 'chartAlias', value: repoName.toLowerCase() ]]
                                }
                            }
                        }
                        catch (e) {
                            if (notifySlack) {
                                slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)], prNumber)
                            }

                            String commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"

                            if(cloneUsingToken) {
                                git.addCommentToPullRequest(commentMessage, tokenSecret)
                            } else {
                                git.addCommentToPullRequest(commentMessage)
                            }
                            throw e
                        }
                        stage('Notify') {
                            if (notifySlack) {
                                slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")], prNumber)
                            }

                            String commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"

                            if(cloneUsingToken){
                                git.addCommentToPullRequest(commentMessage, tokenSecret)
                            } else {
                                git.addCommentToPullRequest(commentMessage)
                            }
                        }
                    }
                }
            }
        }
    }
}
