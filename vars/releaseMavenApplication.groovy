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
                    stage('Notify') {
                        slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")], prNumber)

                        def commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"
                        git.addCommentToPullRequest(commentMessage)
                    }
                }
            }
        }
    }
}
