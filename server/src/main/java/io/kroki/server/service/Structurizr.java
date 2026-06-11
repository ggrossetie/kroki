package io.kroki.server.service;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.export.Diagram;
import com.structurizr.export.plantuml.StructurizrPlantUMLExporter;
import com.structurizr.view.*;
import io.kroki.server.decode.DiagramSource;
import io.kroki.server.decode.SourceDecoder;
import io.kroki.server.error.BadRequestException;
import io.kroki.server.error.DecodeException;
import io.kroki.server.format.FileFormat;
import io.kroki.server.security.SafeMode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Structurizr implements DiagramService {

  private final Vertx vertx;
  private final StructurizrPlantUMLExporter structurizrPlantUMLExporter;
  private final SafeMode safeMode;
  private final SourceDecoder sourceDecoder;
  private final PlantumlCommand plantumlCommand;

  // same as PlantUML since we convert Structurizr DSL to PlantUML
  private static final List<FileFormat> SUPPORTED_FORMATS = Arrays.asList(FileFormat.PNG, FileFormat.SVG, FileFormat.PDF, FileFormat.BASE64, FileFormat.TXT, FileFormat.UTXT);

  private static final List<String> THEME_BASE_URLS = Arrays.asList(
    "https://static.structurizr.com/themes/",
    "https://playground.structurizr.com/static/themes/"
  );

  // Themes bundled with their icons inlined as data URIs so that conversions do not
  // depend on network access (see ci/tasks/update-structurizr-themes.js).
  // Loaded lazily because they weigh ~30MiB.
  private static final List<String> BUNDLED_THEME_NAMES = Arrays.asList(
    "default",
    "amazon-web-services-2023.01",
    "amazon-web-services-2025.07",
    "google-cloud-platform-v1.5",
    "google-cloud-platform-2025.09",
    "kubernetes",
    "microsoft-azure-2024.07",
    "microsoft-azure-2025.11",
    "oracle-cloud-infrastructure-2021.04",
    "oracle-cloud-infrastructure-2023.04"
  );

  // When a theme cannot be fetched, fall back to the latest bundled version of the provider.
  private static final Map<String, String> PROVIDER_LATEST_THEME_NAMES = createProviderLatestThemeNames();

  private static final Map<String, StructurizrTheme> themesCache = new ConcurrentHashMap<>();

  private static Map<String, String> createProviderLatestThemeNames() {
    Map<String, String> providers = new LinkedHashMap<>();
    providers.put("amazon-web-services", "amazon-web-services-2025.07");
    providers.put("google-cloud-platform", "google-cloud-platform-2025.09");
    providers.put("microsoft-azure", "microsoft-azure-2025.11");
    providers.put("oracle-cloud-infrastructure", "oracle-cloud-infrastructure-2023.04");
    return providers;
  }

  public Structurizr(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.safeMode = SafeMode.get(config.getString("KROKI_STRUCTURIZR_SAFE_MODE", config.getString("KROKI_SAFE_MODE", "secure")), SafeMode.SECURE);
    this.structurizrPlantUMLExporter = new DataUriAwareStructurizrPlantUMLExporter();
    this.sourceDecoder = new SourceDecoder() {
      @Override
      public String decode(String encoded) throws DecodeException {
        return DiagramSource.decode(encoded);
      }
    };
    this.plantumlCommand = createPlantumlCommand(this.safeMode, config);
  }

  protected static PlantumlCommand createPlantumlCommand(SafeMode safeMode, JsonObject config) {
    JsonObject plantumlConfig = config.copy();
    String plantumlSecurityProfile = plantumlConfig.getString("KROKI_PLANTUML_SECURITY_PROFILE");
    if (plantumlSecurityProfile == null && safeMode.value >= SafeMode.SECURE.value) {
      plantumlConfig.put("KROKI_PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
    }
    return new PlantumlCommand(safeMode, plantumlConfig);
  }

  @Override
  public List<FileFormat> getSupportedFormats() {
    return SUPPORTED_FORMATS;
  }

  @Override
  public SourceDecoder getSourceDecoder() {
    return sourceDecoder;
  }

  @Override
  public String getVersion() {
    return "6.2.1";
  }

  @Override
  public Future<Buffer> convert(String sourceDecoded, String serviceName, FileFormat fileFormat, JsonObject options) {
    return vertx.executeBlocking(() -> Buffer.buffer(convert(sourceDecoded, fileFormat, options)));
  }

  static byte[] convert(
    String source,
    FileFormat fileFormat,
    PlantumlCommand plantumlCommand,
    StructurizrPlantUMLExporter structurizrPlantUMLExporter,
    SafeMode safeMode,
    JsonObject options
  ) throws IOException, InterruptedException {
    StructurizrDslParser parser = new StructurizrDslParser();
    try {
      boolean restricted =  safeMode != SafeMode.UNSAFE;
      setRestricted(parser, restricted);
      if (!restricted) {
        parser.getHttpClient().allow(".*");
      }
      parser.parse(source);
      ViewSet viewSet = parser.getWorkspace().getViews();
      Collection<View> views = viewSet.getViews();
      if (views.isEmpty()) {
        throw new BadRequestException("Empty diagram, does not have any view.");
      }
      View selectedView;
      String viewKey = options.getString("view-key");
      if (viewKey != null && !viewKey.trim().isEmpty()) {
        Optional<View> viewFound = views.stream().filter(view -> Objects.equals(view.getKey(), viewKey)).findFirst();
        if (viewFound.isEmpty()) {
          throw new BadRequestException("Unable to find view for key: " + viewKey + ".");
        }
        selectedView = viewFound.get();
      } else {
        // take the first view if not specified
        selectedView = views.iterator().next();
      }
      for (String url : viewSet.getConfiguration().getThemes()) {
        if (url.equalsIgnoreCase("default")) {
          applyTheme(viewSet, getBundledTheme("default"));
        } else if (THEME_BASE_URLS.stream().anyMatch(url::startsWith)) {
          StructurizrTheme theme = getThemeContent(url);
          if (theme != null) {
            applyTheme(viewSet, theme);
          }
        }
      }
      final Diagram diagram;
      if (selectedView instanceof DynamicView) {
        diagram = structurizrPlantUMLExporter.export((DynamicView) selectedView);
      } else if (selectedView instanceof DeploymentView) {
        diagram = structurizrPlantUMLExporter.export((DeploymentView) selectedView);
      } else if (selectedView instanceof ComponentView) {
        diagram = structurizrPlantUMLExporter.export((ComponentView) selectedView);
      } else if (selectedView instanceof ContainerView) {
        diagram = structurizrPlantUMLExporter.export((ContainerView) selectedView);
      } else if (selectedView instanceof SystemContextView) {
        diagram = structurizrPlantUMLExporter.export((SystemContextView) selectedView);
      } else if (selectedView instanceof SystemLandscapeView) {
        diagram = structurizrPlantUMLExporter.export((SystemLandscapeView) selectedView);
      } else {
        throw new BadRequestException("View type is not supported: " + selectedView.getClass().getSimpleName() + ", must be a DynamicView, DeploymentView, ComponentView, ContainerView, SystemContextView or SystemLandscapeView.");
      }

      String outputOption = options.getString("output");
      if (outputOption != null) {
        outputOption = outputOption.trim();
      }

      String diagramPlantUML;
      if (outputOption == null || outputOption.equals("diagram")) {
        diagramPlantUML = diagram.getDefinition();
      } else if (outputOption.equals("legend")) {
        diagramPlantUML = diagram.getLegend().getDefinition();
      } else {
        throw new BadRequestException("Unknown output option: " + outputOption);
      }

      return plantumlCommand.convert(diagramPlantUML, fileFormat, new JsonObject());
    } catch (StructurizrDslParserException e) {
      String cause = e.getMessage();
      final String message;
      if (cause != null && !cause.trim().isEmpty()) {
        message = "Unable to parse the Structurizr DSL. " + cause + ".";
      } else {
        message = "Unable to parse the Structurizr DSL.";
      }
      throw new BadRequestException(message, e);
    }
  }

  private static void setRestricted(StructurizrDslParser parser, boolean restricted) {
    Features features = parser.getFeatures();
    features.configure(Features.ENVIRONMENT, !restricted);
    features.configure(Features.FILE_SYSTEM, !restricted);
    features.configure(Features.PLUGINS, !restricted);
    features.configure(Features.SCRIPTS, !restricted);
    features.configure(Features.COMPONENT_FINDER, !restricted);
    features.configure(Features.DOCUMENTATION, !restricted);
    features.configure(Features.DECISIONS, !restricted);
  }

  private byte[] convert(String source, FileFormat fileFormat, JsonObject options) throws IOException, InterruptedException {
    return convert(source, fileFormat, this.plantumlCommand, this.structurizrPlantUMLExporter, this.safeMode, options);
  }

  private static void applyTheme(ViewSet viewSet, StructurizrTheme theme) {
    List<ElementStyle> elementStyles = theme.getElementStyles();
    for (ElementStyle elementStyle : elementStyles) {
      String tag = elementStyle.getTag();
      ElementStyle currentElementStyle = viewSet.getConfiguration().getStyles().getElementStyle(tag);
      if (currentElementStyle == null) {
        viewSet.getConfiguration().getStyles().add(elementStyle);
      }
    }
    List<RelationshipStyle> relationshipStyles = theme.getRelationshipStyle();
    for (RelationshipStyle relationshipStyle : relationshipStyles) {
      String tag = relationshipStyle.getTag();
      ElementStyle currentRelationshipStyle = viewSet.getConfiguration().getStyles().getElementStyle(tag);
      if (currentRelationshipStyle == null) {
        viewSet.getConfiguration().getStyles().add(relationshipStyle);
      }
    }
  }

  private static StructurizrTheme getBundledTheme(String name) {
    return themesCache.computeIfAbsent(name, key -> readTheme("structurizr/" + key + ".json"));
  }

  private static StructurizrTheme getThemeContent(String url) {
    for (String name : BUNDLED_THEME_NAMES) {
      if (url.contains(name)) {
        return getBundledTheme(name);
      }
    }
    // version not bundled: fall back to the latest bundled version of the provider
    for (Map.Entry<String, String> provider : PROVIDER_LATEST_THEME_NAMES.entrySet()) {
      if (url.contains(provider.getKey())) {
        return getBundledTheme(provider.getValue());
      }
    }
    return null;
  }

  private static StructurizrTheme readTheme(String resource) {
    InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
    try {
      if (input == null) {
        throw new IOException("Unable to get resource: " + resource);
      }
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
        String content = buffer.lines().collect(Collectors.joining("\n"));
        try {
          JsonObject object = (JsonObject) Json.decodeValue(content);
          return new StructurizrTheme(object);
        } catch (io.vertx.core.json.DecodeException e) {
          throw new RuntimeException("Unable to initialize the Structurizr service", e);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize the Structurizr service", e);
    }
  }
}

class StructurizrTheme {

  private final List<ElementStyle> elementStyles;

  public StructurizrTheme(JsonObject object) {
    this.elementStyles = new ArrayList<>();
    Object elementsObject = object.getValue("elements");
    if (elementsObject instanceof JsonArray) {
      for (Object elementObject : ((JsonArray) elementsObject).getList()) {
        if (elementObject instanceof Map) {
          @SuppressWarnings("unchecked")
          JsonObject element = new JsonObject((Map<String, Object>) elementObject);
          ElementStyle elementStyle = new ElementStyle(
            element.getString("tag"),
            element.getInteger("width"),
            element.getInteger("height"),
            element.getString("background", "#FFFFFF"), // remind: cannot pass a null value
            element.getString("color", "#000000"), // remind: cannot pass a null value
            element.getInteger("fontSize")
          );
          elementStyle.setBorder(getBorder(element));
          elementStyle.setStroke(element.getString("stroke", "#000000")); // remind: cannot pass a null value
          elementStyle.setShape(getShape(element));
          elementStyle.setIcon(element.getString("icon"));
          elementStyle.setOpacity(element.getInteger("opacity"));
          elementStyle.setMetadata(element.getBoolean("metadata"));
          elementStyle.setDescription(element.getBoolean("description"));
          this.elementStyles.add(elementStyle);
        }
      }
    }
  }

  public List<ElementStyle> getElementStyles() {
    return elementStyles;
  }

  public List<RelationshipStyle> getRelationshipStyle() {
    // remind: RelationshipStyle does not have a public constructor, as a result, we cannot instantiate it.
    return new ArrayList<>();
  }

  private Shape getShape(JsonObject element) {
    String shapeValue = element.getString("shape");
    if (shapeValue == null) {
      return null;
    }
    try {
      return Shape.valueOf(shapeValue);
    } catch (IllegalArgumentException e) {
      // ignore!
      return null;
    }
  }

  private Border getBorder(JsonObject element) {
    String borderValue = element.getString("border");
    if (borderValue == null) {
      return null;
    }
    try {
      return Border.valueOf(borderValue);
    } catch (IllegalArgumentException e) {
      // ignore!
      return null;
    }
  }
}

/**
 * The upstream exporter only emits icons that start with "http" and downloads them
 * to compute their scale. The themes bundled with Kroki inline icons as data URIs,
 * so accept them and read the image dimensions from the decoded bytes instead.
 */
class DataUriAwareStructurizrPlantUMLExporter extends StructurizrPlantUMLExporter {

  private static final String DATA_IMAGE_PREFIX = "data:image/";

  @Override
  protected boolean isSupportedIcon(String icon) {
    return super.isSupportedIcon(icon) || (icon != null && icon.startsWith(DATA_IMAGE_PREFIX));
  }

  @Override
  protected double calculateIconScale(String icon, int maxIconSize) {
    if (icon != null && icon.startsWith(DATA_IMAGE_PREFIX)) {
      try {
        String base64 = icon.substring(icon.indexOf(',') + 1);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        if (image != null) {
          return ((double) maxIconSize) / Math.max(image.getWidth(), image.getHeight());
        }
      } catch (RuntimeException | IOException e) {
        // ignore: use the default scale
      }
      return 0.5;
    }
    return super.calculateIconScale(icon, maxIconSize);
  }
}
