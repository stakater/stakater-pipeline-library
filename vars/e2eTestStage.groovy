import groovy.json.JsonOutput

def call(config) {
    stage("System test") {
        def testJob = build job: "e2e-test", parameters: [[$class: 'StringParameterValue', name: 'config', value: JsonOutput.toJson(config) ]], propagate:false

        node {
            String text = "<h2>Regression test</h2><a href=\"${testJob.getAbsoluteUrl()}\">${testJob.getProjectName()} ${testJob.getDisplayName()} - ${testJob.getResult()}</a>"
            rtp(nullAction: '1', parserName: 'HTML', stableText: text, abortedAsStable: true, failedAsStable: true, unstableAsStable: true)
        }

        if( testJob.getResult() != "SUCCESS" ) {
            error "System test failed"
        }
    }
}