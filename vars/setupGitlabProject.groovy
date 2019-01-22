#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    toolsNode(toolsImage: "stakater/pipeline-tools:1.13.2") {
        container(name: "tools") {
            withCurrentRepo(gitUsername: config.gitUsername, gitEmail: config.gitEmail) { def repoUrl, def repoName, def repoOwner, def repoBranch ->
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

                    sh """
                        export GITLAB_TOKEN=\$GITLAB_AUTH_TOKEN
                        
                        terraform init
                        terraform validate
                    """
                    }

                    if(utils.isCD()) {
                    stage('Plan and Apply') {
                        sh """
                        export GITLAB_TOKEN=\$GITLAB_AUTH_TOKEN
                        
                        terraform plan
                        terraform apply -auto-approve
                        """

                        git.commitChanges(WORKSPACE, "Update terraform state")
                    }

                    stage('Create Jenkins Jobs') {
                        // Create carbook folder
                        jobDsl scriptText: """
                        folder("${config.jobFolderName}") {
                            displayName("${config.jobFolderDisplayName}")
                            description("${config.jobFolderDescription}")
                        }
                        """

                        files = findFiles(glob: "${config.tfFilesPrefix}*.tf")

                        for(int ii = 0; ii < files.size(); ii++) {
                        projectName = (files[ii].name.split("${config.tfFilesPrefix}")[1]).split('\\.')[0]
                        
                        echo "Project Name: ${projectName}"
                        // Create pipeline job for current project for automatic triggering
                        jobDsl scriptText: """
                            pipelineJob("${config.jobFolderName}/${projectName}") {
                            definition {
                                cpsScm {
                                scm {
                                    git {
                                    branch('*/\044{gitlabBranch}')
                                    remote {
                                        name("origin")
                                        url("https://gitlab.com/${config.jobFolderName}/${projectName}.git")
                                        credentials("${config.gitUserName}")
                                        refspec("+refs/heads/*:refs/remotes/origin/* +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*")
                                    }
                                    }
                                }
                                }
                            }
                            triggers {
                                gitlab {
                                triggerOnMergeRequest(true)
                                triggerOnAcceptedMergeRequest(true)
                                triggerOnClosedMergeRequest(false)
                                triggerOnPush(false)
                                skipWorkInProgressMergeRequest(true)
                                }
                            }
                            }
                        """
                        // Create pipeline job for current project for manual triggering
                        jobDsl scriptText: """
                            multibranchPipelineJob("${config.jobFolderName}/${projectName}-manual") {
                            branchSources {
                                git {
                                id("${projectName}-manual")
                                remote("https://gitlab.com/${config.jobFolderName}/${projectName}.git")
                                credentialsId("${config.gitUserName}")
                                includes("*")
                                }
                            }
                            }
                        """
                        // Create pipeline job for current project for triggering master
                        jobDsl scriptText: """
                            pipelineJob("${config.jobFolderName}/${projectName}-master") {
                            definition {
                                cpsScm {
                                scm {
                                    git {
                                    branch('master')
                                    remote {
                                        name("origin")
                                        url("https://gitlab.com/${config.jobFolderName}/${projectName}.git")
                                        credentials("${config.gitUserName}")
                                        refspec("+refs/heads/*:refs/remotes/origin/*")
                                    }
                                    }
                                }
                                }
                            }
                            triggers {
                                gitlab {
                                triggerOnMergeRequest(false)
                                triggerOnAcceptedMergeRequest(false)
                                triggerOnClosedMergeRequest(false)
                                triggerOnPush(true)
                                branchFilterType("NameBasedFilter")
                                includeBranchesSpec("master")
                                }
                            }
                            }
                        """
                        }
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