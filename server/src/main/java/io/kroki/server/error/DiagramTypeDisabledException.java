package io.kroki.server.error;

/**
 * Thrown when a request targets a diagram type disabled by the operator, using
 * {@code KROKI_DISABLED_DIAGRAM_TYPES}, for instance for security or load reasons.
 */
public class DiagramTypeDisabledException extends ServiceUnavailableException {

  public DiagramTypeDisabledException(String diagramType) {
    super("Diagram type '" + diagramType + "' is disabled on this server.");
  }
}
