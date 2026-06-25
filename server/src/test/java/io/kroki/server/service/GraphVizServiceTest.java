package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphVizServiceTest {

  @Test
  public void should_call_graphviz_with_correct_arguments() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mock(Commander.class);
    when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>graphviz</svg>".getBytes());
    HashMap<String, Object> config = new HashMap<>();
    config.put("KROKI_SAFE_MODE", "unsafe");
    config.put("KROKI_DOT_BIN_PATH", "/path/to/dot");
    Graphviz graphvizService = new Graphviz(vertx, new JsonObject(config), commanderMock);
    JsonObject options = new JsonObject();
    options.put("node-attribute-fontcolor", "Crimson");
    options.put("node-attribute-shape", "rect");
    options.put("layout", "neato");
    options.put("graph-attribute-fontcolor", "SteelBlue");
    options.put("graph-attribute-label", "Hello World");
    options.put("edge-attribute-color", "NavajoWhite");
    options.put("edge-attribute-arrowhead", "diamond");
    Buffer buffer = graphvizService.convert("{}", "graphviz", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    assertThat(buffer.toString()).isEqualTo("<svg>graphviz</svg>");
    Mockito.verify(commanderMock).execute("{}".getBytes(), "/path/to/dot", "-Tsvg", "-Kneato", "-Nfontcolor=Crimson", "-Nshape=rect", "-Gfontcolor=SteelBlue", "-Glabel=Hello World", "-Ecolor=NavajoWhite", "-Earrowhead=diamond");
  }

  @Test
  public void should_inject_dark_palette_when_color_scheme_is_dark() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mock(Commander.class);
    when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>graphviz</svg>".getBytes());
    Graphviz graphvizService = new Graphviz(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject();
    options.put("color-scheme", "dark");
    // an explicit attribute must win over the synthesized default
    options.put("graph-attribute-bgcolor", "black");
    graphvizService.convert("{}", "graphviz", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    Mockito.verify(commanderMock).execute("{}".getBytes(), "dot", "-Tsvg",
      "-Ncolor=#c9d1d9", "-Nfontcolor=#c9d1d9", "-Ecolor=#c9d1d9", "-Efontcolor=#c9d1d9",
      "-Gbgcolor=black");
  }

  @Test
  public void should_inject_media_query_when_color_scheme_is_auto() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mock(Commander.class);
    when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg width=\"1\"><g/></svg>".getBytes());
    Graphviz graphvizService = new Graphviz(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "auto");
    Buffer buffer = graphvizService.convert("{}", "graphviz", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    assertThat(buffer.toString())
      .contains("@media (prefers-color-scheme:dark)")
      .startsWith("<svg width=\"1\"><style>");
    // no graphviz palette arguments are added for auto
    Mockito.verify(commanderMock).execute("{}".getBytes(), "dot", "-Tsvg");
  }
}
