# Stakater Pipeline Library


## Problem

We often face situations where multiple projects were using the same CI/CD pipeline workflow(optionally with minor changes). This resulted in a lot of code duplication and redundant work when trying to update a functionality. 
We would like to watch if some change happens in `ConfigMap` and/or `Secret`; then perform a rolling upgrade on relevant `DeploymentConfig`, `Deployment`, `Daemonset` and `Statefulset`

## Solution

We decided to extract out the reusable components from different Jenkinsfile into a pipeline library making it the single source of truth and using it for CI/CD workflows of all our applications.

## How to use Stakater Pipeline Library

Just add the following to the top of your Jenkinsfile

```groovy
#!/usr/bin/env groovy
@Library('github.com/stakater/stakater-pipeline-library@v2.15.1') _
```
and you can directly use any methods available in the library.

## Help

### Documentation
You can find more documentation [here](docs/)

### Have a question?
File a GitHub [issue](https://github.com/stakater/stakater-pipeline-library/issues), or send us an [email](mailto:stakater@gmail.com).

### Talk to us on Slack

Join and talk to us on Slack for discussing Stakater Pipeline Library

[![Join Slack](https://stakater.github.io/README/stakater-join-slack-btn.png)](https://stakater-slack.herokuapp.com/)

## Contributing

### Bug Reports & Feature Requests

Please use the [issue tracker](https://github.com/stakater/stakater-pipeline-library/issues) to report any bugs or file feature requests.

### Developing

PRs are welcome. In general, we follow the "fork-and-pull" Git workflow.

 1. **Fork** the repo on GitHub
 2. **Clone** the project to your own machine
 3. **Commit** changes to your own branch
 4. **Push** your work back up to your fork
 5. Submit a **Pull request** so that we can review your changes

NOTE: Be sure to merge the latest from "upstream" before making a pull request!

## Changelog

View our closed [Pull Requests](https://github.com/stakater/stakater-pipeline-library/pulls?q=is%3Apr+is%3Aclosed).

## License

Apache2 © [Stakater](http://stakater.com)

## About

`Stakater Pipeline Library` is maintained by [Stakater][website]. Like it? Please let us know at <hello@stakater.com>

See [our other projects][community]
or contact us in case of professional services and queries on <hello@stakater.com>

  [website]: http://stakater.com/
  [community]: https://github.com/stakater/

## Acknowledgements

- [fabric8-pipeline-library](https://github.com/fabric8io/fabric8-pipeline-library); We got the motivation to write our own pipeline library from fabric8's pipeline library. Initially we started by improving the existing library but felt that it needed a major revamp to cater better to our needs so we decided to write our own version of pipeline library.
