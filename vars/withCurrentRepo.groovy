def call(Map parameters = [:], body) {
    withSCM { def repoUrl, def repoName, def repoBranch ->
        print "Repo URL ${repoUrl}"
        print "Repo Name ${repoName}"
        print "Repo Branch ${repoBranch}"
        print "Repo Owner ${repoOwner}"

        def gitUsername = parameters.get('gitUsername', 'stakater-user')
        def gitEmail = parameters.get('gitEmail', 'stakater@gmail.com')

        def workspaceDir = "/home/jenkins/" + repoName

        sh "mkdir -p ${workspaceDir}"

        def git = new io.stakater.vc.Git()
                
        git.setUserInfo(gitUsername, gitEmail)
        git.addHostsToKnownHosts()
        git.checkoutRepo(repoUrl, repoBranch, workspaceDir)

        ws(workspaceDir) {
            body(repoUrl, repoName, repoOwner, repoBranch)
        }
    }
}