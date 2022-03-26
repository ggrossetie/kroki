default:

packageErd:
	docker build -f build-static-erd -t kroki-builder-erd .

packageSvgbob:
	docker build -f build-static-svgbob -t kroki-builder-svgbob .

packagePikchr:
	docker build -f build-static-pikchr -t kroki-builder-pikchr .

publishErd: packageErd
	docker tag kroki-builder-erd:latest yuzutech/kroki-builder-erd:latest
	docker tag kroki-builder-erd:latest yuzutech/kroki-builder-erd:0.2.1.0
	docker push yuzutech/kroki-builder-erd:latest
	docker push yuzutech/kroki-builder-erd:0.2.1.0

publishSvgbob: packageSvgbob
	docker tag kroki-builder-svgbob:latest yuzutech/kroki-builder-svgbob:latest
	docker tag kroki-builder-svgbob:latest yuzutech/kroki-builder-svgbob:0.5.3
	docker push yuzutech/kroki-builder-svgbob:latest
	docker push yuzutech/kroki-builder-svgbob:0.5.3

publishPikchr: packagePikchr
	docker tag kroki-builder-pikchr:latest yuzutech/kroki-builder-pikchr:latest
	docker tag kroki-builder-pikchr:latest yuzutech/kroki-builder-pikchr:7269f78c4a
	docker push yuzutech/kroki-builder-pikchr:latest
	docker push yuzutech/kroki-builder-pikchr:7269f78c4a
