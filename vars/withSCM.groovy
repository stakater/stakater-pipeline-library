def call(body) {
    def scmConfig = scm.getUserRemoteConfigs()[0]
    
    def repoUrl = scmConfig.getUrl()
    def repoName = repoUrl.tokenize('/').last().tokenize('.').first()    
    def repoBranch = scmConfig.getRefspec().tokenize('/').last()

    body(repoUrl, repoName, repoBranch)
}