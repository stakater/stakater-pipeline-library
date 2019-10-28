#!/usr/bin/groovy
//execute make target

def call(body) {
    Map config = [:]
    String[] methodParameters = ["target"]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def app = new io.stakater.app.App()
    config = app.configure(config)
    timestamps {
        toolsNode(toolsImage: 'stakater/builder-tool:terraform-0.11.11-v0.0.13') {
            withSCM { String repoUrl, String repoName, String repoOwner, String repoBranch ->
                checkout scm

                def appConfig = new io.stakater.app.AppConfig()
                Map notificationConfig = appConfig.getNotificationConfig(config)
                Map gitConfig = appConfig.getGitConfig(config)


                def notificationManager = new io.stakater.notifications.NotificationManager()



                container(name: 'tools') {
                    try {
                        stage('Create Version') {
                            print "aaaaaaaaaaaaaaaaaaaaaaa"
                            print config
                            config["notifySlack"] = true
                            print config
                            print "bbbbbbbbbbbbbbbbbbbbbbb"
                            print notificationConfig
                            sh "ls -al"
                            sh "make non-master"
                        }
                    }
                    catch (e) {
                        
                        print "caught exception during build phase"
                        print e
                        buildException = e
                        notificationManager.sendError(notificationConfig, gitConfig, "${env.BUILD_NUMBER}", "${env.BUILD_URL}", repoBranch, e)
                    }
                }
    }
    



    // config.target = config.target ? config.target : "install-dry-run"

    // timestamps {
    //     ArrayList<String> parameters = new ArrayList<String>()
    //     config.keySet().each { key ->
    //         if (! (key in methodParameters)) {
    //             parameters.add("$key=${config[key]}")
    //         }
    //     }
    //     sh "make ${config.target} ${parameters.join(" ")}"
    // }
}}}