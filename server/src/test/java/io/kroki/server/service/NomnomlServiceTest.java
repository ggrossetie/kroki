package io.kroki.server.service;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NomnomlServiceTest {

  @Test
  public void should_not_change_source_when_color_scheme_is_not_dark() {
    String source = "[A] -> [B]";
    assertThat(Nomnoml.applyColorScheme(source, new JsonObject())).isEqualTo(source);
    assertThat(Nomnoml.applyColorScheme(source, new JsonObject().put("color-scheme", "auto"))).isEqualTo(source);
  }

  @Test
  public void should_prepend_dark_directives_when_color_scheme_is_dark() {
    String result = Nomnoml.applyColorScheme("[A] -> [B]", new JsonObject().put("color-scheme", "dark"));
    assertThat(result).isEqualTo("#fill: #2b2b2b\n#stroke: #c9d1d9\n[A] -> [B]");
  }

  @Test
  public void should_not_override_author_directives() {
    String source = "#fill: #fafafa\n[A] -> [B]";
    String result = Nomnoml.applyColorScheme(source, new JsonObject().put("color-scheme", "dark"));
    // author #fill is kept; only the missing #stroke default is added
    assertThat(result).isEqualTo("#stroke: #c9d1d9\n" + source);
  }

  @Test
  public void should_advertise_light_and_dark_support() {
    Nomnoml nomnoml = new Nomnoml(io.vertx.core.Vertx.vertx(), new JsonObject(), null);
    assertThat(nomnoml.getSupportedColorSchemes()).containsExactly(ColorScheme.LIGHT, ColorScheme.DARK);
  }
}