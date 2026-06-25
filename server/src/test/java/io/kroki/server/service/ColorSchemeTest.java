package io.kroki.server.service;

import io.kroki.server.error.BadRequestException;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ColorSchemeTest {

  @Test
  public void should_default_to_light_when_absent() {
    assertThat(ColorScheme.from(new JsonObject())).isEqualTo(ColorScheme.LIGHT);
  }

  @Test
  public void should_parse_known_values_case_insensitively() {
    assertThat(ColorScheme.from(new JsonObject().put("color-scheme", "light"))).isEqualTo(ColorScheme.LIGHT);
    assertThat(ColorScheme.from(new JsonObject().put("color-scheme", "DARK"))).isEqualTo(ColorScheme.DARK);
    assertThat(ColorScheme.from(new JsonObject().put("color-scheme", " Auto "))).isEqualTo(ColorScheme.AUTO);
  }

  @Test
  public void should_treat_empty_value_as_light() {
    assertThat(ColorScheme.from(new JsonObject().put("color-scheme", ""))).isEqualTo(ColorScheme.LIGHT);
  }

  @Test
  public void should_reject_unknown_value() {
    assertThatThrownBy(() -> ColorScheme.from(new JsonObject().put("color-scheme", "sepia")))
      .isInstanceOf(BadRequestException.class)
      .hasMessageContaining("color-scheme");
  }
}
