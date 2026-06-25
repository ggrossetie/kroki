package io.kroki.server.service;

import io.kroki.server.action.Commander;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class GoatServiceTest {

  private static Goat newGoat(Vertx vertx) {
    JsonObject config = new JsonObject();
    return new Goat(vertx, config, new Commander(config));
  }

  private static Commander mockCommander() {
    Commander commanderMock = mock(Commander.class);
    try {
      when(commanderMock.execute(any(), any(String[].class))).thenReturn("<svg>goat</svg>".getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return commanderMock;
  }

  @Test
  public void should_force_light_strokes_when_color_scheme_is_dark(Vertx vertx) throws Throwable {
    Commander commanderMock = mockCommander();
    Goat goat = new Goat(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark");
    goat.convert("x", "goat", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    Mockito.verify(commanderMock).execute("x".getBytes(), "goat", "-svg-color-light-scheme", "#FFFFFF");
  }

  @Test
  public void should_keep_native_adaptive_output_when_color_scheme_is_auto(Vertx vertx) throws Throwable {
    Commander commanderMock = mockCommander();
    Goat goat = new Goat(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "auto");
    goat.convert("x", "goat", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    // GoAT is adaptive by default: no color arguments are added
    Mockito.verify(commanderMock).execute("x".getBytes(), "goat");
  }

  @Test
  public void should_let_explicit_light_scheme_win_over_color_scheme(Vertx vertx) throws Throwable {
    Commander commanderMock = mockCommander();
    Goat goat = new Goat(vertx, new JsonObject(), commanderMock);
    JsonObject options = new JsonObject().put("color-scheme", "dark").put("svg-color-light-scheme", "blue");
    goat.convert("x", "goat", FileFormat.SVG, options).await(2, TimeUnit.SECONDS);
    Mockito.verify(commanderMock).execute("x".getBytes(), "goat", "-svg-color-light-scheme", "blue");
  }

  @Test
  public void should_advertise_light_dark_and_auto_support(Vertx vertx) {
    assertThat(newGoat(vertx).getSupportedColorSchemes())
      .containsExactly(ColorScheme.LIGHT, ColorScheme.DARK, ColorScheme.AUTO);
  }

  @Test
  public void should_preserve_leading_whitespace_on_the_first_line(Vertx vertx) throws DecodeException {
    // GoAT is whitespace-sensitive: the first line is indented by 4 spaces just
    // like the others. Trimming the source would strip that indentation, shifting
    // the top border to x=0 and misaligning the box.
    String source = "    .------------.\n" +
      "o-->|Hello, world|--*\n" +
      "    '------------'\n";
    String encoded = new String(DiagramSource.encode(source));

    String decoded = newGoat(vertx).getSourceDecoder().decode(encoded);

    assertThat(decoded).isEqualTo(source);
    assertThat(decoded).startsWith("    .");
  }
}