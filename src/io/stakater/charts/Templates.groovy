
def renderChart(String chartTemplatesDir, String chartPath, String version, String dockerImage){
    sh """
        export VERSION=${version}
        export DOCKER_IMAGE=${dockerImage}
        for template in ${chartTemplatesDir}/*.yaml.tmpl; do 
            # Remove .tmpl suffix
            file=\${template%.tmpl}
            # Remove full path
            file=\${file##*/}
            # Append new path
            file="${chartPath}/\${file}"
            # Render template
            gotplenv \${template} > \${file}
        done
    """
}

def generateManifests(String chartPath, String manifestsDir){
    sh """
        mkdir -p ${manifestsDir}
        templatesDir="templates"
        for templateName in ${chartPath}/\${templatesDir}/*.yaml; do
            # Remove full path
            templateName=\${templateName##*/}
            helm template ${chartPath} -x \${templatesDir}/\${templateName} > ${manifestsDir}/\${templateName}
        done
    """
}