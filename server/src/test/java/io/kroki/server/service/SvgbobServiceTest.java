package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SvgbobServiceTest {

  private Commander mockCommander() {
    Commander commanderMock = mock(Commander.class);
    try {
      when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>svgbob</svg>".getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return commanderMock;
  }

  @Test
  public void should_inject_dark_palette_when_color_scheme_is_dark() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mockCommander();
    Svgbob svgbob = new Svgbob(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark");
    svgbob.convert("a---b", "svgbob", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    Mockito.verify(commanderMock).execute("a---b".getBytes(), "svgbob", "--background=#1e1e1e", "--fill-color=#c9d1d9");
  }

  @Test
  public void should_let_explicit_background_win_over_color_scheme() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mockCommander();
    Svgbob svgbob = new Svgbob(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark").put("background", "black");
    svgbob.convert("a---b", "svgbob", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    // synthesized fill-color is still added, but the explicit background wins (single --background)
    Mockito.verify(commanderMock).execute("a---b".getBytes(), "svgbob", "--fill-color=#c9d1d9", "--background=black");
  }

  @Test
  public void should_advertise_light_and_dark_support() {
    Svgbob svgbob = new Svgbob(Vertx.vertx(), new JsonObject(), mockCommander());
    assertThat(svgbob.getSupportedColorSchemes()).containsExactly(ColorScheme.LIGHT, ColorScheme.DARK);
  }
}