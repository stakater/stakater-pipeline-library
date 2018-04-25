def call(Map parameters = [:], body) {
    withSCM { def repoUrl, def repoName, def repoOwner, def repoBranch ->
        print "Repo URL ${repoUrl}"
        print "Repo Name ${repoName}"
        print "Repo Branch ${repoBranch}"
        print "Repo Owner ${repoOwner}"

        def gitUsername = parameters.get('gitUsername', 'stakater-user')
        def gitEmail = parameters.get('gitEmail', 'stakater@gmail.com')
        
        def type = parameters.get('type', '')
        def workspaceDir = ""
        println("type: ${type}")
        switch(type.toLowerCase()) {
            // Set workspaceDir in gopath if go project
            case "go":
                def host = repoUrl.substring(repoUrl.indexOf("@") + 1, repoUrl.indexOf(":"))
                workspaceDir = "/go/src/${host}/${repoOwner}/${repoName}"
            break
            default: 
                workspaceDir = "/home/jenkins/" + repoName

        }
        
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