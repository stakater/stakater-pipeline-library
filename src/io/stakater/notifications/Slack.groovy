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

def sendFailureNotification(String webhookURL, String text, String channel, attachments) {
    for (def attachment : attachments) {
        attachment["color"] = "danger"
        attachment.fields << createStatusField("Failure")
    }

    sendNotification(webhookURL, text, channel, attachments)
}

def createField(String title, String value, boolean isShort) {
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

def createErrorField(String error) {
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