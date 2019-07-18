# Stakater Node

Stakater Node is a configurable node to run jenkins pipelines. It supports a fully configurable pod template.

## Configuration

The stakater node can be configured using the following parameters:

| Name            | Value                   | Description                       |
|-----------------|-------------------------|-----------------------------------|
| label           | `stakater-node`         | Label to be assigned to pod template and node.|
| serviceAccount  | `jenkins`               | Name of Service account to be used.|
| inheritFrom     | `base`                  | The base template to inherit from|
| podAnnotations  | `[:]`                   | Annotations to be added to pod. See [PodAnnotationSpec](#pod-annotations-spec)|
| podEnvVars      | `[:]`                   | Environment Varibles to be added to pod. See [PodEnvVarsSpec](#pod-environment-variables-spec)|
| podVolumes      | `[:]`                   | Volumes to be mounted by pod. See [PodVolumesSpec](#pod-volumes-spec)|
| podContainers   | `[:]`                   | Containers to be added to pod. See [PodContainersSpec](#pod-containers-spec)|

## Pod Annotations Spec

Annotations can be added to pod using the following parameters:

| Name                  | Value                   | Description                       |
|-----------------------|-------------------------|-----------------------------------|
| isCritical            | `true`                  | Sets `scheduler.alpha.kubernetes.io/critical-pod` to `true`|
| additionalAnnotations | `[[:]]`   | Additional Annotations to add. Expects [PodAnnotationSpec[]](#pod-annotation-spec)|

## Pod Annotation Spec

Additional pod annotations apart from configured annotations can be added to pod annotations using the following parameters:

| Name                  | Value                   | Description                       |
|-----------------------|-------------------------|-----------------------------------|
| key                   | `nil`                   | Key of pod annotation             |
| value                 | `nil`                   | Value of pod annotation           |

## Pod Environment Variables Spec

Environment variables can be added to pod using the following parameters:

| Name                  | Value                   | Description                       |
|-----------------------|-------------------------|-----------------------------------|
| isChartMuseum         | `false`                 | Adds two secret env variables to pod for chart museum i.e `secretEnvVar(key: 'CHARTMUSEUM_USERNAME', secretName: 'chartmuseum-auth', secretKey: 'username')` and `secretEnvVar(key: 'CHARTMUSEUM_PASSWORD', secretName: 'chartmuseum-auth', secretKey: 'password')` |
| isNotifySlack         | `false`                 | Adds two secret env variables to pod for slack i.e `secretEnvVar(key: 'SLACK_CHANNEL', secretName: 'slack-notification-hook', secretKey: 'channel')` and `secretEnvVar(key: 'SLACK_WEBHOOK_URL', secretName: 'slack-notification-hook', secretKey: 'webHookURL')`|
| isGithubToken         | `false`                 | Adds two secret env variables to pod for github i.e `secretEnvVar(key: 'GITHUB_AUTH_TOKEN', secretName: 'jenkins-hub-api-token', secretKey: 'hub')`|
| isGitlabToken         | `false`                 | Adds two secret env variables to pod for gitlab i.e `secretEnvVar(key: 'GITLAB_AUTH_TOKEN', secretName: 'jenkins-hub-api-token', secretKey: 'gitlab.hub')`
| additionalEnvVars     | `[[:]]`                   | Additional Environment Variables to add. Expects [PodEnvVarSpec[]](#pod-environment-variable-spec)|
| additionalSecretEnvVars | `[[:]]`             | Additional Environment Variables to add. Expects [PodSecretEnvVarSpec[]](#pod-secret-environment-variable-spec)|

## Pod Environment Variable Spec

A pod environment variable can be added using the following parameters:

| Name                  | Value                   | Description                       |
|-----------------------|-------------------------|-----------------------------------|
| key                   | `nil`                   | Key of Envionment variable        |
| value                 | `nil`                   | Value of Envionment variable      |

## Pod Secret Environment Variable Spec

A pod secret environment variable can be added using the following parameters:

| Name                  | Value                   | Description                        |
|-----------------------|-------------------------|------------------------------------|
| key                   | `nil`                   | Key of Secret Envionment variable  |
| value                 | `nil`                   | Value of SecretEnvionment variable |

## Pod Volumes Spec

Volumes can be mounted on pod using the following parameters:

| Name                      | Value         | Description                        |
|---------------------------|---------------|------------------------------------|
| isMaven                   | `false`       | Adds a secret volume with name `jenkins-maven-settings` and mount path `/root/.m2`. |
| isMavenLocalRepo          | `false`       | Adds a persistent volume claim with name `jenkins-mvn-local-repo` and mount path `/root/.mvnrepository`.|
| isDockerConfig            | `false`       | Adds a secret volume with name `jenkins-docker-cfg` and mount path `/home/jenkins/.docker`. |
| isDockerMount             | `false`       | Adds a host path volume with host path `/var/run/docker.sock` and mount path `/var/run/docker.sock`. |
| isGitSsh                  | `false`       | Adds a secret volume with name `jenkins-git-ssh` and mount path `/root/.ssh-git`. |
| isHubApiToken             | `false`       | Adds a secret volume with name `jenkins-hub-api-token` and mount path `/home/jenkins/.apitoken`. |
| isStkConfig               | `false`       | Adds a secret volume with name `stk-config` and mount path `/home/jenkins/.stk`. |
| isHelmPgpKey              | `false`       | Adds a secret volume with name `helm-pgp-key` and mount path `/usr/local/bin/pgp-configuration/`. |
| additionalSecretVolumes   | `[:]`         | Manages additional secret volumes. Expects [PodSecretVolumeSpec[]](#pod-secret-volume-spec) |
| additionalHostPathVolumes | `[:]`         | Manages additional host volumes. Expects [PodHostPathVolumeSpec[]](#pod-host-path-volume-spec) |
| additionalPVCs            | `[:]`         | Manages additional secret volumes. Expects [PodPersistentVolumeClaimSpec[]](#pod-persistent-volume-claim-spec) |

## Pod Secret Volume Spec

A pod secret volume can be defined using following parameters

| Name                      | Value         | Description                        |
|---------------------------|---------------|------------------------------------|
| secretName                | `nil`         | Name of secret volume              |
| mountPath                 | `nil`         | Mount path of secret volume        |

## Pod Host Path Volume Spec

A pod host path volume can be defined using following parameters

| Name                      | Value         | Description                        |
|---------------------------|---------------|------------------------------------|
| hostPath                  | `nil`         | Host path of volume                |
| mountPath                 | `nil`         | Mount path of volume               |

## Pod Persistent Volume Claim Spec

A pod persistent volume claim can be defined using following parameters

| Name                      | Value         | Description                           |
|---------------------------|---------------|---------------------------------------|
| claimName                 | `nil`         | Name of persistent volume claim       |
| mountPath                 | `nil`         | Mount path of persistent volume claim |

## Pod Containers Spec

Containers running on pods can be configured using following parameters

| Name                        | Value       | Description                        |
|-----------------------------|-------------|------------------------------------|
| enableDefaultContainer      | `true`      | Enables default container          |
| defaultContainer.name       | `tools`     | Name of default container          |
| defaultContainer.image      | `stakater/pipeline-tools:v2.0.5` | Image of default container |
| defaultContainer.command    | `/bin/sh -c` | Command of default container      |
| defaultContainer.args       | `cat`       | Args of default container          |
| defaultContainer.privileged | `true`      | Name of default container          |
| defaultContainer.workingDir | `/home/jenkins/` | Working directory of default container |
| defaultContainer.ttyEnabled | `true`      | Enable TTY of default container    |
| defaultContainer.envVarsConfig.isDocker    | `false` | Adds 2 env vars. One with name `DOCKER_CONFIG` and value `/home/jenkins/.docker/` and the other with name `DOCKER_API_VERSION` and value `1.32` |
| defaultContainer.envVarsConfig.isKubernetes | `false` | Adds env var with name `KUBERNETES_MASTER` and value `https://kubernetes.default:443` |
| defaultContainer.envVarsConfig.isMaven      | `false` | Adds env var with name `MAVEN_OPTS` and value `-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn` |
| defaultContainer.envVarsConfig.extraEnvVars | `[[:]]` | Adds a list of extra env variables defined by `key` and `value`. |
| additionalContainers        | `[]`    | Adds additional containers to pod. Expects [PodAdditionalContainerSpec[](#pod-additional-container-spec) |

## Pod Additional Container Spec

A pod container can be defined using following parameters

| Name          | Value                          | Description                      |
|---------------|--------------------------------|----------------------------------|
| name          | `tools`                        | Name of container                |
| image         | `stakater/pipeline-tools:v2.0.5`| Image of container               |
| command       | `/bin/sh -c`                   | Command of container             |
| args          | `cat`                          | Args of container                |
| privileged    | `true`                         | Name of container                |
| workingDir    | `/home/jenkins/`               | Working directory of container   |
| ttyEnabled    | `true`                         | Enable TTY of container          |
| envVars       | `[]`                           | List of env vars of container    |