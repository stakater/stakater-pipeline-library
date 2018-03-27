#!/usr/bin/groovy
package io.stakater.test

import hudson.tasks.test.AbstractTestResultAction

@NonCPS
def getTestSummary() {
    def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def actions = currentBuild.rawBuild.getActions()
    for (action : actions) {
        print "Action: ${action}"
    }
    def summary = ""

    if (testResultAction != null) {
        total = testResultAction.getTotalCount()
        failed = testResultAction.getFailCount()
        skipped = testResultAction.getSkipCount()

        summary = "Passed: " + (total - failed - skipped)
        summary = summary + (", Failed: " + failed)
        summary = summary + (", Skipped: " + skipped)
    } else {
        summary = "No tests found"
        print summary + " in current build"
    }
    return summary
}

return this