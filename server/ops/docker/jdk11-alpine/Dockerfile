# based on alpine 3.12
FROM eclipse-temurin:11.0.14_9-jre-alpine

RUN addgroup -g 1000 kroki && adduser -D -G kroki -u 1000 kroki

COPY --from=yuzutech/kroki-builder-svgbob:0.5.3 /home/rust/.cargo/bin/svgbob /usr/bin/svgbob
COPY --from=yuzutech/kroki-builder-erd:0.2.1.0 /root/.local/bin/erd /usr/bin/erd
COPY --from=yuzutech/kroki-builder-pikchr:7269f78c4a /build/pikchr /usr/bin/pikchr
COPY --from=yuzutech/kroki-builder-nomnoml:1.4.0-node14.19.1-alpine3.15 /app/app.bin /usr/bin/nomnoml
COPY --from=kroki-builder-vega:latest /app/app.bin /usr/bin/vega
COPY --from=kroki-builder-vega:latest /app/canvas.node /usr/bin/canvas.node
COPY --from=kroki-builder-wavedrom:latest /app/app.bin /usr/bin/wavedrom
COPY --from=kroki-builder-bytefield:latest /app/app.bin /usr/bin/bytefield

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
