# syntax=docker/dockerfile:1
# build static executable binary
FROM ubuntu:18.04 as kroki-builder-static-erd

RUN apt-get -qq update && apt-get install -qq -y graphviz curl git > /dev/null

RUN curl -sSL https://get.haskellstack.org/ | sh -s - -q > /dev/null

RUN git clone https://github.com/BurntSushi/erd.git

WORKDIR erd

RUN git checkout v0.2.1.0

RUN /usr/local/bin/stack install --silent --ghc-options="-fPIC" \
  --ghc-options="-static" \
  --ghc-options="-optl=-static" \
  --ghc-options="-optc=-static" \
  --ghc-options="-w"

# build pikchr binary
FROM alpine:3.16 as kroki-builder-static-pikchr

RUN apk update && apk add ca-certificates wget build-base

WORKDIR build

RUN wget -q https://pikchr.org/home/raw/7269f78c4a3aa2809bd8c278e522c4eac5568ad0fd6ed0a3f807f4a2f6367ef0 -O pikchr.c

RUN gcc -O0 -g -static -Wall -Wextra -DPIKCHR_SHELL pikchr.c -o pikchr -lm

# build static executable binary
FROM ekidd/rust-musl-builder:1.56.1 as kroki-builder-static-svgbob
COPY ops/docker/Cargo.toml .

RUN SVGBOB_VERSION=`cat Cargo.toml | grep "svgbob_cli =" | sed -r 's/.*"([^"]+)"/\1/'` \
  && cargo install --quiet --version $SVGBOB_VERSION svgbob_cli

# based on alpine 3.16
FROM eclipse-temurin:11.0.16.1_1-jre-alpine

RUN addgroup -g 1000 kroki && adduser -D -G kroki -u 1000 kroki

COPY --from=kroki-builder-static-svgbob /home/rust/.cargo/bin/svgbob /usr/bin/svgbob
COPY --from=kroki-builder-static-erd /root/.local/bin/erd /usr/bin/erd
COPY --from=kroki-builder-nomnoml /app/app.bin /usr/bin/nomnoml
COPY --from=kroki-builder-vega /app/app.bin /usr/bin/vega
COPY --from=kroki-builder-vega /app/canvas.node /usr/bin/canvas.node
COPY --from=kroki-builder-wavedrom /app/app.bin /usr/bin/wavedrom
COPY --from=kroki-builder-bytefield /app/app.bin /usr/bin/bytefield
COPY --from=kroki-builder-static-pikchr /build/pikchr /usr/bin/pikchr

RUN apk add --update --no-cache \
           libjpeg \
           giflib-dev \
           graphviz \
           ttf-freefont \
           font-noto-cjk

COPY --chown=kroki:kroki ops/docker/logback.xml /etc/kroki/logback.xml

ENV KROKI_CONTAINER_SUPPORT=""
ENV KROKI_SAFE_MODE=secure
ENV KROKI_SVGBOB_BIN_PATH=/usr/bin/svgbob
ENV KROKI_ERD_BIN_PATH=/usr/bin/erd
ENV KROKI_DOT_BIN_PATH=/usr/bin/dot
ENV KROKI_NOMNOML_BIN_PATH=/usr/bin/nomnoml
ENV KROKI_VEGA_BIN_PATH=/usr/bin/vega
ENV KROKI_WAVEDROM_BIN_PATH=/usr/bin/wavedrom
ENV KROKI_BYTEFIELD_BIN_PATH=/usr/bin/bytefield
ENV KROKI_PIKCHR_BIN_PATH=/usr/bin/pikchr
ENV JAVA_OPTS="-Dlogback.configurationFile=/etc/kroki/logback.xml -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"

COPY --chown=kroki:kroki target/kroki-server.jar /usr/local/kroki/kroki-server.jar

EXPOSE 8000

USER kroki

ENTRYPOINT exec java $JAVA_OPTS -jar /usr/local/kroki/kroki-server.jar
