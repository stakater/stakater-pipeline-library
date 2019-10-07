.ONESHELL:
SHELL := /bin/bash
SITE_NAME ?= website
INFRA_TYPE ?= none
BUCKET_ID ?= "s3://www.<website>"
USER_MAIL?= "stakater@gmail.com"
USER_NAME?= "stakater-user"
BRANCH ?= ""
APP_PATH?="app"

build-app:
	cd $(APP_PATH)
	npm install
	yarn build

deploy-app: build-app
	aws s3 rm $(BUCKET_ID) --recursive --exclude "*" --include "precache-manifest.*"
	aws s3 sync $(APP_PATH)/build $(BUCKET_ID)

remove-app:
	aws s3 rm $(BUCKET_ID) --recursive

validate-infra:
	cd infra/$(SITE_NAME)/$(INFRA_TYPE)
	make validate

plan-infra:
	cd infra/$(SITE_NAME)/$(INFRA_TYPE)
	make plan

apply-infra:
	cd infra/$(SITE_NAME)/$(INFRA_TYPE)
	make apply || true
	cd ..
	$(MAKE) set-git-config  || true
	$(MAKE) git-push BRANCH=$(BRANCH)  || true
	

destroy-infra:
	cd infra
	make destroy || true
	cd ..
	$(MAKE) set-git-config  || true
	$(MAKE) git-push BRANCH=$(BRANCH) || true

destroy-all: destroy-app destroy-infra

set-git-config:
	git config --global user.email $(USER_MAIL)
	git config --global user.name $(USER_NAME)

git-push:
	git add .
	git commit -m "[skip ci] update terraform state"
	git push origin HEAD:$(BRANCH)