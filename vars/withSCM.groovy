def call(body) {
    def scmConfig = scm.getUserRemoteConfigs()[0]
    
    def repoUrl = scmConfig.getUrl()
    def tokenizedUrl = repoUrl.tokenize('/')
    def repoName = tokenizedUrl.last().tokenize('.').first()
    def repoOwner = tokenizedUrl.get(tokenizedUrl.size() - 2)

    if(!repoUrl.startsWith("git@")) {
        // Lets make it ssh url link
        def url = new java.net.URL(repoUrl)
        print "Host: ${url.getHost()}"
        print " Path: ${url.getPath()}"

        repoUrl = "git@${url.getHost()}:${url.getPath()}" 
    }

    def repoBranch = scmConfig.getRefspec().tokenize('/').last()

    body(repoUrl, repoName, repoOwner, repoBranch)
}