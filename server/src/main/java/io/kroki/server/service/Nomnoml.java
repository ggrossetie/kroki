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
import java.util.regex.Pattern;

public class Nomnoml implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Collections.singletonList(FileFormat.SVG);
  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final Commander commander;

  public Nomnoml(Vertx vertx, JsonObject config, Commander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_NOMNOML_BIN_PATH", "nomnoml");
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
    return "1.7.0";
  }

  // nomnoml has no native theme; the unified color-scheme option prepends style directives.
  private static final String DARK_FILL = "#2b2b2b";
  private static final String DARK_STROKE = "#c9d1d9";

  @Override
  public List<ColorScheme> getSupportedColorSchemes() {
    // nomnoml output carries no prefers-color-scheme query, so AUTO degrades to light.
    return List.of(ColorScheme.LIGHT, ColorScheme.DARK);
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> {
      byte[] result = nomnoml(applyColorScheme(sourceDecoded, options).getBytes());
      return Buffer.buffer(result);
    });
  }

  /**
   * For {@code color-scheme=dark}, prepends dark {@code #fill}/{@code #stroke} style directives.
   * Each directive is only added when the source does not already declare it, so author-provided
   * directives always win. Other schemes return the source unchanged.
   */
  static String applyColorScheme(String source, JsonObject options) {
    if (ColorScheme.from(options) != ColorScheme.DARK) {
      return source;
    }
    StringBuilder prefix = new StringBuilder();
    if (!hasDirective(source, "fill")) {
      prefix.append("#fill: ").append(DARK_FILL).append("\n");
    }
    if (!hasDirective(source, "stroke")) {
      prefix.append("#stroke: ").append(DARK_STROKE).append("\n");
    }
    return prefix + source;
  }

  private static boolean hasDirective(String source, String name) {
    // nomnoml directives appear at the start of a line as "#name: ..."
    return Pattern.compile("(?m)^\\s*#" + name + "\\s*:").matcher(source).find();
  }

  private byte[] nomnoml(byte[] source) throws IOException, InterruptedException, IllegalStateException {
    return commander.execute(source, binPath);
  }
}
