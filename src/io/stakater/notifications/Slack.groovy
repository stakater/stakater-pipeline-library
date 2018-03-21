#!/usr/bin/groovy
package io.stakater.notifications

def sendNotification(String webhookURL, String text, String channel, def attachments) {
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

def createField(String title, String value, boolean isShort) {
    return [
        title: title,
        value: value,
        short: isShort
    ]
}

def createAttachment(String title, String titleLink, String color, String authorName, String text, def fields) {
    return [
        title: title,
        title_link: titleLink,
        color: color,
        author_name: authorName,
        text: text,
        fields: fields
    ]
}

def combineFields(def... fields) {
    return fields
}

return this