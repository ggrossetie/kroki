package io.kroki.server.service;

import io.kroki.server.action.Delegator;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.BadRequestException;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

import java.util.Arrays;
import java.util.List;

public class Mermaid implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.PNG, FileFormat.SVG);

  private final Delegator delegator;
  private final String host;
  private final int port;
  private final SourceDecoder sourceDecoder;

  public Mermaid(Vertx vertx, JsonObject config, Delegator delegator) {
    this.delegator = delegator;
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        return DiagramSource.decode(encoded);
      }
    };
    this.host = config.getString("KROKI_MERMAID_HOST", "127.0.0.1");
    this.port = config.getInteger("KROKI_MERMAID_PORT", 8002);
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
    return "11.15.0";
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    JsonObject effectiveOptions;
    try {
      effectiveOptions = applyColorScheme(options);
    } catch (BadRequestException e) {
      return Future.failedFuture(e);
    }
    String requestURI = "/" + serviceName + "/" + fileFormat.getName();
    Future<HttpResponse<Buffer>> httpResponseFuture = this.delegator.delegate(host, port, requestURI, sourceDecoded, effectiveOptions);
    return Delegator.handle(host, port, requestURI, httpResponseFuture);
  }

  /**
   * Translates the unified {@code color-scheme} option into Mermaid's own {@code theme} config and
   * strips {@code color-scheme} before delegating, so the companion service stays generic.
   * Mermaid has no prefers-color-scheme aware output, so {@code auto} degrades to the light theme.
   * An explicit {@code theme} option always wins.
   */
  static JsonObject applyColorScheme(JsonObject options) {
    ColorScheme colorScheme = ColorScheme.from(options);
    if (!options.containsKey(ColorScheme.OPTION_NAME)) {
      return options;
    }
    JsonObject effectiveOptions = options.copy();
    effectiveOptions.remove(ColorScheme.OPTION_NAME);
    if (colorScheme == ColorScheme.DARK && !effectiveOptions.containsKey("theme")) {
      effectiveOptions.put("theme", "dark");
    }
    return effectiveOptions;
  }
}
