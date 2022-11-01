BUILDX_AVAILABLE := $(shell docker buildx inspect | grep -i -e running -e amd64 > /dev/null 2>&1; echo $$?)

.PHONY: package publish run

default:

package:
ifeq ($(BUILDX_AVAILABLE), 0)
ifeq ($(CI), "true")
CACHE_OPS = "--cache-from $CACHE_FROM --cache-to $CACHE_TO"
endif
	docker buildx build -f build-static-erd -t kroki-builder-static-erd $(CACHE_OPS) .
	docker buildx build -f build-static-svgbob -t kroki-builder-static-svgbob $(CACHE_OPS) .
	docker buildx build -f build-static-pikchr -t kroki-builder-static-pikchr $(CACHE_OPS) .
else
	docker build -f build-static-erd -t kroki-builder-static-erd .
	docker build -f build-static-svgbob -t kroki-builder-static-svgbob .
	docker build -f build-static-pikchr -t kroki-builder-static-pikchr .
endif
