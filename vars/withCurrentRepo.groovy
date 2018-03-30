def call(body) {
    withSCM { def repoUrl, def repoName, def repoBranch ->
        print "Repo URL ${repoUrl}"
        print "Repo Name ${repoName}"
        print "Repo Branch ${repoBranch}"

        body()
    }
}