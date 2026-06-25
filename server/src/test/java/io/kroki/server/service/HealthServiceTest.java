package io.kroki.server.service;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthServiceTest {

  @Test
  void should_get_version_info() {
    HashMap<String, String> versions = new HashMap<>();
    versions.put("plantuml", "1.2022.5");
    HealthHandler healthHandler = new HealthHandler(versions);
    String krokiBuildHash = healthHandler.getKrokiBuildHash();
    String krokiVersionNumber = healthHandler.getKrokiVersionNumber();
    List<ServiceVersion> serviceVersions = healthHandler.getServiceVersions();
    assertThat(krokiBuildHash).isNotEmpty();
    assertThat(krokiVersionNumber).isNotEmpty();
    assertThat(serviceVersions).contains(new ServiceVersion("plantuml", "1.2022.5"));
  }

  @Test
  void should_return_health() {
    RoutingContext routingContextMock = mock(RoutingContext.class);
    HttpServerResponse httpServerResponseMock = mock(HttpServerResponse.class);
    when(routingContextMock.response()).thenReturn(httpServerResponseMock);
    when(httpServerResponseMock.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(httpServerResponseMock);
    Handler<RoutingContext> healthHandler = new HealthHandler(new HashMap<>()).create();
    healthHandler.handle(routingContextMock);
    Mockito.verify(httpServerResponseMock).end(argThat((ArgumentMatcher<String>) argument ->
      {
        JsonObject responseJson = new JsonObject(argument);
        return responseJson.getString("status").equals("pass") && responseJson.getJsonObject("version") != null;
      }
    ));
  }

  @Test
  void should_expose_color_scheme_support() {
    RoutingContext routingContextMock = mock(RoutingContext.class);
    HttpServerResponse httpServerResponseMock = mock(HttpServerResponse.class);
    when(routingContextMock.response()).thenReturn(httpServerResponseMock);
    when(httpServerResponseMock.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(httpServerResponseMock);
    Map<String, List<String>> colorSchemes = new HashMap<>();
    colorSchemes.put("d2", Arrays.asList("light", "dark", "auto"));
    colorSchemes.put("mermaid", Arrays.asList("light", "dark"));
    colorSchemes.put("ditaa", List.of("light")); // light-only must not be advertised
    Handler<RoutingContext> healthHandler = new HealthHandler(new HashMap<>(), colorSchemes, null).create();
    healthHandler.handle(routingContextMock);
    Mockito.verify(httpServerResponseMock).end(argThat((ArgumentMatcher<String>) argument ->
      {
        JsonObject responseJson = new JsonObject(argument);
        JsonObject colorScheme = responseJson.getJsonObject("color_scheme");
        return colorScheme != null
          && colorScheme.getJsonArray("d2").getList().equals(Arrays.asList("light", "dark", "auto"))
          && colorScheme.getJsonArray("mermaid").getList().equals(Arrays.asList("light", "dark"))
          && !colorScheme.containsKey("ditaa");
      }
    ));
  }
}
