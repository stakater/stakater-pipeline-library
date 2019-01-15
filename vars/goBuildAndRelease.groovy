#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: 'stakater/pipeline-tools:1.6.0'

    toolsNode(toolsImage: toolsImage) {
        container(name: 'tools') {
            withCurrentRepo(type: 'go') { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                String chartPackageName = ""
                String srcDir = WORKSPACE
                def host = repoUrl.substring(repoUrl.indexOf("@") + 1, repoUrl.indexOf(":"))
                def goProjectDir = "/go/src/${host}/${repoOwner}/${repoName}"
                def kubernetesDir = WORKSPACE + "/deployments/kubernetes"

                def chartTemplatesDir = kubernetesDir + "/templates/chart"
                def chartDir = kubernetesDir + "/chart"
                def manifestsDir = kubernetesDir + "/manifests"

                def dockerContextDir = WORKSPACE + "/build/package"
//                def dockerImage = repoOwner.toLowerCase() + "/" + repoName.toLowerCase()
                def dockerImageVersion = ""

                // Slack variables
                def slackChannel = "${env.SLACK_CHANNEL}"
                def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

                def utils = new io.fabric8.Utils()
                def git = new io.stakater.vc.Git()
                def helm = new io.stakater.charts.Helm()
                def templates = new io.stakater.charts.Templates()
                def common = new io.stakater.Common()
                def chartManager = new io.stakater.charts.ChartManager()
                def docker = new io.stakater.containers.Docker()
                def stakaterCommands = new io.stakater.StakaterCommands()
                def slack = new io.stakater.notifications.Slack()
                
                def chartRepositoryURL =  config.chartRepositoryURL ?: common.getEnvValue('CHART_REPOSITORY_URL')
                
                def dockerImage = ""
                def version = ""
                def dockerRepositoryURL = config.dockerRepositoryURL ?: common.getEnvValue('DOCKER_REPOSITORY_URL')

                try {
                    stage('Create Version'){
                        dockerImage = "${dockerRepositoryURL}/${repoOwner.toLowerCase()}/${imageName}"
                        // If image Prefix is passed, use it, else pass empty string to create versions
                        def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                        version = stakaterCommands.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                        echo "Version: ${version}"                       
                        fullAppNameWithVersion = imageName + '-'+ version
                        echo "Full App name: ${fullAppNameWithVersion}"
                    }
                    stage('Download Dependencies') {
                        sh """
                            cd ${goProjectDir}
                            export DOCKER_IMAGE=${dockerImage}
                            make install
                        """
                    }

                    stage('Run Tests') {
                        sh """
                            cd ${goProjectDir}
                            make test
                        """
                    }
                    if (utils.isCI()) {
                        stage('CI: Publish Dev Image') {
                            dockerImageVersion = stakaterCommands.getBranchedVersion("${env.BUILD_NUMBER}")
                            def builder = "docker.io/" + "${dockerImage}:${dockerImageVersion}"
                            sh """
                              cd ${goProjectDir}
                              export DOCKER_TAG=${dockerImageVersion}
                              export BUILDER=${builder}
                              make binary-image
                              make push
                            """
                        }

                        stage('Notify') {
                            def dockerImageWithTag = "${dockerImage}:${dockerImageVersion}"
                            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField(dockerImageWithTag)])

                            def commentMessage = "Image is available for testing. ``docker pull ${dockerImageWithTag}``"
                            git.addCommentToPullRequest(commentMessage)
                            sh """
                                stk notify jira --comment "${commentMessage}"
                            """
                        }
                    } else if (utils.isCD()) {
                        stage('CD: Tag and Push') {
                            print "Generating New Version"
                            def versionFile = ".version"
                            def version = common.shOutput("jx-release-version --gh-owner=${repoOwner} --gh-repository=${repoName} --version-file ${versionFile}")
                            dockerImageVersion = version

                            print "Pushing Tag ${version} to DockerHub"

                            sh """
                                echo "${version}" > ${versionFile}
                                cd ${goProjectDir}
                                export DOCKER_TAG=${version}
                                make binary-image
                                make push
                            """

                            sh """
                                stk notify jira --comment "Version ${version} of ${repoName} has been successfully built and released."
                            """

                            // Render chart from templates
                            templates.renderChart(chartTemplatesDir, chartDir, repoName.toLowerCase(), version, dockerImage)
                            // Generate manifests from chart
                            templates.generateManifests(chartDir, repoName.toLowerCase(), manifestsDir)
                            
                            // Generate combined manifest
                            sh """
                                cd ${manifestsDir}
                                find . -type f -name '*.yaml' -exec cat {} + > ${kubernetesDir}/${repoName.toLowerCase()}.yaml
                            """

                            git.commitChanges(WORKSPACE, "Bump Version to ${version}")

                            print "Pushing Tag ${version} to Git"
                            git.createTagAndPush(WORKSPACE, version)
                            git.createRelease(version)
                        }

                        stage('Chart: Init Helm') {
                            helm.init(true)
                        }

                        stage('Chart: Prepare') {
                            helm.lint(chartDir, repoName.toLowerCase())
                            chartPackageName = helm.package(chartDir, repoName.toLowerCase())
                        }

                        stage('Chart: Upload') {
                            echo ("Uploading chart")
                            String cmUsername = common.getEnvValue('CHARTMUSEUM_USERNAME')
                            String cmPassword = common.getEnvValue('CHARTMUSEUM_PASSWORD')
                            String publicChartRepositoryURL = config.publicChartRepositoryURL
                            String publicChartGitURL = config.publicChartGitURL

                            if (config.chartRepositoryURL) {
                                echo "Uploading to custom chart repository: ${chartRepositoryURL}"
                                chartManager.uploadToChartMuseum(chartDir, repoName.toLowerCase(), chartPackageName, cmUsername, cmPassword, chartRepositoryURL)                        
                            }
                            if (publicChartRepositoryURL && publicChartGitURL) {
                                echo "Uploading to public chart repository: ${publicChartRepositoryURL}"
                                echo "Public chart repository Git URL: ${publicChartGitURL}"

                                def packagedChartLocation = chartDir + "/" + repoName.toLowerCase() + "/" + chartPackageName;
                                chartManager.uploadToStakaterCharts(packagedChartLocation, publicChartRepositoryURL, publicChartGitURL)
                            }
                        }

                        stage('Notify') {
                            def dockerImageWithTag = "${dockerImage}:${dockerImageVersion}"
                            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField(dockerImageWithTag)])

                            def commentMessage = "Image is available for testing. ``docker pull ${dockerImageWithTag}``"
                            git.addCommentToPullRequest(commentMessage)
                        }
                    }
                }
                catch(e) {
                    slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                    def commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                    git.addCommentToPullRequest(commentMessage)
                    sh """
                        stk notify jira --comment "${commentMessage}"
                    """
                    throw e
                }
            }
        }
    }

}
