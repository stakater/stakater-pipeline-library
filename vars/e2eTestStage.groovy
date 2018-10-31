import groovy.json.JsonOutput

def call(Map parameters = [:], config) {
    stage("E2E test") {
        def jobName = parameters.get('jobName', 'carbook/e2e-tests-manual/master')
        def testJob = build job: jobName, parameters: [[$class: 'StringParameterValue', name: 'config', value: JsonOutput.toJson(config) ]], propagate:false

        node {
            String text = "<h2>Regression test</h2><a href=\"${testJob.getAbsoluteUrl()}\">${testJob.getProjectName()} ${testJob.getDisplayName()} - ${testJob.getResult()}</a>"
            rtp(nullAction: '1', parserName: 'HTML', stableText: text, abortedAsStable: true, failedAsStable: true, unstableAsStable: true)
        }

        if( testJob.getResult() != "SUCCESS" ) {
            error "System test failed"
        }
    }
}