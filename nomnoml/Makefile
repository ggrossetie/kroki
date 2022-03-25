NODE_VERSION := $(shell cat ops/docker/alpine/Dockerfile | grep "FROM " | sed -En "s/FROM node:([0-9\.]+)-alpine[0-9\.]+/\1/p")
NOMNOML_VERSION := $(shell cat package.json| grep 'nomnoml":' | sed -En "s/.*: \"([0-9\.]+)\"/\1/p")
ALPINE_VERSION := $(shell cat ops/docker/alpine/Dockerfile | grep "FROM " | sed -En "s/FROM node:[0-9\.]+-alpine([0-9\.]+)/\1/p")

default:

package:
ifndef NODE_VERSION
		$(error NODE_VERSION is undefined)
endif
ifndef NOMNOML_VERSION
		$(error NOMNOML_VERSION is undefined)
endif
ifndef ALPINE_VERSION
		$(error ALPINE_VERSION is undefined)
endif
	docker build -f ops/docker/alpine/Dockerfile -t yuzutech/kroki-builder-nomnoml:${NOMNOML_VERSION}-node${NODE_VERSION}-alpine${ALPINE_VERSION} -t yuzutech/kroki-builder-nomnoml:${NOMNOML_VERSION}-alpine -t yuzutech/kroki-builder-nomnoml:latest .
