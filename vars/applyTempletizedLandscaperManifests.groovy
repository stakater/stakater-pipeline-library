#!/usr/bin/groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def helmRepoName = config.helmRepoName ?: 'chartmuseum '
    def helmRepoUrl = config.helmRepoUrl ?: 'http://chartmuseum'

    def utils = new io.fabric8.Utils()

    properties([
        disableConcurrentBuilds(),
        parameters([
            string(name: 'AppName', defaultValue: ''),
            string(name: 'AppVersion', defaultValue: ''),
            string(name: 'Namespace', defaultValue: 'dev')
        ])
    ])

    toolsWithCurrentKubeNode(toolsImage: 'stakater/pipeline-tools:1.8.1') {
        container(name: 'tools') {
            withCurrentRepo { def repoUrl, def repoName, def repoOwner, def repoBranch ->

                def templatesDir = WORKSPACE + "/templates"
                def outputDir = WORKSPACE + "/output"
                def versionsDir = WORKSPACE + "/versions"

                def git = new io.stakater.vc.Git()
                def helm = new io.stakater.charts.Helm()
                def landscaper = new io.stakater.charts.Landscaper()
                def common = new io.stakater.Common()

                stage('Update App Version') {
                    if(params.AppName != '' && params.AppVersion != '') {
                        sh """
                            cd versions/${params.Namespace}
                            echo "export VERSION=${params.AppVersion}" > ${params.AppName}
                        """
                    }
                }

                stage('Generate Manifests') {
                    sh """
                        cd ${templatesDir}
                        for nsDir in *
                        do
                            mkdir -p ${outputDir}/\$nsDir
                            for app in \$nsDir/*
                            do
                                template=\${app##*/}
                                app=\${template%.*.*}
                                if [ -f ${versionsDir}/\$nsDir/\$app ]; then
                                    source ${versionsDir}/\$nsDir/\$app
                                fi
                                gotplenv ${templatesDir}/\$nsDir/\$template > ${outputDir}/\$nsDir/\$app.yaml
                            done
                        done
                    """
                }

                stage('Init Helm') {
                    // Sleep is needed for the first time because tiller pod might not be ready instantly
                    helm.init(false)

                    sh "sleep 30s"

                    helm.addRepo("chartmuseum", "http://chartmuseum")
                }

                stage('Dry Run Charts') {
                    landscaper.apply(outputDir, true)
                }

                if(utils.isCD()) {
                    stage('Install Charts') {
                        landscaper.apply(outputDir, false)
                    }

                    def versionFile = ".version"
                    git.tagAndRelease(versionFile, repoName, repoOwner)
                }
            }
        }
    }
}
