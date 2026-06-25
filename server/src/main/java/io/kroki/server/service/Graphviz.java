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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Graphviz implements DiagramService {

  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.PNG, FileFormat.SVG, FileFormat.JPEG, FileFormat.PDF);

  // Graphviz has no native theme, so the unified color-scheme option synthesizes a palette.
  private static final String DARK_BG = "#1e1e1e";
  private static final String DARK_FG = "#c9d1d9";
  // For color-scheme=auto we inject a prefers-color-scheme media query so a single SVG adapts.
  private static final String DARK_MODE_STYLE =
    "<style>@media (prefers-color-scheme:dark){"
      + ".graph>polygon{fill:" + DARK_BG + ";stroke:" + DARK_BG + "}"
      + "text{fill:" + DARK_FG + "}"
      + ".node ellipse,.node polygon,.node path,.node rect{stroke:" + DARK_FG + "}"
      + ".edge path{stroke:" + DARK_FG + "}"
      + ".edge polygon{fill:" + DARK_FG + ";stroke:" + DARK_FG + "}"
      + "}</style>";

  private final Vertx vertx;
  private final String binPath;
  private final SourceDecoder sourceDecoder;
  private final Commander commander;

  public Graphviz(Vertx vertx, JsonObject config, Commander commander) {
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
    return "14.1.3";
  }

  @Override
  public List<ColorScheme> getSupportedColorSchemes() {
    return List.of(ColorScheme.LIGHT, ColorScheme.DARK, ColorScheme.AUTO);
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> {
      byte[] result = dot(sourceDecoded.getBytes(), fileFormat.getName(), options);
      return Buffer.buffer(result);
    });
  }

  private byte[] dot(byte[] source, String format, JsonObject options) throws IOException, InterruptedException, IllegalStateException {
    ColorScheme colorScheme = ColorScheme.from(options);
    List<String> commands = new ArrayList<>();
    commands.add(binPath);
    // Supported format:
    // canon cmap cmapx cmapx_np dot dot_json eps fig gd gd2 gif gv imap imap_np ismap
    // jpe jpeg jpg json json0 mp pdf pic plain plain-ext
    // png pov ps ps2
    // svg svgz tk vml vmlz vrml wbmp x11 xdot xdot1.2 xdot1.4 xdot_json xlib
    commands.add("-T" + format);
    String scale = options.getString("scale");
    if (scale != null) {
      commands.add("-s" + scale);
    }
    String layout = options.getString("layout");
    if (layout != null) {
      commands.add("-K" + layout);
    }
    // color-scheme provides defaults only; explicit *-attribute-* options below always win.
    if (colorScheme == ColorScheme.DARK) {
      addDarkPaletteDefault(options, commands, "graph-attribute-bgcolor", "-Gbgcolor=" + DARK_BG);
      addDarkPaletteDefault(options, commands, "node-attribute-color", "-Ncolor=" + DARK_FG);
      addDarkPaletteDefault(options, commands, "node-attribute-fontcolor", "-Nfontcolor=" + DARK_FG);
      addDarkPaletteDefault(options, commands, "edge-attribute-color", "-Ecolor=" + DARK_FG);
      addDarkPaletteDefault(options, commands, "edge-attribute-fontcolor", "-Efontcolor=" + DARK_FG);
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
    byte[] result = commander.execute(source, commands.toArray(new String[0]));
    if (colorScheme == ColorScheme.AUTO && "svg".equals(format)) {
      result = injectDarkModeStyle(result);
    }
    return result;
  }

  private static void addDarkPaletteDefault(JsonObject options, List<String> commands, String optionKey, String command) {
    if (options.getString(optionKey) == null) {
      commands.add(command);
    }
  }

  private static byte[] injectDarkModeStyle(byte[] svg) {
    String content = new String(svg, StandardCharsets.UTF_8);
    int svgTagStart = content.indexOf("<svg");
    if (svgTagStart < 0) {
      return svg;
    }
    int insertAt = content.indexOf('>', svgTagStart);
    if (insertAt < 0) {
      return svg;
    }
    String result = content.substring(0, insertAt + 1) + DARK_MODE_STYLE + content.substring(insertAt + 1);
    return result.getBytes(StandardCharsets.UTF_8);
  }
}
