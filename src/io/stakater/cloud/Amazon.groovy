#!/usr/bin/groovy
package io.stakater.cloud

// persist AWS Keys with default region
def persistAwsKeys(String accessKeyId, String secretAccessKey){
    persistAwsKeys(accessKeyId, secretAccessKey, "us-east-1")
}

// persist AWS Keys with custom region
def persistAwsKeys(String accessKeyId, String secretAccessKey, String region) {
    sh """
        cd \$HOME
        mkdir -p .aws/
        echo "[default]\naws_access_key_id = ${accessKeyId}\naws_secret_access_key = ${secretAccessKey}" > .aws/credentials
        echo "[default]\nregion = ${region}" > .aws/config
    """
}

// persist AWS Role with default ec2 credential source and default region
def persistAwsRoleWithDefaultCredSrcAndDefaultRegion(String roleArn){
    persistAwsRole(roleArn, "Ec2InstanceMetadata", "us-east-1")
}

// persist AWS Role with ec2 credential source and default region
def persistAwsRoleWithDefaultRegion(String roleArn, String credentialSource){
    persistAwsRole(roleArn, credentialSource, "us-east-1")
}

// persist AWS Role with default ec2 credential source and region
def persistAwsRoleWithDefaultCredSrc(String roleArn, String region){
    persistAwsRole(roleArn, "Ec2InstanceMetadata", region)
}

// persist AWS Role with custom region
def persistAwsRole(String roleArn, String credentialSource, String region){
    sh """
        cd \$HOME
        mkdir -p .aws/
        echo "[default]\nrole_arn = ${roleArn}\ncredential_source = ${credentialSource}" > .aws/credentials
        echo "[default]\nregion = ${region}" > .aws/config
    """
}

return this