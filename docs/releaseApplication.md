# Release Application

`releaseApplication` is a method which can be used to release applications of type:
1. Angular
2. Node
3. Java Maven
4. Java Gradle
5. ASP .NET
6. Python

## Configuration

The releaseApplication can be used with the following parameters:

| Name            | Default Value                   | Description                       |
|-----------------|-------------------------|-----------------------------------|
| appType                | `maven`     | The type of application to be deployed e.g. `node|angular|maven|dotnet|python|gradle`|
| builderImage     | `stakater/builder-angular:7.0.7-node8.16-alpine-v0.0.1`| docker image to use for the particular `appType`.|
| goal                   | `install;run build:stage`              | build goal for the application.|
| notifySlack            | `false`                           | Should notify slack for pipeline results or not.|
| runIntegrationTest     | `false`                  | Should run Integration Tests or not.|
| gitUser                | `stakater-user`          | Git username |
| gitEmail               | `stakater@gmail.com`     | Git email address for the username.|
| usePersonalAccessToken | `false`                  | Use personal access token or not.|
| tokenCredentialID      | `""`                     | Name for the personal access token. |
| serviceAccount         | `jenkins`                | Service account to be used for k8s. |
| isEcr                  | `false`     | Enable Configurations for Amazon ECR Elastic Container Registery (Uses credentials through assigned role) |
| ecrRegion            | `us-west-1` | Region in which ECR is located (only if isEcr is true) | 
| dockerRepositoryURL    | `""`                     | Docker URL to push docker images. |
| chartRepositoryURL     | `""`                     | URL to push helm charts. |
| javaRepositoryURL      | `""` | Nexus Repository to push artifacts.|
| artifactType           | `.jar`                         | Suffix to add to the artifiact name when pushing to `javaRepositoryURL`|
| kubernetesGenerateManifests           | `false`                         | Generate & commit back kubernetes manifests|
| kubernetesPublicChartRepositoryURL           | `https://stakater.github.io/stakater-charts`                         | Helm Chart repository URL|
| kubernetesChartName           | `stakater/application`                         | Helm chart against which manifests will be generated|
| kubernetesChartVersion           | `0.0.12`                         | Helm chart version|
| kubernetesNamespace           | `default`                         | Namespace which will be used to generate k8s resources |
| useBuildah           | `false`                         | Use buildah to build and push Image |
| buildahVerifyTls           | `false`                         | Verify TLS when using buildah |


Passing "#ENVIRONMENT" in goals would dynamically replace it with prod if the branch against which release application 
was called is master, else it will replace it with stage.
For example,

`releaseApplication {
   ...
    goal = "install;run build:#ENVIRONMENT"
   ...
}`
this will run goal install;run build:prod if the branch was master and install;run build:stage in other cases.
