#!/usr/bin/groovy
package io.stakater.charts

def apply(String directory, boolean dryRun) {
    String landscaperApplyCmd = "landscaper apply --dir \${dir##*/}/ --namespace \${dir##*/}"
    
    if(dryRun) {
        landscaperApplyCmd = landscaperApplyCmd + " --dry-run"
    }

    sh """
        cd ${directory}
        for dir in ${directory}/*;
        do
            if [[ -d \$dir ]]; then
                dir=\${dir%*/}
                ${landscaperApplyCmd}
            fi
        done
    """
}

return this