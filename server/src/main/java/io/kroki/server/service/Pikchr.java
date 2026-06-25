package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Pikchr implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Collections.singletonList(FileFormat.SVG);
  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final Commander commander;

  public Pikchr(Vertx vertx, JsonObject config, Commander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_PIKCHR_BIN_PATH", "pikchr");
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        return DiagramSource.decode(encoded);
      }
    };
    this.commander = commander;
  }

  @Override
  public List<FileFormat> getSupportedFormats() {
    return SUPPORTED_FORMATS;
  }

  @Override
  public SourceDecoder getSourceDecoder() {
    return sourceDecoder;
  }

  @Override
  public String getVersion() {
    return "7269f78c4a";
  }

  @Override
  public List<ColorScheme> getSupportedColorSchemes() {
    // Pikchr's --dark-mode inverts colors; the SVG carries no media query, so AUTO degrades to light.
    return List.of(ColorScheme.LIGHT, ColorScheme.DARK);
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> {
      byte[] result = pikchr(sourceDecoded.getBytes(), options);
      return Buffer.buffer(result);
    });
  }

  private byte[] pikchr(byte[] source, JsonObject options) throws IOException, InterruptedException, IllegalStateException {
    if (ColorScheme.from(options) == ColorScheme.DARK) {
      return commander.execute(source, binPath, "--dark-mode", "--svg-only", "-");
    }
    return commander.execute(source, binPath, "--svg-only", "-");
  }
}
