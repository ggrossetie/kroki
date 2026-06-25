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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Goat implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.SVG);
  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final Commander commander;

  public Goat(Vertx vertx, JsonObject config, Commander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_GOAT_BIN_PATH", "goat");
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        // GoAT renders ASCII art on a character grid, so leading whitespace is
        // significant. Decode without trimming to preserve the indentation of
        // the first line (see Svgbob, Ditaa).
        return DiagramSource.decode(encoded, false);
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
    return "undefined";
  }

  // Light color GoAT draws with when color-scheme=dark forces a dark-mode look.
  private static final String DARK_FG = "#FFFFFF";

  @Override
  public List<ColorScheme> getSupportedColorSchemes() {
    // GoAT's SVG is adaptive out of the box (prefers-color-scheme media query), so AUTO is native.
    return List.of(ColorScheme.LIGHT, ColorScheme.DARK, ColorScheme.AUTO);
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> {
      List<String> commands = new ArrayList<>();
      commands.add(binPath);

      ColorScheme colorScheme = ColorScheme.from(options);
      String svgColorDarkScheme = options.getString("svg-color-dark-scheme");
      String svgColorLightScheme = options.getString("svg-color-light-scheme");
      // GoAT already emits an adaptive SVG, so auto/light keep the native behavior. color-scheme=dark
      // forces a dark-mode look by drawing with a light color in the light scheme too; an explicit
      // svg-color-light-scheme option still wins.
      if (colorScheme == ColorScheme.DARK && svgColorLightScheme == null) {
        svgColorLightScheme = svgColorDarkScheme != null ? svgColorDarkScheme : DARK_FG;
      }
      if (svgColorDarkScheme != null) {
        commands.add("-svg-color-dark-scheme");
        commands.add(svgColorDarkScheme);
      }

      if (svgColorLightScheme != null) {
        commands.add("-svg-color-light-scheme");
        commands.add(svgColorLightScheme);
      }

      String utf8 = options.getString("utf8");
      if (utf8 != null) {
        commands.add("-utf8");
      }

      // TODO: Integrate `css` option.
      // Currently GoAT only supports passing custom CSS via files.
      // It would be best if we can inline it directly instead.

      byte[] result = commander.execute(sourceDecoded.getBytes(), commands.toArray(new String[0]));
      return Buffer.buffer(result);
    });
  }
}
