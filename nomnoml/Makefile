default:

package:
	docker build -f ops/docker/alpine/Dockerfile -t kroki-builder-nomnoml .

publish: package
	docker tag kroki-builder-nomnoml:latest yuzutech/kroki-builder-nomnoml:latest
	docker tag kroki-builder-nomnoml:latest yuzutech/kroki-builder-nomnoml:1.4.0-alpine
	docker tag kroki-builder-nomnoml:latest yuzutech/kroki-builder-nomnoml:1.4.0-node14-alpine3.15
	docker tag kroki-builder-nomnoml:latest yuzutech/kroki-builder-nomnoml:1.4.0-node14.19.1-alpine3.15
	docker push yuzutech/kroki-builder-nomnoml:latest
	docker push yuzutech/kroki-builder-nomnoml:1.4.0-alpine
	docker push yuzutech/kroki-builder-nomnoml:1.4.0-node14-alpine3.15
	docker push yuzutech/kroki-builder-nomnoml:1.4.0-node14.19.1-alpine3.15
