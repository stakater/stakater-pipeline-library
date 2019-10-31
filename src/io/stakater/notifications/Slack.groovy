#!/usr/bin/groovy
package io.stakater.notifications

import groovy.json.JsonOutput

def sendNotification(String webhookURL, String text, String channel, attachments) {
    def slackURL = webhookURL
    def jenkinsIcon = 'https://wiki.jenkins-ci.org/download/attachments/2916393/logo.png'

    def payload = JsonOutput.toJson([text: text,
        channel: channel,
        username: "Jenkins",
        icon_url: jenkinsIcon,
        attachments: attachments
    ])

    sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
}

def sendSuccessNotification(String webhookURL, String text, String channel, attachments) {
    for (def attachment : attachments) {
        attachment["color"] = "good"
        attachment.fields << createStatusField("Success")
    }

    sendNotification(webhookURL, text, channel, attachments)
}

def createDefaultAttachment(fields, String branchName) {
    def git = new io.stakater.vc.Git()
    // Add Branch field
    if(branchName == null) {
        branchName = env.BRANCH_NAME
    }
    
    fields << createBranchField(branchName)

    def attachment = createAttachment(
        "${env.JOB_NAME}, build #${env.BUILD_NUMBER}",
        env.BUILD_URL,
        git.getGitAuthor(),
        "",
        fields
    )
    return attachment
}

def createDefaultAttachment(fields) {
    createDefaultAttachment(fields, null)
}

def sendDefaultFailureNotification(String webhookURL, String channel, fields) {
    sendDefaultFailureNotification(webhookURL, channel, fields, null)
}

def sendDefaultFailureNotification(String webhookURL, String channel, fields, String branchName) {
    def attachment = createDefaultAttachment(fields, branchName)
    sendFailureNotification(webhookURL, "", channel, [attachment])
}

def sendDefaultSuccessNotification(String webhookURL, String channel, String branchName) {
    sendDefaultSuccessNotification(webhookURL, channel, [], branchName)
}

def sendDefaultSuccessNotification(String webhookURL, String channel, fields) {
    sendDefaultSuccessNotification(webhookURL, channel, fields, null)
}

def sendDefaultSuccessNotification(String webhookURL, String channel, fields, String branchName) {
    def attachment = null
    if (fields != null) {
        attachment = createDefaultAttachment(fields, branchName)
    } else {
        attachment = createDefaultAttachment(branchName)
    }
    sendSuccessNotification(webhookURL, "", channel, [attachment])
}

def sendFailureNotification(String webhookURL, String text, String channel, attachments) {
    for (def attachment : attachments) {
        attachment["color"] = "danger"
        attachment.fields << createStatusField("Failure")
    }

    sendNotification(webhookURL, text, channel, attachments)
}

def createField(String title, value, boolean isShort) {
    return [
        title: "${title}",
        value: "${value}",
        short: isShort
    ]
}

def createBranchField(String branchName) {
    return createField("Branch", branchName, true)
}

def createLastCommitField(String lastCommit) {
    return createField("Last Commit", lastCommit, false)
}

def createErrorField(error) {
    return createField("Error", error, false)
}

def createTestResultsField(String testSummary) {
    return createField("Test Results", testSummary, true)
}

def createStatusField(String status) {
    return createField("Status", status, false)
}

def createDockerImageField(String image) {
    return createField("Docker Image", image, false)
}

def createArtifactField(String artifact) {
    return createField("Artifact Name", artifact, false)
}

def createAttachmentWithColor(String title, String titleLink, String color, String authorName, String text, fields) {
    return [
        title: "${title}",
        title_link: "${titleLink}",
        color: "${color}",
        author_name: "${authorName}",
        text: "${text}",
        fields: fields
    ]
}

def createAttachment(String title, String titleLink, String authorName, String text, fields) {
    return [
        title: "${title}",
        title_link: "${titleLink}",
        author_name: "${authorName}",
        text: "${text}",
        fields: fields
    ]
}

return this