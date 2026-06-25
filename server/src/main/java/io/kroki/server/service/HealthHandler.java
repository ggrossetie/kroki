package io.kroki.server.service;

import io.kroki.server.Main;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthHandler {

  private final String krokiVersionNumber;
  private final String krokiBuildHash;
  private final List<ServiceVersion> serviceVersions;
  private final Map<String, List<String>> colorSchemes;

  public HealthHandler(Map<String, String> versions) {
    this(versions, Collections.emptyMap(), null);
  }

  public HealthHandler(Map<String, String> versions, KrokiBlockedThreadChecker blockedThreadChecker) {
    this(versions, Collections.emptyMap(), blockedThreadChecker);
  }

  public HealthHandler(Map<String, String> versions, Map<String, List<String>> colorSchemes, KrokiBlockedThreadChecker blockedThreadChecker) {
    krokiVersionNumber = Main.getApplicationProperty("app.version", "");
    krokiBuildHash = Main.getApplicationProperty("app.sha1", "");
    serviceVersions = new ArrayList<>();
    // QUESTION: should we dynamically fetch the versions ?
    for (Map.Entry<String, String> entry : versions.entrySet()) {
      serviceVersions.add(new ServiceVersion(entry.getKey(), entry.getValue()));
    }
    this.colorSchemes = colorSchemes;
  }

  public Handler<RoutingContext> create() {
    return routingContext -> {
      Map<String, Object> data = new HashMap<>();
      data.put("status", "pass");
      HashMap<String, Object> versions = new HashMap<>();
      HashMap<String, Object> krokiVersion = new HashMap<>();
      krokiVersion.put("number", krokiVersionNumber);
      krokiVersion.put("build_hash", krokiBuildHash);
      versions.put("kroki", krokiVersion);
      data.put("version", versions);
      for (ServiceVersion serviceVersion : serviceVersions) {
        versions.put(serviceVersion.getService(), serviceVersion.getVersion());
      }
      // Advertise dark-mode support: only diagrams that honor more than the default `light`.
      HashMap<String, Object> colorScheme = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : colorSchemes.entrySet()) {
        if (entry.getValue().size() > 1) {
          colorScheme.put(entry.getKey(), entry.getValue());
        }
      }
      if (!colorScheme.isEmpty()) {
        data.put("color_scheme", colorScheme);
      }
      routingContext
        .response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/health+json")
        .end(Json.encode(data));
    };
  }

  public String getKrokiVersionNumber() {
    return krokiVersionNumber;
  }

  public String getKrokiBuildHash() {
    return krokiBuildHash;
  }

  public List<ServiceVersion> getServiceVersions() {
    return serviceVersions;
  }
}
