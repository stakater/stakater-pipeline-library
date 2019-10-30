#!/usr/bin/groovy
//execute make target

def call(body) {
    def env = System.getenv()
    Map config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    String[] methodParameters = config.requiredParams
    
    def app = new io.stakater.app.App()
    config = app.configure(config)
    timestamps {
        stakaterNode(config) {
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm
                
                def appConfig = new io.stakater.app.AppConfig()
                Map notificationConfig = appConfig.getNotificationConfig(config)
                Map gitConfig = appConfig.getGitConfig(config)
                def notificationManager = new io.stakater.notifications.NotificationManager()
                
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
                            sh "echo KEYID: \$AWS_ACCESS_KEY_ID \n  SECRET KEY: \$AWS_SECRET_ACCESS_KEY"
                            // withAWS(credentials:'aws-credentials', region: 'eu-west-1') {
                            //     s3Upload(file:'file.txt', bucket:'cypress-test-bucket', path:'path/to/target/file.txt')
                            // }
                        }
                    }
                    catch (e) {
                        print "caught exception during build phase"
                        print e
                        notificationManager.sendError(notificationConfig, gitConfig, "${env.BUILD_NUMBER}", "${env.BUILD_URL}", repoBranch, e)
                    }
                }
            }
        }
    }
}