
def call(body) {
    def utils = new io.fabric8.Utils()
    echo "SCM Configs: ${scm.getUserRemoteConfigs()}"

    def repoUrl
    def repoName
    def repoBranch
    def repoOwner
    
    if(env['gitlabSourceRepoSshUrl'] != null) { // Triggered by gitlab webhook
        repoUrl = env['gitlabSourceRepoSshUrl']
        repoName = env['gitlabSourceRepoName']
        repoBranch = env['gitlabSourceBranch']
        repoOwner = env['gitlabSourceNamespace']
        echo "Trigger Phrase: ${env}"
    } else {
        def scmConfig = scm.getUserRemoteConfigs()[0]
    
        repoUrl = scmConfig.getUrl()
        def tokenizedUrl = repoUrl.tokenize('/')
        repoName = tokenizedUrl.last().split('\\.git').first()
        repoOwner = tokenizedUrl.get(tokenizedUrl.size() - 2)
        
        if(!repoUrl.startsWith("git@")) {
            // Lets make it ssh url link
            def url = new java.net.URL(repoUrl)
    
            repoUrl = "git@${url.getHost()}:${url.getPath().substring(1)}"
        }
        
        if (env.CHANGE_FORK) {
            repoUrl = repoUrl.replaceFirst(repoOwner, env.CHANGE_FORK)
        }
        
        if(env['defaultBranch'] != null) {
            repoBranch = env['defaultBranch']
        } else if(utils.getBranch().startsWith("PR-")) {
            // Fetch branch from CHANGE_AUTHOR
            repoBranch = "${env.CHANGE_BRANCH}"
        }
        else {
            //repoBranch = scmConfig.getRefspec().tokenize('/').last()
            repoBranch = utils.getBranch()
        }
    }
    
    withEnv(["REPO_URL=${repoUrl}",
             "REPO_NAME=${repoName}",
             "REPO_OWNER=${repoOwner}",
             "REPO_BRANCH=${repoBranch}"]) {
        body(repoUrl, repoName, repoOwner, repoBranch)
    }
}