#!/usr/bin/groovy
package io.stakater.charts

def renderChart(String chartTemplatesDir, String chartDir, String chartName, String version, String dockerImage){
    sh """
        export VERSION=${version}
        export DOCKER_IMAGE=${dockerImage}
        for template in ${chartTemplatesDir}/*.yaml.tmpl; do 
            # Remove .tmpl suffix
            file=\${template%.tmpl}
            # Remove full path
            file=\${file##*/}
            # Append new path
            file="${chartDir}/${chartName}/\${file}"
            # Render template
            gotplenv \${template} > \${file}
        done
    """
}

// for an additional argument when image version is different than helm version
def renderChart(String chartTemplatesDir, String chartDir, String chartName, String imageVersion, String helmVersion, String dockerImage){
    echo "Rendering chart for separate image & helm versions"
    echo "Helm version: ${helmVersion}"
    echo "Image version: ${imageVersion}"
    sh """    
        export VERSION=${helmVersion}
        export DOCKER_IMAGE=${dockerImage}
        export DOCKER_TAG=${imageVersion}
        for template in ${chartTemplatesDir}/*.yaml.tmpl; do 
            # Remove .tmpl suffix
            file=\${template%.tmpl}
            # Remove full path
            file=\${file##*/}
            # Append new path
            file="${chartDir}/${chartName}/\${file}"
            # Render template
            gotplenv \${template} > \${file}
        done
    """
}

def renderTemplate(String version, String manifestDir){
    sh """
        export VERSION=${version}
        for template in ${manifestDir}/*.yaml.tmpl; do 
            # Remove .tmpl suffix
            file=\${template%.tmpl}
            # Render template
            gotplenv \${template} > \${file}
        done
    """
}

def renderChart(String chartTemplatesDir, String chartDir, String chartName, String version) {
    sh """
        export VERSION=${version}
        for template in ${chartTemplatesDir}/*.yaml.tmpl; do 
            # Remove .tmpl suffix
            file=\${template%.tmpl}
            # Remove full path
            file=\${file##*/}
            # Append new path
            file="${chartDir}/${chartName}/\${file}"
            # Render template
            gotplenv \${template} > \${file}
        done
    """
}

def generateManifests(String chartDir, String chartName, String manifestsDir){
    sh """
        mkdir -p ${manifestsDir}
        templatesDir="templates"
        for templateName in ${chartDir}/${chartName}/\${templatesDir}/*.yaml; do
            # Remove full path
            templateName=\${templateName##*/}
            helm template --name ${chartName} --namespace default ${chartDir}/${chartName} -x \${templatesDir}/\${templateName} > ${manifestsDir}/\${templateName}
        done
    """
}

def generateManifestsUsingValues(String chartRepoUrl, String chartName, String chartVersion, String deploymentsDir,
                                 String appName){
    sh """
        helm init --client-only
        helm repo add stakater ${chartRepoUrl}
        helm repo update
        helm fetch --untar ${chartName} --version=${chartVersion}
        ls ${deploymentsDir}/
        for valueFile in ${deploymentsDir}/*.yaml; do
            echo "value file: \${valueFile}"
            manifestsDir=\${deploymentsDir}+"/manifests"
            echo "manifest file: \${manifestsDir}"
            if [ ! "\${valueFile##*/}" == "values.yaml" ]; then manifestsDir=\${manifestsDir} + "-" + \${valueFile##*/} ; fi
            echo "manifest file after If: \${manifestsDir}"
            mkdir -p \${manifestsDir}
            chartDir=\${chartName##*/}
            helm template \${chartDir} -f ${deploymentsDir}/values.yaml --namespace default > \${manifestsDir}/\${chartDir}/${appName}.yaml
            helm template -f ${deploymentsDir}/values.yaml --output-dir './\${manifestsDir}' './\${chartDir}'
            rm -rf \${chartDir}
        done
    """
}