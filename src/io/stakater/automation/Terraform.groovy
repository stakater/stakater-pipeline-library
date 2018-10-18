#!/usr/bin/groovy
package io.stakater.automation


def installDefaultThirdPartyProviders() {
  def defaultProviders = [
    "https://github.com/stakater/terraform-provider-gitlab/releases/download/v1.1.0/terraform-provider-gitlab"
  ]
  installThirdPartyProviders(defaultProviders)
}

def installThirdPartyProviders(providers) {
  def git = new io.stakater.vc.Git()
  def pluginsDir = "~/.terraform.d/plugins"
  sh """
    mkdir -p ${pluginsDir}
  """
  for (provider in providers) {
    sh """
      cd ${pluginsDir}
      curl -LO --show-error ${provider}
    """
  }
}