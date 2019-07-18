#!/usr/bin/groovy
package io.stakater.notifications

def sendError(Map notificationConfig, Map gitConfig, String buildNumber, String buildUrl, String repoBranch, def error) {
    def slack = new io.stakater.notifications.Slack()

    if (notificationConfig.notifySlack) {
        slack.sendDefaultFailureNotification(notificationConfig.slackWebHookURL, notificationConfig.slackChannel, [slack.createErrorField(e)], repoBranch)
    }

    String commentMessage = "Yikes! You better fix it before anyone else finds out! [Build ${buildNumber}](${buildUrl}) has Failed!"

    if(gitConfig.cloneUsingToken) {
        git.addCommentToPullRequest(commentMessage, gitConfig.tokenSecret)
    } else {
        git.addCommentToPullRequest(commentMessage)
    }
}

def sendSuccess(Map notificationConfig, Map gitConfig, String dockerImage, String version, String repoBranch){
    def slack = new io.stakater.notifications.Slack()

    if (notificationConfig.notifySlack) {
        slack.sendDefaultSuccessNotification(notificationConfig.slackWebHookURL, notificationConfig.slackChannel, [slack.createDockerImageField("${dockerImage}:${version}")], repoBranch)
    }

    String commentMessage = "Image is available for testing. `docker pull ${dockerImage}:${version}`"

    if(gitConfig.cloneUsingToken){
        git.addCommentToPullRequest(commentMessage, gitConfig.tokenSecret)
    } else {
        git.addCommentToPullRequest(commentMessage)
    }
}

return this