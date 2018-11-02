import groovy.json.JsonOutput

def call(Map parameters = [:], config) {
    def appName = parameters.get('appName', '')
    def e2eJobName = parameters.get('e2eJobName', '')
    def performanceTestJobName = parameters.get('performanceTestJobName', '')
    def chartName = parameters.get('chartName', '')
    def chartVersion = parameters.get('chartVersion', '')
    def repoUrl = parameters.get('repoUrl', '')
    def repoBranch = parameters.get('repoBranch', '')
    def testJob = build job: e2eJobName, parameters: [ [$class: 'StringParameterValue', name: 'chartName', value: chartName ], [$class: 'StringParameterValue', name: 'chartVersion', value: chartVersion ], [$class: 'StringParameterValue', name: 'repoUrl', value: repoUrl ], [$class: 'StringParameterValue', name: 'repoBranch', value: repoBranch ],  [$class: 'StringParameterValue', name: 'performanceTestJobName', value: performanceTestJobName ],  [$class: 'StringParameterValue', name: 'appName', value: appName ], [$class: 'StringParameterValue', name: 'config', value: JsonOutput.toJson(config) ]  ], propagate:false

    node {
        String text = "<h2>Regression test</h2><a href=\"${testJob.getAbsoluteUrl()}\">${testJob.getProjectName()} ${testJob.getDisplayName()} - ${testJob.getResult()}</a>"
        rtp(nullAction: '1', parserName: 'HTML', stableText: text, abortedAsStable: true, failedAsStable: true, unstableAsStable: true)
    }

    if( testJob.getResult() != "SUCCESS" ) {
        error "System test failed"
    }    
}