package io.kroki.server.service;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramRegistryDisabledTest {

  @Test
  void should_parse_a_comma_separated_list() {
    JsonObject config = new JsonObject().put("KROKI_DISABLED_DIAGRAM_TYPES", " PlantUML,mermaid ,, ");
    assertThat(DiagramRegistry.getDisabledDiagramTypes(config)).containsExactly("mermaid", "plantuml");
  }

  @Test
  void should_parse_a_json_array() {
    JsonObject config = new JsonObject().put("KROKI_DISABLED_DIAGRAM_TYPES", new JsonArray().add("plantuml").add(" c4plantuml "));
    assertThat(DiagramRegistry.getDisabledDiagramTypes(config)).containsExactly("c4plantuml", "plantuml");
  }

  @Test
  void should_return_an_empty_set_when_not_configured() {
    assertThat(DiagramRegistry.getDisabledDiagramTypes(new JsonObject())).isEmpty();
    assertThat(DiagramRegistry.getDisabledDiagramTypes(new JsonObject().put("KROKI_DISABLED_DIAGRAM_TYPES", ""))).isEmpty();
  }
}
