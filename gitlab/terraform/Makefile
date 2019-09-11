.ONESHELL:
SHELL := /bin/bash
PLUGIN_DIR ?= ~/.terraform.d/plugins
PROVIDERS ?= https://github.com/stakater/terraform-provider-gitlab/releases/download/v1.1.0/terraform-provider-gitlab

get_third_party:
	mkdir -p $(PLUGIN_DIR)
	cd $(PLUGIN_DIR)
	for provider in $(PROVIDERS); do \
		curl -LO --show-error $$provider; \
	done
	chmod -R +x .

init: get_third_party
	terraform init

validate: init
	terraform validate

plan: validate
	terraform plan

apply: plan
	terraform apply -auto-approve

.PHONY: validate plan apply