.ONESHELL:
SHELL := /bin/bash
MODULE_NAMES ?= INVALID_NAME

init:
	for module in $(MODULE_NAMES); do \
		cd $$module; \
		terraform init \
		cd ..
	done

validate: init
	for module in $(MODULE_NAMES); do \
		cd $$module; \
		terraform validate \
		cd ..
	done

plan: validate
	for module in $(MODULE_NAMES); do \
		cd $$module; \
		terraform plan \
		cd ..
	done
	

apply: plan
	for module in $(MODULE_NAMES); do \
		cd $$module; \
		terraform apply -auto-approve \
		cd ..
	done
	cd $(MODULE_NAME)
	

destroy: init
	for module in $(MODULE_NAMES); do \
		cd $$module; \
		terraform destroy -auto-approve \
		cd ..
	done
	

.PHONY: validate plan apply destroy