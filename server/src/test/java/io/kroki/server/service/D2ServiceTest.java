package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class D2ServiceTest {

  private Commander mockCommander() {
    Commander commanderMock = mock(Commander.class);
    try {
      when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>d2</svg>".getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return commanderMock;
  }

  @Test
  public void should_default_theme_to_dark_when_color_scheme_is_dark() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mockCommander();
    D2 d2 = new D2(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark");
    d2.convert("a -> b", "d2", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    // dark-mauve == theme id 200
    Mockito.verify(commanderMock).execute("a -> b".getBytes(), "d2", "--theme=200", "-");
  }

  @Test
  public void should_set_dark_theme_for_adaptive_svg_when_color_scheme_is_auto() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mockCommander();
    D2 d2 = new D2(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "auto");
    d2.convert("a -> b", "d2", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    Mockito.verify(commanderMock).execute("a -> b".getBytes(), "d2", "--dark-theme=200", "-");
  }

  @Test
  public void should_let_explicit_theme_win_over_color_scheme() throws Throwable {
    Vertx vertx = Vertx.vertx();
    Commander commanderMock = mockCommander();
    D2 d2 = new D2(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark").put("theme", "aubergine");
    d2.convert("a -> b", "d2", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    // aubergine == theme id 7, color-scheme must not override it
    Mockito.verify(commanderMock).execute("a -> b".getBytes(), "d2", "--theme=7", "-");
  }
}