#!/usr/bin/groovy
package io.stakater.cloud

def persistAwsKeys(String accessKeyId, String secretAccessKey, String region) {
    sh """
        cd \$HOME
        mkdir -p .aws/
        echo "[default]\naws_access_key_id = ${accessKeyId}\naws_secret_access_key = ${secretAccessKey}" > .aws/credentials
        echo "[default]\nregion = ${region}" > .aws/config
    """
}

return this