#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsImage = config.toolsImage ?: "stakater/pipeline-tools:1.13.2"
    module = config.module ?: "github" // for compatibility with old repos

    toolsNode(toolsImage: toolsImage) {
    container(name: "tools") {
      withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->

        def utils = new io.fabric8.Utils()
        def slack = new io.stakater.notifications.Slack()
        def git = new io.stakater.vc.Git()
        def terraform = new io.stakater.automation.Terraform()

        // Slack variables
        def slackChannel = "${env.SLACK_CHANNEL}"
        def slackWebHookURL = "${env.SLACK_WEBHOOK_URL}"

        def exportKey
        def exportValue
        
        try {

          stage('Validate') {
            terraform.installDefaultThirdPartyProviders()

            if (module.equals("github")) {
              exportKey = "GITHUB_TOKEN"
              exportValue = "GITHUB_AUTH_TOKEN"
            } else if (module.equals("gitlab")) {
              exportKey = "GITLAB_TOKEN"
              exportValue = "GITLAB_AUTH_TOKEN"
            }

            sh """
              export ${exportKey}=\$${exportValue}
              
              terraform init
              terraform validate
            """
          }

          if(utils.isCD()) {
            stage('Plan and Apply') {
              sh """
                export ${exportKey}=\$${exportValue}
                
                terraform plan
                terraform apply -auto-approve
              """

              git.commitChanges(WORKSPACE, "Update terraform state")
            }
          }

          stage('Notify') {
            slack.sendDefaultSuccessNotification(slackWebHookURL, slackChannel, [])

            def commentMessage = "Terraform ${utils.isCD() ? "Apply" : "Validate"} successful"
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