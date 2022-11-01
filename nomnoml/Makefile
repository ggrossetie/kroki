BUILDX_AVAILABLE := $(shell docker buildx inspect | grep -i -e running -e amd64 > /dev/null 2>&1; echo $$?)

.PHONY: package publish run

default:

package:
ifeq ($(BUILDX_AVAILABLE), 0)
ifeq ($(CI), "true")
CACHE_OPS = "--cache-from $CACHE_FROM --cache-to $CACHE_TO"
endif
	docker buildx build -f ops/docker/alpine/Dockerfile -t kroki-builder-nomnoml:alpine -t kroki-builder-nomnoml:latest -o type="image,push=false" $(CACHE_OPS) .
else
	docker build -f ops/docker/alpine/Dockerfile -t kroki-builder-nomnoml:alpine -t kroki-builder-nomnoml:latest .
endif
