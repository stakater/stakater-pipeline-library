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
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch, String repoCloneBranch ->

                def appConfig = new io.stakater.app.AppConfig()
                Map packageConfig = appConfig.getPackageConfig(config)
                Map deploymentConfig = appConfig.getDeploymentConfig(config)
                Map gitConfig = appConfig.getGitConfig(config)
                Map notificationConfig = appConfig.getNotificationConfig(config)
                Map baseConfig = appConfig.getBaseConfig(config, repoName, repoOwner, WORKSPACE)
                Map ecrConfig = appConfig.getEcrConfig(config)
                Map kubernetesConfig = appConfig.getKubernetesConfig(config)

                def docker = new io.stakater.containers.Docker()
                def buildah = new io.stakater.containers.Buildah()
                def git = new io.stakater.vc.Git()
                def utils = new io.fabric8.Utils()
                def chartManager = new io.stakater.charts.ChartManager()
                def notificationManager = new io.stakater.notifications.NotificationManager()
                def nexus = new io.stakater.repository.Nexus()
                def aws = new io.stakater.cloud.Amazon()
                def templates = new io.stakater.charts.Templates()
                def cloneUsingToken = config.usePersonalAccessToken ?: false
                def isPullRequest = repoBranch.contains("PR-") ? true : false

                // Required variables for generating charts
                def deploymentsDir = WORKSPACE + "/deployment"

                String dockerImage = ""
                String version = ""
                def buildException = null

                if (cloneUsingToken && !isPullRequest) {
                    git.cloneRepoWithCredentials(repoUrl, gitConfig.user, gitConfig.tokenSecret, repoCloneBranch)
                }
                else {
                    checkout scm
                }

                container(name: 'tools') {
                    try {
                        echo "Image NAME: ${baseConfig.imageName}"
                        echo "Repo Owner: ${baseConfig.repoOwner}"

                        stage('Create version') {
                            dockerImage = "${packageConfig.dockerRepositoryURL}/${baseConfig.repoOwner.toLowerCase()}/${baseConfig.imageName}"
                            version = app.getImageVersion(repoUrl, baseConfig.imagePrefix, repoBranch, "${env.BUILD_NUMBER}")
                            echo "Version: ${version}"
                        }
                    }
                    catch (e) {
                        print "caught exception during create version stage"
                        buildException = e
                    }
                }
                container(name: 'builder') {
                    try {
                        stage('Build application') {
                            app.build(baseConfig.appType, version, baseConfig.goal)
                        }

                        if (utils.isCD()) {
                            stage('Publish artifact') {
                                if (packageConfig.publishArtifact) {
                                    nexus.pushAppArtifact(baseConfig.imageName, version, packageConfig.javaRepositoryURL, packageConfig.artifactType)
                                }
                            }
                        }
                    }
                    catch (e) {
                        print "caught exception during build & publish stage"
                        buildException = e
                    }
                }

                container(name: 'tools') {
                    git.setUserInfo(gitConfig.user, gitConfig.email)
                    
                    if (ecrConfig.isEcr) {
                        aws.configureECRCredentials(ecrConfig.ecrRegion)
                    }

                    try {
                        if (buildException != null) {
                            throw buildException
                        }

                        stage('Build & push image') {
                            if (packageConfig.useBuildah) {
                                echo "Using buildah to build image"
                                buildah.buildImageWithTagCustom(dockerImage, version, packageConfig.buildahVerifyTls)
                                buildah.pushTagCustom(dockerImage, version, packageConfig.buildahVerifyTls)
                            }
                            else {
                                echo "Using docker to build image"
                                docker.buildImageWithTagCustom(dockerImage, version)
                                docker.pushTagCustom(dockerImage, version)
                            }
                        }

                        stage('Package chart' ) {
                            if (packageConfig.publishChart) {
                                packageConfig.chartPackageName = chartManager.packageChart(repoName, version, dockerImage, baseConfig.kubernetesDir)
                            }
                        }

                        stage('Run Synthetic/E2E tests') {
                            if (packageConfig.executeE2E) {
                                echo "Running synthetic tests for application:  ${repoName}"
                                // def testJob = build job: packageConfig.e2eTestJob, propagate:false
                                build config.e2eJobName
                                // e2eTestStage(appName: baseConfig.name, e2eJobName: packageConfig.e2eTestJob, 
                                //                 performanceTestJobName: packageConfig.performanceTestsJob,
                                //                 chartName: repoName.toLowerCase(), chartVersion: packageConfig.helmVersion,
                                //                 repoUrl: repoUrl, repoBranch: repoBranch, 
                                //                 chartRepositoryURL: packageConfig.chartRepositoryURL, 
                                //                 mockAppsJobName: packageConfig.mockAppsJobName, [
                                //                     microservice: [
                                //                             name   : repoName.toLowerCase(),
                                //                             version: packageConfig.helmVersion
                                //                     ]
                                // ])
                            }
                        }

                        stage("Generate chart templates"){
                            if (kubernetesConfig.kubernetesGenerateManifests && !isPullRequest) {
                                // Generate manifests from chart using pre-defined values.yaml
                                templates.generateManifestsUsingValues(kubernetesConfig.kubernetesPublicChartRepositoryURL,
                                        kubernetesConfig.kubernetesChartName, kubernetesConfig.kubernetesChartVersion,
                                        kubernetesConfig.kubernetesNamespace, deploymentsDir, baseConfig.name)
                                git.commitChangesUsingToken(WORKSPACE, "Update chart templates")
                                if(kubernetesConfig.commitToManifestsRepo) {
                                    String sourceFilePath= WORKSPACE + "/deployment/manifests/" +
                                            kubernetesConfig.kubernetesChartName.substring(kubernetesConfig
                                                    .kubernetesChartName.lastIndexOf("/") + 1) + "/" + baseConfig.name + ".yaml"
                                    git.commitFileToRepo(sourceFilePath, kubernetesConfig.manifestsRepoUrl,
                                            kubernetesConfig.manifestsFilePath, "Updating manifest for ${baseConfig.name}",
                                            gitConfig.user, gitConfig.tokenSecret)
                                }
                            }
                        }

                        // If master
                        if (utils.isCD()) {
                            stage('Upload helm chart') {
                                if (packageConfig.publishChart) {
                                    chartManager.uploadChart(chartRepository, packageConfig.chartRepositoryURL, baseConfig.kubernetesDir,
                                            nexusChartRepoName, repoName, packageConfig.chartPackageName)
                                }
                            }

                            stage("Create git tag"){
                                app.createAndPushTag(gitConfig.cloneUsingToken, WORKSPACE, version)
                            }

                            stage("Deploy") {
                                if (deploymentConfig.deployManifest) {
                                    sh """
                                        make deploy IMAGE_NAME=${dockerImage} IMAGE_TAG=${version} NAMESPACE=${deploymentConfig.namespace}
                                    """
                                }
                            }

                            stage("Push to Dev-Apps Repo") {
                                if (deploymentConfig.pushToDevApps) {
                                    build job: deploymentConfig.devAppsJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartVersion', value: packageConfig.helmVersion ], [$class: 'StringParameterValue', name: 'chartName', value: repoName.toLowerCase() ], [$class: 'StringParameterValue', name: 'chartUrl', value: packageConfig.chartRepositoryURL ], [$class: 'StringParameterValue', name: 'chartAlias', value: repoName.toLowerCase() ]]
                                }
                            }
                        } else {
                            if (packageConfig.runIntegrationTest) {
                                echo "As PR, so rolling back to stable version"
                                sh """
                                    make rollback
                                """
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
