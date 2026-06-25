package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.kroki.server.security.SafeMode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Vega implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.SVG, FileFormat.PNG, FileFormat.PDF);
  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final SafeMode safeMode;
  private final Commander commander;
  private final SpecFormat specFormat;

  public Vega(Vertx vertx, JsonObject config, SpecFormat specFormat, Commander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_VEGA_BIN_PATH", "vega");
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        return DiagramSource.decode(encoded);
      }
    };
    this.safeMode = SafeMode.get(config.getString("KROKI_SAFE_MODE", "secure"), SafeMode.SECURE);
    this.commander = commander;
    this.specFormat = specFormat;
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
    if (specFormat == SpecFormat.DEFAULT) {
      return "6.2.0";
    } else {
      return "6.4.3"; // Vega Lite
    }
  }

  @Override
  public List<ColorScheme> getSupportedColorSchemes() {
    // Vega output (svg/png/pdf) carries no prefers-color-scheme query, so AUTO degrades to light.
    return List.of(ColorScheme.LIGHT, ColorScheme.DARK);
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> {
      byte[] result = vega(applyColorScheme(sourceDecoded, options).getBytes(), fileFormat.getName());
      return Buffer.buffer(result);
    });
  }

  /**
   * For {@code color-scheme=dark}, merges a dark background and config (axis/legend/title/view
   * colors) into the Vega/Vega-Lite spec. Keys already present in the spec always win, so an
   * author-provided background or config is preserved. Returns the source unchanged when the
   * scheme is not dark or the source is not a JSON object.
   */
  static String applyColorScheme(String source, JsonObject options) {
    if (ColorScheme.from(options) != ColorScheme.DARK) {
      return source;
    }
    JsonObject spec;
    try {
      spec = new JsonObject(source);
    } catch (io.vertx.core.json.DecodeException e) {
      // not a JSON object we can enrich; let the vega binary parse and report the error
      return source;
    }
    mergeDefaults(spec, darkSpec());
    return spec.encode();
  }

  private static JsonObject darkSpec() {
    return new JsonObject()
      .put("background", "#1e1e1e")
      .put("config", new JsonObject()
        .put("title", new JsonObject().put("color", "#c9d1d9").put("subtitleColor", "#c9d1d9"))
        .put("axis", new JsonObject()
          .put("domainColor", "#c9d1d9").put("gridColor", "#3c3c3c")
          .put("tickColor", "#c9d1d9").put("labelColor", "#c9d1d9").put("titleColor", "#c9d1d9"))
        .put("legend", new JsonObject().put("labelColor", "#c9d1d9").put("titleColor", "#c9d1d9"))
        .put("view", new JsonObject().put("stroke", "#3c3c3c")));
  }

  private static void mergeDefaults(JsonObject target, JsonObject defaults) {
    for (String key : defaults.fieldNames()) {
      Object defaultValue = defaults.getValue(key);
      Object currentValue = target.getValue(key);
      if (currentValue == null) {
        target.put(key, defaultValue);
      } else if (currentValue instanceof JsonObject && defaultValue instanceof JsonObject) {
        mergeDefaults((JsonObject) currentValue, (JsonObject) defaultValue);
      }
      // otherwise keep the value already present in the spec
    }
  }

  private byte[] vega(byte[] source, String format) throws IOException, InterruptedException, IllegalStateException {
    String vegaSafeMode = safeMode == SafeMode.UNSAFE ? "unsafe" : "secure";
    return commander.execute(source, binPath,
      "--output-format=" + format,
      "--safe-mode=" + vegaSafeMode,
      "--spec-format=" + specFormat.name().toLowerCase());
  }

  public enum SpecFormat {
    DEFAULT,
    LITE;
  }
}
