package io.kroki.server.service;

import io.kroki.server.error.DiagramTypeDisabledException;
import io.kroki.server.error.MethodNotAllowedException;
import io.kroki.server.error.UnsupportedFormatException;
import io.kroki.server.response.Caching;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiagramRegistry {

  public static final String DISABLED_DIAGRAM_TYPES_KEY = "KROKI_DISABLED_DIAGRAM_TYPES";

  private static final Logger logger = LoggerFactory.getLogger(DiagramRegistry.class);

  private static class Entry {
    final DiagramHandler handler;
    final List<Route> routes;

    Entry(DiagramHandler handler, List<Route> routes) {
      this.handler = handler;
      this.routes = routes;
    }
  }

  private final Map<String, Entry> registry = new HashMap<>();
  private final Router router;
  private final BodyHandler bodyHandler;
  /**
   * Diagram types rejected with a 503 while their routes stay registered, so that they can be
   * re-enabled at runtime, without restarting the server (see {@link #updateDisabled(JsonObject)}).
   * Replaced as a whole (never mutated) so that request handlers always see a consistent set.
   */
  private volatile SortedSet<String> disabled = Collections.emptySortedSet();

  public DiagramRegistry(Router router, BodyHandler bodyHandler) {
    this.router = router;
    this.bodyHandler = bodyHandler;
  }

  public void register(DiagramService diagramService, String... names) {
    DiagramHandler diagramHandler = new DiagramHandler(diagramService, new Caching(diagramService.getVersion()));
    for (String name : names) {
      Handler<RoutingContext> enabledCheck = createEnabledCheck(name);
      List<Route> routes = new ArrayList<>();
      routes.add(router.get("/" + name + "/:output_format/:source_encoded")
        .handler(diagramHandler.createRequestReceived(name))
        .handler(enabledCheck)
        .handler(diagramHandler.createGet(name)));
      routes.add(router.route("/" + name)
        .handler(event -> {
          if (HttpMethod.POST.equals(event.request().method())) {
            event.next();
            return;
          }
          event.fail(405, new MethodNotAllowedException(List.of("POST")));
        }));
      routes.add(router.post("/" + name)
        .handler(bodyHandler)
        .handler(diagramHandler.createRequestReceived(name))
        .handler(enabledCheck)
        .handler(diagramHandler.createPost(name)));
      routes.add(router.route("/" + name + "/:output_format")
        .handler(event -> {
          if (HttpMethod.POST.equals(event.request().method())) {
            event.next();
            return;
          }
          String outputFormat = event.pathParam("output_format");
          try {
            diagramHandler.validate(name, outputFormat);
          } catch (UnsupportedFormatException e) {
            event.fail(e);
            return;
          }
          event.fail(405, new MethodNotAllowedException(List.of("POST")));
        }));
      routes.add(router.post("/" + name + "/:output_format")
        .handler(bodyHandler)
        .handler(diagramHandler.createRequestReceived(name))
        .handler(enabledCheck)
        .handler(diagramHandler.createPost(name)));
      registry.put(name, new Entry(diagramHandler, routes));
    }
  }

  private Handler<RoutingContext> createEnabledCheck(String name) {
    return routingContext -> {
      if (isDisabled(name)) {
        routingContext.fail(new DiagramTypeDisabledException(name));
        return;
      }
      routingContext.next();
    };
  }

  /**
   * Removes a previously registered diagram type, disabling its routes.
   * Used to evict a companion service that unregistered or missed its heartbeat deadline.
   *
   * @param name the diagram type name
   * @return true if a registration was removed, false if none existed
   */
  public boolean unregister(String name) {
    Entry entry = registry.remove(name);
    if (entry == null) {
      return false;
    }
    for (Route route : entry.routes) {
      route.remove();
    }
    return true;
  }

  public boolean isRegistered(String name) {
    return registry.containsKey(name);
  }

  public DiagramHandler get(String name) {
    Entry entry = registry.get(name);
    return entry != null ? entry.handler : null;
  }

  public Set<String> names() {
    return registry.keySet();
  }

  public Map<String, String> getVersions() {
    return registry.entrySet().stream().map(registryEntry -> {
      String diagramName = registryEntry.getKey();
      String diagramVersion = registryEntry.getValue().handler.getService().getVersion();
      return Map.entry(diagramName, diagramVersion);
    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public boolean isDisabled(String name) {
    return disabled.contains(name.toLowerCase(Locale.ROOT));
  }

  public SortedSet<String> getDisabled() {
    return disabled;
  }

  /**
   * Reads the diagram types to disable from {@value #DISABLED_DIAGRAM_TYPES_KEY}, either a
   * comma-separated string (environment variable) or a JSON array (configuration file), and
   * replaces the current set. Called at startup and every time the configuration changes.
   *
   * @param config the whole configuration
   */
  public void updateDisabled(JsonObject config) {
    SortedSet<String> newDisabled = getDisabledDiagramTypes(config);
    if (newDisabled.equals(disabled)) {
      return;
    }
    disabled = newDisabled;
    if (newDisabled.isEmpty()) {
      logger.info("All diagram types are enabled");
    } else {
      logger.warn("Disabled diagram types: {}", String.join(", ", newDisabled));
    }
    // companion services can still register these names later on (see issue #1423)
    List<String> unknown = newDisabled.stream().filter(name -> !registry.containsKey(name)).collect(Collectors.toList());
    if (!unknown.isEmpty()) {
      logger.warn("{} contains diagram types that are not registered (yet): {}", DISABLED_DIAGRAM_TYPES_KEY, String.join(", ", unknown));
    }
  }

  static SortedSet<String> getDisabledDiagramTypes(JsonObject config) {
    Object value = config.getValue(DISABLED_DIAGRAM_TYPES_KEY);
    if (value == null) {
      return Collections.emptySortedSet();
    }
    Stream<?> values = value instanceof JsonArray
      ? ((JsonArray) value).stream()
      : Arrays.stream(value.toString().split(","));
    return Collections.unmodifiableSortedSet(values
      .filter(Objects::nonNull)
      .map(name -> name.toString().trim().toLowerCase(Locale.ROOT))
      .filter(name -> !name.isEmpty())
      .collect(Collectors.toCollection(TreeSet::new)));
  }
}
