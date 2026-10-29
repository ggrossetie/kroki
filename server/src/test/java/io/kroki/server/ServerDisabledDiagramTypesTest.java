package io.kroki.server;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class ServerDisabledDiagramTypesTest {

  private static int getAvailablePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static HttpResponse<Buffer> post(WebClient client, int port, String path, String body) throws TimeoutException {
    return client.post(port, "localhost", path)
      .sendBuffer(Buffer.buffer(body))
      .await(5, TimeUnit.SECONDS);
  }

  private static HttpResponse<Buffer> get(WebClient client, int port, String path) throws TimeoutException {
    return client.get(port, "localhost", path)
      .send()
      .await(5, TimeUnit.SECONDS);
  }

  @Test
  void should_reject_disabled_diagram_types_with_a_503(Vertx vertx) throws IOException, TimeoutException {
    int krokiPort = getAvailablePort();
    JsonObject config = new JsonObject()
      .put("KROKI_LISTEN", "127.0.0.1:" + krokiPort)
      .put("KROKI_DISABLED_DIAGRAM_TYPES", " Graphviz, seqdiag ,");
    vertx.deployVerticle(new Server(), new DeploymentOptions().setConfig(config)).await(5, TimeUnit.SECONDS);
    WebClient client = WebClient.create(vertx);

    HttpResponse<Buffer> postResponse = post(client, krokiPort, "/graphviz/svg", "digraph { a -> b }");
    assertThat(postResponse.statusCode()).isEqualTo(503);
    assertThat(postResponse.bodyAsString()).contains("Diagram type 'graphviz' is disabled on this server.");
    assertThat(postResponse.getHeader("Cache-Control")).isNull();
    assertThat(get(client, krokiPort, "/seqdiag/svg/eNpLyUwvSizIUHBXqOZSUEiCcBQUdK0UNEDKdKwUNDRBAgDFkwbS").statusCode()).isEqualTo(503);
    // the gate also applies to the diagram_type field of the POST / endpoint
    HttpResponse<Buffer> restResponse = client.post(krokiPort, "localhost", "/")
      .sendJsonObject(new JsonObject()
        .put("diagram_source", "digraph { a -> b }")
        .put("diagram_type", "graphviz")
        .put("output_format", "svg"))
      .await(5, TimeUnit.SECONDS);
    assertThat(restResponse.statusCode()).isEqualTo(503);
    // aliases are distinct diagram types: "dot" is still enabled (rejected here only because of the invalid output format)
    assertThat(post(client, krokiPort, "/dot/foo", "digraph { a -> b }").statusCode()).isEqualTo(400);

    HttpResponse<Buffer> healthResponse = get(client, krokiPort, "/health");
    assertThat(healthResponse.statusCode()).isEqualTo(200);
    assertThat(healthResponse.bodyAsJsonObject().getString("status")).isEqualTo("pass");
    assertThat(healthResponse.bodyAsJsonObject().getJsonArray("disabled")).containsExactly("graphviz", "seqdiag");
    assertThat(get(client, krokiPort, "/").bodyAsString()).contains("(disabled)");
  }

  @Test
  void should_disable_and_re_enable_a_diagram_type_at_runtime(Vertx vertx, @TempDir Path tempDir) throws IOException, TimeoutException, InterruptedException {
    int krokiPort = getAvailablePort();
    Path configFile = tempDir.resolve("config.json");
    Files.writeString(configFile, new JsonObject()
      .put("KROKI_LISTEN", "127.0.0.1:" + krokiPort)
      .put("KROKI_DISABLED_DIAGRAM_TYPES", new JsonArray().add("graphviz"))
      .encode());
    ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
      .setScanPeriod(100)
      .addStore(new ConfigStoreOptions()
        .setType("file")
        .setConfig(new JsonObject().put("path", configFile.toString()))));
    JsonObject config = retriever.getConfig().await(5, TimeUnit.SECONDS);
    Server.start(vertx, new VertxOptions(), config, retriever).await(5, TimeUnit.SECONDS);
    WebClient client = WebClient.create(vertx);

    assertThat(post(client, krokiPort, "/graphviz/foo", "digraph { a -> b }").statusCode()).isEqualTo(503);

    Files.writeString(configFile, new JsonObject()
      .put("KROKI_LISTEN", "127.0.0.1:" + krokiPort)
      .put("KROKI_DISABLED_DIAGRAM_TYPES", new JsonArray())
      .encode());
    assertThat(awaitStatusCode(client, krokiPort, "/graphviz/foo", 400)).isEqualTo(400);

    Files.writeString(configFile, new JsonObject()
      .put("KROKI_LISTEN", "127.0.0.1:" + krokiPort)
      .put("KROKI_DISABLED_DIAGRAM_TYPES", "graphviz")
      .encode());
    assertThat(awaitStatusCode(client, krokiPort, "/graphviz/foo", 503)).isEqualTo(503);
  }

  private static int awaitStatusCode(WebClient client, int port, String path, int expected) throws TimeoutException, InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    int statusCode;
    do {
      statusCode = post(client, port, path, "digraph { a -> b }").statusCode();
      if (statusCode == expected) {
        return statusCode;
      }
      Thread.sleep(50);
    } while (System.currentTimeMillis() < deadline);
    return statusCode;
  }
}
