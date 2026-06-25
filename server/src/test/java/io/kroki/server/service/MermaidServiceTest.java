package io.kroki.server.service;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MermaidServiceTest {

  @Test
  public void should_not_alter_options_when_color_scheme_absent() {
    JsonObject options = new JsonObject().put("theme", "forest");
    assertThat(Mermaid.applyColorScheme(options)).isSameAs(options);
  }

  @Test
  public void should_map_dark_color_scheme_to_dark_theme_and_strip_option() {
    JsonObject options = new JsonObject().put("color-scheme", "dark");
    JsonObject result = Mermaid.applyColorScheme(options);
    assertThat(result.getString("theme")).isEqualTo("dark");
    assertThat(result.containsKey("color-scheme")).isFalse();
  }

  @Test
  public void should_let_explicit_theme_win_over_color_scheme() {
    JsonObject options = new JsonObject().put("color-scheme", "dark").put("theme", "forest");
    JsonObject result = Mermaid.applyColorScheme(options);
    assertThat(result.getString("theme")).isEqualTo("forest");
  }

  @Test
  public void should_degrade_auto_to_light_theme() {
    JsonObject options = new JsonObject().put("color-scheme", "auto");
    JsonObject result = Mermaid.applyColorScheme(options);
    // mermaid has no prefers-color-scheme aware output: auto leaves the default (light) theme
    assertThat(result.containsKey("theme")).isFalse();
    assertThat(result.containsKey("color-scheme")).isFalse();
  }
}