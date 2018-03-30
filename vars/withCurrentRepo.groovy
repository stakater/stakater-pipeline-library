def call(Map parameters = [:], body) {
    withSCM { def repoUrl, def repoName, def repoBranch ->
        print "Repo URL ${repoUrl}"
        print "Repo Name ${repoName}"
        print "Repo Branch ${repoBranch}"

        def gitUsername = parameters.get('gitUsername', 'stakater-user')
        def gitEmail = parameters.get('gitEmail', 'stakater@gmail.com')

        def workspaceDir = pwd() + repoName

        ws(workspaceDir) {
            def git = new io.stakater.vc.Git()
            
            git.setUserInfo(gitUsername, gitEmail)
            git.addHostsToKnownHosts()
            git.checkoutRepo(repoUrl, repoBranch, workspaceDir)
            body()
        }
    }
}