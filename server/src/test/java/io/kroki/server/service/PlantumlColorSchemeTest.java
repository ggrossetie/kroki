package io.kroki.server.service;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlantumlColorSchemeTest {

  @Test
  public void should_not_set_theme_without_color_scheme() {
    assertThat(Plantuml.resolveTheme(new JsonObject())).isNull();
  }

  @Test
  public void should_default_to_dark_theme_when_color_scheme_is_dark() {
    assertThat(Plantuml.resolveTheme(new JsonObject().put("color-scheme", "dark"))).isEqualTo("cyborg");
  }

  @Test
  public void should_let_explicit_theme_win_over_color_scheme() {
    assertThat(Plantuml.resolveTheme(new JsonObject().put("color-scheme", "dark").put("theme", "spacelab")))
      .isEqualTo("spacelab");
  }

  @Test
  public void should_not_set_dark_theme_for_auto_or_light() {
    assertThat(Plantuml.resolveTheme(new JsonObject().put("color-scheme", "auto"))).isNull();
    assertThat(Plantuml.resolveTheme(new JsonObject().put("color-scheme", "light"))).isNull();
  }
}