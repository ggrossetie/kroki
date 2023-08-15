package io.kroki.server.service;

import io.kroki.server.action.NuCommander;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Graphviz implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.PNG, FileFormat.SVG, FileFormat.JPEG, FileFormat.PDF);

  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final NuCommander commander;

  public Graphviz(Vertx vertx, JsonObject config, NuCommander commander) {
    this.vertx = vertx;
    this.binPath = config.getString("KROKI_DOT_BIN_PATH", "dot");
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
    return "8.0.5";
  }

  @Override
  public void convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options, Handler<AsyncResult<Buffer>> handler) {
    try {
      List<String> commands = new ArrayList<>();
      commands.add(binPath);
      // Supported format:
      // canon cmap cmapx cmapx_np dot dot_json eps fig gd gd2 gif gv imap imap_np ismap
      // jpe jpeg jpg json json0 mp pdf pic plain plain-ext
      // png pov ps ps2
      // svg svgz tk vml vmlz vrml wbmp x11 xdot xdot1.2 xdot1.4 xdot_json xlib
      commands.add("-T" + fileFormat.getName());
      String scale = options.getString("scale");
      if (scale != null) {
        commands.add("-s" + scale);
      }
      String layout = options.getString("layout");
      if (layout != null) {
        commands.add("-K" + layout);
      }
      for (String fieldName : options.fieldNames()) {
        if (fieldName.startsWith("node-attribute-")) {
          String name = fieldName.replace("node-attribute-", "");
          commands.add("-N" + name + "=" + options.getString(fieldName));
        }
        if (fieldName.startsWith("graph-attribute-")) {
          String name = fieldName.replace("graph-attribute-", "");
          commands.add("-G" + name + "=" + options.getString(fieldName));
        }
        if (fieldName.startsWith("edge-attribute-")) {
          String name = fieldName.replace("edge-attribute-", "");
          commands.add("-E" + name + "=" + options.getString(fieldName));
        }
      }
      commander.execute(sourceDecoded.getBytes(), this.vertx.getOrCreateContext(), handler, commands.toArray(new String[0]));
    } catch (InterruptedException e) {
      handler.handle(new AsyncResult<Buffer>() {
        @Override
        public Buffer result() {
          return null;
        }

        @Override
        public Throwable cause() {
          return e;
        }

        @Override
        public boolean succeeded() {
          return false;
        }

        @Override
        public boolean failed() {
          return true;
        }
      });
    }
  }
}
