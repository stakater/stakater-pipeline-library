# Release Application

`releaseApplication` is a method which can be used to release applications of type:
1. Angular
2. Node
3. Java Maven
4. Java Gradle
5. ASP .NET

## Configuration

The releaseApplication can be used with the following parameters:

| Name            | Value                   | Description                       |
|-----------------|-------------------------|-----------------------------------|
| appType                | `node|angular|maven|dotnet|gradle`     | The type of application to be deployed.|
| builderImage           | `stakater/builder-node-8:v0.0.2`       | docker image to use for the particular `appType`.|
| goal                   | `install`                              | build goal for the application.|
| notifySlack            | `true|false`                           | Should notify slack for pipeline results or not.|
| runIntegrationTest     | `true|false`                           | Should run Integration Tests or not.|
| gitUser                | `example-user`                         | Git username |
| gitEmail               | `example@gitdomain.com`                | Git email address for the username.|
| usePersonalAccessToken | `true|false`                           | Use personal access token or not.|
| tokenCredentialID      | `app-token`                            | Name for the personal access token. |
| serviceAccount         | `service-account-jenkins`              | Service account to be used for k8s. |
| dockerRepositoryURL    | `docker.domain.com:443`                | Docker URL to push docker images. |
| chartRepositoryURL     | `http://nexus.domain/repository/charts`| URL to push helm charts. 
| javaRepositoryURL      | `http://nexus.domain/repository/maven` | Nexus Repository to push artifacts.
| artifactType           | `-example.jar`                         | Suffix to add to the artifiact name when pushing to `javaRepositoryURL`