def call(body) {
    def scmConfig = scm.getUserRemoteConfigs()[0]
    
    def repoUrl = scmConfig.getUrl()
    def repoName = repoUrl.tokenize('/').last().tokenize('.').first()    

    if(!repoUrl.startsWith("git@")) {
        // Lets make it ssh url link
        //https://github.com/Stakater/IngressMonitorController.git
        def url = new java.net.URL(repoUrl)
        print "Host: ${url.getHost()}"
        print " Path: ${url.getPath()}"

        repoUrl = "git@${url.getHost()}:${url.getPath()}" 
    }

    def repoBranch = scmConfig.getRefspec().tokenize('/').last()

    body(repoUrl, repoName, repoBranch)
}