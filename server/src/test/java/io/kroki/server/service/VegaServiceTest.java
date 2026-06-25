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

public class VegaServiceTest {

  @Test
  public void should_call_vega_with_correct_arguments() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mock(Commander.class);
    when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>vega</svg>".getBytes());
    HashMap<String, Object> config = new HashMap<>();
    config.put("KROKI_SAFE_MODE", "unsafe");
    config.put("KROKI_VEGA_BIN_PATH", "/path/to/vega");
    Vega vegaService = new Vega(vertx, new JsonObject(config), Vega.SpecFormat.DEFAULT, commanderMock);
    Buffer buffer = vegaService.convert("{}", "vega", FileFormat.SVG, new JsonObject()).await(4, TimeUnit.SECONDS);
    assertThat(buffer.toString()).isEqualTo("<svg>vega</svg>");
    Mockito.verify(commanderMock).execute("{}".getBytes(), "/path/to/vega", "--output-format=svg", "--safe-mode=unsafe", "--spec-format=default");
  }

  @Test
  public void should_call_vega_lite_with_correct_arguments() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mock(Commander.class);
    when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>vega-lite</svg>".getBytes());
    HashMap<String, Object> config = new HashMap<>();
    config.put("KROKI_SAFE_MODE", "unsafe");
    config.put("KROKI_VEGA_BIN_PATH", "/path/to/vega");
    Vega vegaService = new Vega(vertx, new JsonObject(config), Vega.SpecFormat.LITE, commanderMock);
    Buffer buffer = vegaService.convert("{}", "vegalite", FileFormat.SVG, new JsonObject()).await(4, TimeUnit.SECONDS);
    assertThat(buffer.toString()).isEqualTo("<svg>vega-lite</svg>");
    Mockito.verify(commanderMock).execute("{}".getBytes(), "/path/to/vega", "--output-format=svg", "--safe-mode=unsafe", "--spec-format=lite");
  }

  @Test
  public void should_not_change_spec_when_color_scheme_is_not_dark() {
    String source = "{\"mark\":\"bar\"}";
    assertThat(Vega.applyColorScheme(source, new JsonObject())).isEqualTo(source);
    assertThat(Vega.applyColorScheme(source, new JsonObject().put("color-scheme", "auto"))).isEqualTo(source);
  }

  @Test
  public void should_merge_dark_background_and_config_when_color_scheme_is_dark() {
    String result = Vega.applyColorScheme("{\"mark\":\"bar\"}", new JsonObject().put("color-scheme", "dark"));
    JsonObject spec = new JsonObject(result);
    assertThat(spec.getString("background")).isEqualTo("#1e1e1e");
    assertThat(spec.getString("mark")).isEqualTo("bar");
    assertThat(spec.getJsonObject("config").getJsonObject("axis").getString("labelColor")).isEqualTo("#c9d1d9");
  }

  @Test
  public void should_let_spec_values_win_over_dark_defaults() {
    String source = "{\"background\":\"white\",\"config\":{\"axis\":{\"labelColor\":\"red\"}}}";
    JsonObject spec = new JsonObject(Vega.applyColorScheme(source, new JsonObject().put("color-scheme", "dark")));
    // author-provided values are preserved
    assertThat(spec.getString("background")).isEqualTo("white");
    assertThat(spec.getJsonObject("config").getJsonObject("axis").getString("labelColor")).isEqualTo("red");
    // but missing dark defaults are still merged in
    assertThat(spec.getJsonObject("config").getJsonObject("legend").getString("labelColor")).isEqualTo("#c9d1d9");
  }

  @Test
  public void should_return_source_unchanged_when_not_json() {
    String notJson = "this is not json";
    assertThat(Vega.applyColorScheme(notJson, new JsonObject().put("color-scheme", "dark"))).isEqualTo(notJson);
  }

  @Test
  public void should_advertise_light_and_dark_support() {
    Vega vega = new Vega(Vertx.vertx(), new JsonObject(), Vega.SpecFormat.DEFAULT, mock(Commander.class));
    assertThat(vega.getSupportedColorSchemes()).containsExactly(ColorScheme.LIGHT, ColorScheme.DARK);
  }
}
