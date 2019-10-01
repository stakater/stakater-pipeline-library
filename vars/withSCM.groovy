
def call(body) {
    def utils = new io.fabric8.Utils()

    def repoUrl
    def repoName
    def repoCloneBranch
    def repoOwner
    def repoBranch
    
    if(env['gitlabSourceRepoSshUrl'] != null) { // Triggered by gitlab webhook
        repoUrl = env['gitlabSourceRepoSshUrl']
        repoName = env['gitlabSourceRepoName']

        if(env['gitlabMergeRequestState'] == "merged") {
            repoCloneBranch = env['gitlabTargetBranch']
        } else {
            repoCloneBranch = env['gitlabSourceBranch']
        }

        repoBranch = repoCloneBranch

        // Override repoBranch if its MR
        if(env['gitlabMergeRequestId'] != null) {
            repoBranch = "MR-" + env['gitlabMergeRequestIid']
        }
        
        repoOwner = env['gitlabSourceNamespace']
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
            repoCloneBranch = env['defaultBranch']
        } else if(utils.getBranch().startsWith("PR-")) {
            // Fetch branch from CHANGE_AUTHOR
            repoCloneBranch = "${env.CHANGE_BRANCH}"
        }
        else {
            //repoCloneBranch = scmConfig.getRefspec().tokenize('/').last()
            repoCloneBranch = utils.getBranch()
        }
        repoBranch = utils.getBranch()
    }

    // Fixes issues while generating tags for docker images
    repoBranch = repoBranch.replace('/', '-')
    
    withEnv(["REPO_URL=${repoUrl}",
             "REPO_NAME=${repoName}",
             "REPO_OWNER=${repoOwner}",
             "REPO_CLONE_BRANCH=${repoCloneBranch}",
             "REPO_BRANCH=${repoBranch}"]) {
        body(repoUrl, repoName, repoOwner, repoBranch, repoCloneBranch)
    }
}
