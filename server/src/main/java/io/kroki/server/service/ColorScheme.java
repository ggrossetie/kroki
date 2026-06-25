package io.kroki.server.service;

import io.kroki.server.error.BadRequestException;
import io.vertx.core.json.JsonObject;

/**
 * Unified, cross-diagram color scheme option.
 * <p>
 * Exposed to clients as the {@code color-scheme} diagram option (query param,
 * {@code Kroki-Diagram-Options-Color-Scheme} header or {@code diagram_options} JSON field),
 * with the values {@code light}, {@code dark} and {@code auto}.
 * <p>
 * The default is {@link #LIGHT} so the behavior is unchanged when the option is omitted.
 * {@code color-scheme} only provides defaults: a diagram-specific option set explicitly by
 * the caller (e.g. D2 {@code theme}/{@code dark-theme}, Mermaid {@code theme}, Graphviz
 * {@code graph-attribute-*}) always takes precedence.
 */
public enum ColorScheme {
  /** Light rendering (current default behavior). */
  LIGHT,
  /** Fixed dark rendering: dark background, light strokes and text. */
  DARK,
  /** A single SVG that adapts to the viewer via {@code @media (prefers-color-scheme: dark)}. */
  AUTO;

  public static final String OPTION_NAME = "color-scheme";

  /**
   * Reads and validates the {@code color-scheme} option.
   *
   * @param options the request options
   * @return the requested color scheme, or {@link #LIGHT} when the option is absent
   * @throws BadRequestException if the value is not one of {@code light}, {@code dark} or {@code auto}
   */
  public static ColorScheme from(JsonObject options) {
    Object value = options.getValue(OPTION_NAME);
    if (value == null) {
      return LIGHT;
    }
    String raw = value.toString().trim().toLowerCase();
    switch (raw) {
      case "":
      case "light":
        return LIGHT;
      case "dark":
        return DARK;
      case "auto":
        return AUTO;
      default:
        throw new BadRequestException("Invalid value for option '" + OPTION_NAME + "': '" + raw + "'. Allowed values are 'light', 'dark' and 'auto'.");
    }
  }
}
