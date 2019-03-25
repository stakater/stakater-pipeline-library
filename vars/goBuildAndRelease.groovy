#!/usr/bin/groovy
import groovy.json.*

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: 'stakater/pipeline-tools:1.14.1'

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

                def imageName = repoName.split("dockerfile-").last().toLowerCase()                
                def dockerImage = ""
                def version = ""
                def prNumber = "${env.REPO_BRANCH}"                        
                def dockerRepositoryURL = config.dockerRepositoryURL ?: "docker.io"

                try {
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
                            if (config.nexusChartRepoURL) {
                                def username, password

                                withCredentials([usernamePassword(credentialsId: 'nexus-stackator-admin', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                                    username = env.USER
                                    password = env.PASS
                                }

                                echo "User: ${username}"
                                echo "Pass: ${password}"

                                ////////////////////////////////////////////////////////////////
                                // 1st step: Upload new chart to nexus
                                ////////////////////////////////////////////////////////////////

                                def packagedChartLocation = chartDir + "/" + repoName.toLowerCase() + "/" + chartPackageName;
                                echo "Packaged Chart Location: ${packagedChartLocation}"

                                sh "curl -u ${username}:${password} --upload-file ${packagedChartLocation} ${config.nexusChartRepoURL}/repository/${config.nexusChartRepoName} -v"

                                ////////////////////////////////////////////////////////////////
                                // 2nd step: 
                                ////////////////////////////////////////////////////////////////

                                def response = sh(script: "curl -u ${username}:${password} -X GET ${config.nexusChartRepoURL}/service/rest/v1/assets?repository=${config.nexusChartRepoName} -v", returnStdout: true)
                                echo "Response: ${response}"

                                def responseJSON = new JsonSlurperClassic().parseText(response)

                                echo "Response JSON: ${responseJSON}"

                                echo "Items: ${responseJSON.items}"

                                sh "mkdir nexus-charts"

                                responseJSON.items.each{item -> 
                                    echo "URL: ${item.downloadUrl}"
                                    sh """
                                        cd nexus-charts
                                        curl -u ${username}:${password} --remote-name ${item.downloadUrl} -v
                                    """
                                }
                            }
                        }

                        stage('Notify') {
                            def dockerImageWithTag = "${dockerImage}:${version}"
                            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField(dockerImageWithTag)])

                            def commentMessage = "Image is available for testing. ``docker pull ${dockerImageWithTag}``"
                            git.addCommentToPullRequest(commentMessage)
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
