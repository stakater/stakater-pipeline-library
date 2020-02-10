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

def generateManifestsUsingValues(String chartRepoUrl, String chartName, String chartVersion, String namespace,
                                 String deploymentsDir, String appName){
    sh """
        helm init --client-only
        helm repo add stakater ${chartRepoUrl}
        helm repo update
        helm fetch --untar ${chartName} --version=${chartVersion}
        for valueFile in ${deploymentsDir}/*.yaml; do
            manifestsDir="${deploymentsDir}/manifests"
            manifestsFileName=\${valueFile##*/}
            manifestsFileNameWithoutExtension=\${manifestsFileName%.*}
            if [ ! "\${valueFile##*/}" == "values.yaml" ]; then manifestsDir="\${manifestsDir}-\${manifestsFileNameWithoutExtension}" ; fi
            mkdir -p \${manifestsDir}
            chartName=${chartName}
            chartDir=\${chartName##*/}
            echo "chart dir: \${chartDir} - chart name: \${chartName}" 
            helm template -f \${valueFile} --namespace ${namespace} --output-dir './\${manifestsDir}' './\${chartDir}'
            helm template \${chartDir} -f \${valueFile} --namespace ${namespace} > \${manifestsDir}/\${chartDir}/${appName}.yaml
            rm -rf \${chartDir}
        done
    """
}