#!/usr/bin/groovy
//execute make target

def call(body) {
    Map config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    String[] methodParameters = config.requiredParams
    
    def app = new io.stakater.app.App()
    config = app.configure(config)
    timestamps {
        minimalToolsNode(toolsImage: config.image) {
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm
                
                def appConfig = new io.stakater.app.AppConfig()
                Map notificationConfig = appConfig.getNotificationConfig(config)
                Map gitConfig = appConfig.getGitConfig(config)
                def notificationManager = new io.stakater.notifications.NotificationManager()
                def aws = new io.stakater.cloud.Amazon()
                container(name: 'tools') {
                    try {
                        stage('run') {
                            ArrayList<String> parameters = new ArrayList<String>()
                                config.keySet().each { key ->
                                    if ((key in methodParameters)) {
                                        parameters.add("$key=${config[key]}")
                                    }
                            }
                            sh "make ${config.target} ${parameters.join(" ")}"
                            
                            if (config.pushToS3) { 
                                withAWS(credentials: 'aws-credentials', region: "eu-west-1") {
                                    s3Upload(file: config.BACKUP_NAME, bucket: config.S3_BUCKET_NAME)
                                }
                            }
                            
                            notificationManager.sendSuccess(notificationConfig, gitConfig, "Tests have been passed!", repoBranch)
                        }
                    }
                    catch (e) {
                        print "caught exception during build phase"
                        notificationManager.sendError(notificationConfig, gitConfig, "${env.BUILD_NUMBER}", "${env.BUILD_URL}", repoBranch, e)
                        throw e
                    }
                }
            }
        }
    }
}