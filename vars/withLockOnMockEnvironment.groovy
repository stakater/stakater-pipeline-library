#!/usr/bin/groovy

import groovy.json.JsonOutput

def call(Map parameters = [:], body) {
    int defaultLifetimeInSeconds = 25 * 60
    int defaultWaitInSeconds = 5
    URL url = new URL("http://restful-distributed-lock-manager.release:8080/locks/mock")

    String lockName = parameters.get('lockName')
    String lockJson = JsonOutput.toJson(
            [
                title: lockName,
                lifetime: defaultLifetimeInSeconds,
                wait: defaultWaitInSeconds
            ])

    URL lockUrl = null
    stage('Aquire lock on mock') {
        while (lockUrl == null) {
            echo "Waiting for lock"
            lockUrl = acquireLock(url, lockJson)
        }
    }

    try {
        body()
    } finally {
        releaseLock(lockUrl)
    }

}

private URL acquireLock(URL url, String lockBody) {

    def connection = url.openConnection()
    connection.setDoOutput(true)
    def writer = new OutputStreamWriter(connection.getOutputStream())
    writer.write(lockBody)
    writer.flush()
    writer.close()

    URL lockUrl = null;
    def responseCode = connection.getResponseCode()
    if (responseCode == 201) {
        lockUrl = new URL(connection.getHeaderField("Location"))
        echo "Acquired ${lockUrl}"
    } else {
        echo "Did not get a lock"
        if (responseCode != 408) {
            echo "Something went wrong when locking: ${responseCode}"
        }
    }

    return lockUrl;
}

private void releaseLock(URL lockUrl) {
    echo "Releasing ${lockUrl}"
    def conn = lockUrl.openConnection()
    conn.setRequestMethod("DELETE")

    def responseCode = conn.getResponseCode()
    if (responseCode != 204) {
        echo "Something went wrong when releaseing the lock: ${responseCode}"
    }
}