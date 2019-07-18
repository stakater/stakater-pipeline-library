#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def app = new io.stakater.app.App()
    config = app.configure(config)
    
    timestamps {
        stakaterNode(config) {
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm

                def appConfig = new io.stakater.app.AppConfig()
                Map packageConfig = appConfig.getPackageConfig(config)
                Map deploymentConfig = appConfig.getDeploymentConfig(config)
                Map gitConfig = app.getGitConfig(config)
                Map notificationConfig = app.getNotificationConfig(config)
                Map baseConfig = app.getBaseConfig(config, repoName, repoOwner, WORKSPACE)

                def docker = new io.stakater.containers.Docker()
                def stakaterCommands = new io.stakater.StakaterCommands()
                def git = new io.stakater.vc.Git()
                def utils = new io.fabric8.Utils()
                def chartManager = new io.stakater.charts.ChartManager()
                def notificationManager = new io.stakater.notifications.NotificationManager()

                if (gitConfig.cloneUsingToken) {
                    git.configureRepoWithCredentials(repoUrl, gitConfig.user, gitConfig.tokenSecret)
                }

                String dockerImage = ""
                String version = ""

                container(name: 'builder') {
                    try {
                        echo "Image NAME: ${baseConfig.imageName}"
                        echo "Repo Owner: ${baseConfig.repoOwner}"

                        stage('Create Version') {
                            dockerImage = "${packageConfig.dockerRepositoryURL}/${baseConfig.repoOwner.toLowerCase()}/${baseConfig.imageName}"
                            version = stakaterCommands.getImageVersionForNodeCiAndCd(repoUrl, baseConfig.imagePrefix, repoBranch, "${env.BUILD_NUMBER}")
                            echo "Version: ${version}"
                        }

                        stage('Build Application') {
                            app.build(baseConfig.appType, version, baseConfig.goal)
                        }

                    }
                    catch (e) {
                        notificationManager.sendError(notificationConfig, gitConfig, "${env.BUILD_NUMBER}", "${env.BUILD_URL}", repoBranch, e)
                        throw e
                    }
                }

                container(name: 'tools') {    
                    git.setUserInfo(gitConfig.user, gitConfig.email)

                    try {

                        stage('Image build & push') {
                            docker.buildImageWithTagCustom(dockerImage, version)
                            docker.pushTagCustom(dockerImage, version)
                        }

                        stage('Package chart', packageConfig.packageChart) {
                            packageConfig.chartPackageName = chartManager.packageChart(repoName, version, dockerImage, baseConfig.kubernetesDir)
                        }

                        stage('Run Synthetic/E2E Tests', packageConfig.executeE2E) {
                            echo "Running synthetic tests for Node application:  ${packageConfig.e2eTestJob}"   
                            e2eTestStage(appName: baseConfig.name, e2eJobName: packageConfig.e2eTestJob, 
                                            performanceTestJobName: packageConfig.performanceTestsJob,
                                            chartName: repoName.toLowerCase(), chartVersion: packageConfig.helmVersion,
                                            repoUrl: repoUrl, repoBranch: repoBranch, 
                                            chartRepositoryURL: packageConfig.chartRepositoryURL, 
                                            mockAppsJobName: packageConfig.mockAppsJobName, [
                                                microservice: [
                                                        name   : repoName.toLowerCase(),
                                                        version: packageConfig.helmVersion
                                                ]
                            ])  
                        }
                        // If master
                        if (utils.isCD()) {
                            stage('Upload Helm Chart', packageConfig.packageChart) {
                                chartManager.uploadChart(chartRepository, packageConfig.chartRepositoryURL, baseConfig.kubernetesDir,
                                            nexusChartRepoName, repoName, packageConfig.chartPackageName)
                            }

                            stage("Create Git Tag"){
                                app.createAndPushTag(gitConfig.cloneUsingToken, WORKSPACE, version)
                            }

                            stage("Deploy", deploymentConfig.deployManifest) {
                                sh """
                                    make deploy IMAGE_NAME=${dockerImage} IMAGE_TAG=${version} NAMESPACE=${deploymentConfig.namespace}
                                """
                            }

                            stage("Push to Dev-Apps Repo", deploymentConfig.pushToDevApps){
                                build job: deploymentConfig.devAppsJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartVersion', value: packageConfig.helmVersion ], [$class: 'StringParameterValue', name: 'chartName', value: repoName.toLowerCase() ], [$class: 'StringParameterValue', name: 'chartUrl', value: packageConfig.chartRepositoryURL ], [$class: 'StringParameterValue', name: 'chartAlias', value: repoName.toLowerCase() ]]
                            }
                        }
                    }
                    catch (e) {
                        notificationManager.sendError(notificationConfig, gitConfig, "${env.BUILD_NUMBER}", "${env.BUILD_URL}", repoBranch, e)
                        throw e
                    }
                    stage('Notify') {
                        notificationManager.sendSuccess(notificationConfig, gitConfig, dockerImage, version, repoBranch)
                    }
                }
            }
        }
    }
}
