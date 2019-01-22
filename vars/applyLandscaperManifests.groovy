#!/usr/bin/groovy

def call(body) {
    def config = [:]
    def common = new io.stakater.Common()

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def helmRepoName = config.helmRepoName ?: 'chartmuseum '
    def chartRepositoryURL =  config.chartRepositoryURL ?: common.getEnvValue('CHART_REPOSITORY_URL')
    def cluster = config.cluster ?: 'external'

    def utils = new io.fabric8.Utils()

    if(cluster.equals('external')){
      toolsWithExternalKubeNode(toolsImage: 'stakater/pipeline-tools:1.8.1') {
          container(name: 'tools') {
              withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                  String workspaceDir = WORKSPACE
                  String manifestsDir = workspaceDir + "/manifests/"
                  String preInstall = workspaceDir + "/pre-install"
                  String postInstall = workspaceDir + "/post-install"

                  sh "echo Running toolsWithExternalKubeNode"

                  // Slack variables
                  def slackChannel = "${env.SLACK_CHANNEL}"
                  def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

                  def git = new io.stakater.vc.Git()
                  def slack = new io.stakater.notifications.Slack()
                  def landscaper = new io.stakater.charts.Landscaper()
                  def helm = new io.stakater.charts.Helm()
                  def flow = new io.stakater.StakaterCommands()

                  def imageName = repoName.split("dockerfile-").last().toLowerCase()                
                  def dockerImage = ""
                  def version = ""
                  def prNumber = "${env.REPO_BRANCH}"                        
                  def dockerRepositoryURL = config.dockerRepositoryURL ?: "docker.io"

                  try {
                      stage('Pre install'){
                        sh """
                          if [ -d ${preInstall} ]; then
                            echo "Running Pre Install"
                            cd ${preInstall}
                            chmod +x pre-install.sh
                            ls -l
                            ./pre-install.sh
                            echo "Successfully run Pre Install"
                          fi
                        """
                      }

                      stage('Init Helm') {
                          // Sleep is needed for the first time because tiller pod might not be ready instantly
                          helm.init(false)

                          sh "sleep 30s"

                          helm.addRepo(helmRepoName, chartRepositoryURL)
                      }

                      stage('Dry Run Charts') {
                          landscaper.apply(manifestsDir, true)
                      }

                      if(utils.isCD()) {
                          stage('Create Version') {
                              dockerImage = "${repoOwner.toLowerCase()}/${imageName}"
                              // If image Prefix is passed, use it, else pass empty string to create versions
                              def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                              version = flow.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                              echo "Version: ${version}"                       
                              fullAppNameWithVersion = imageName + '-'+ version
                              echo "Full App name: ${fullAppNameWithVersion}"
                          }

                          stage('Install Charts') {
                              landscaper.apply(manifestsDir, false)
                          }

                          flow.createGitHubRelease(version)
                      }

                      stage('Post install'){
                        sh """
                          if [ -d ${postInstall} ]; then
                            echo "Running Post Install"
                            cd ${postInstall}
                            chmod +x post-install.sh
                            ls -l
                            ./post-install.sh
                            echo "Successfully run Post Install"
                          fi
                        """
                      }
                  } catch(e) {
                      //TODO: Extract test result and send in notification
                      slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                      def commentMessage = "[Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                      git.addCommentToPullRequest(commentMessage)

                      throw e
                  }

                  stage('Notify') {
                      def message
                      def versionFile = ".version"
                      version = common.shOutput("cat ${versionFile}")
                      if (utils.isCD()) {
                          message = "Release ${repoName} ${version}"
                      }
                      else {
                          message = "Dry Run successful"
                      }
                      slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createField("Message", message, false)])

                      git.addCommentToPullRequest(message)
                  }
              }
          }
      }
  } else {
      toolsWithCurrentKubeNode(toolsImage: 'stakater/pipeline-tools:1.8.1') {
          container(name: 'tools') {
              withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->
                  String workspaceDir = WORKSPACE
                  String manifestsDir = workspaceDir + "/manifests/"
                  String preInstall = workspaceDir + "/pre-install"
                  String postInstall = workspaceDir + "/post-install"

                  sh "echo Running toolsWithCurrentKubeNode"

                  // Slack variables
                  def slackChannel = "${env.SLACK_CHANNEL}"
                  def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

                  def git = new io.stakater.vc.Git()
                  def slack = new io.stakater.notifications.Slack()
                  def landscaper = new io.stakater.charts.Landscaper()
                  def helm = new io.stakater.charts.Helm()

                  def imageName = repoName.split("dockerfile-").last().toLowerCase()                
                  def dockerImage = ""
                  def version = ""
                  def prNumber = "${env.REPO_BRANCH}"                        
                  def dockerRepositoryURL = config.dockerRepositoryURL ?: "docker.io"
  
                  try {
                      stage('Pre install'){
                        sh """
                          if [ -d ${preInstall} ]; then
                            echo "Running Pre Install"
                            cd ${preInstall}
                            chmod +x pre-install.sh
                            ls -l
                            ./pre-install.sh
                            echo "Successfully run Pre Install"
                          fi
                        """
                      }

                      stage('Init Helm') {
                          // Sleep is needed for the first time because tiller pod might not be ready instantly
                          helm.init(false)

                          sh "sleep 30s"

                          helm.addRepo(helmRepoName, chartRepositoryURL)
                      }

                      stage('Dry Run Charts') {
                          landscaper.apply(manifestsDir, true)
                      }

                      if(utils.isCD()) {
                          stage('Create Version') {
                              dockerImage = "${repoOwner.toLowerCase()}/${imageName}"
                              // If image Prefix is passed, use it, else pass empty string to create versions
                              def imagePrefix = config.imagePrefix ? config.imagePrefix + '-' : ''                        
                              version = flow.getImageVersionForCiAndCd(repoUrl,imagePrefix, prNumber, "${env.BUILD_NUMBER}")
                              echo "Version: ${version}"                       
                              fullAppNameWithVersion = imageName + '-'+ version
                              echo "Full App name: ${fullAppNameWithVersion}"
                          }
                          stage('Install Charts') {
                              landscaper.apply(manifestsDir, false)
                          }

                          flow.createGitHubRelease(version)
                      }

                      stage('Post install'){
                        sh """
                          if [ -d ${postInstall} ]; then
                            echo "Running Post Install"
                            cd ${postInstall}
                            chmod +x post-install.sh
                            ls -l
                            ./post-install.sh
                            echo "Successfully run Post Install"
                          fi
                        """
                      }
                  } catch(e) {
                      //TODO: Extract test result and send in notification
                      slack.sendDefaultFailureNotification(slackWebHookURL, slackChannel, [slack.createErrorField(e)])

                      def commentMessage = "[Build ${env.BUILD_NUMBER}](${env.BUILD_URL}) has Failed!"
                      git.addCommentToPullRequest(commentMessage)

                      throw e
                  }

                  stage('Notify') {
                      def message
                      def versionFile = ".version"
                      version = common.shOutput("cat ${versionFile}")
                      if (utils.isCD()) {
                          message = "Release ${repoName} ${version}"
                      }
                      else {
                          message = "Dry Run successful"
                      }
                      slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [slack.createField("Message", message, false)])

                      git.addCommentToPullRequest(message)
                  }
              }
          }
      }
  }
}
