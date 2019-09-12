.ONESHELL:
SHELL := /bin/bash
MODULE_NAME ?= INVALID_NAME

init:
	cd $(MODULE_NAME)
	terraform init

validate: init
	cd $(MODULE_NAME)
	terraform validate

plan: validate
	cd $(MODULE_NAME)
	terraform plan

apply: plan
	cd $(MODULE_NAME)
	terraform apply -auto-approve

destroy: init
	cd $(MODULE_NAME)
	terraform destroy -auto-approve

.PHONY: validate plan apply destroy