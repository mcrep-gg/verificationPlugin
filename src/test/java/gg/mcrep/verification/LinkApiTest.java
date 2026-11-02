package gg.mcrep.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs LinkApi against a stand-in for the website's POST /api/link-codes. */
class LinkApiTest {
  private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

  private HttpServer server;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private final AtomicReference<String> seenPath = new AtomicReference<>();
  private final AtomicReference<String> seenAuth = new AtomicReference<>();
  private final AtomicReference<String> seenBody = new AtomicReference<>();
  private int status;
  private String responseBody;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      seenPath.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
      seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    http.shutdownNow();
  }

  private LinkApi api() {
    return new LinkApi(http, URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "s3cret");
  }

  private void respond(int status, String body) {
    this.status = status;
    this.responseBody = body;
  }

  @Test
  void sendsThePlayerAndSecretAndReturnsTheCode() {
    respond(200, "{\"code\":\"ABC234\",\"expiresAt\":\"2026-11-02T12:05:00.000Z\"}");

    LinkCode code = api().requestCode(NOTCH, "Notch").join();

    assertEquals(new LinkCode("ABC234", Instant.parse("2026-11-02T12:05:00Z")), code);
    assertEquals("POST /api/link-codes", seenPath.get());
    assertEquals("Bearer s3cret", seenAuth.get());
    JsonObject body = JsonParser.parseString(seenBody.get()).getAsJsonObject();
    assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", body.get("uuid").getAsString());
    assertEquals("Notch", body.get("username").getAsString());
  }

  @Test
  void wrongSecretIsUnauthorized() {
    respond(401, "{\"error\":\"Unauthorized\"}");
    assertEquals(LinkApiException.Reason.UNAUTHORIZED, failure().reason());
  }

  @Test
  void websiteWithoutSecretIsNotConfigured() {
    respond(503, "{\"error\":\"Link Server is not configured\"}");
    assertEquals(LinkApiException.Reason.NOT_CONFIGURED, failure().reason());
  }

  @Test
  void otherErrorsFail() {
    respond(500, "Internal Server Error");
    assertEquals(LinkApiException.Reason.FAILED, failure().reason());
  }

  @Test
  void unreadableResponseFails() {
    respond(200, "<html>maintenance</html>");
    assertEquals(LinkApiException.Reason.FAILED, failure().reason());
  }

  @Test
  void unreachableWebsiteFailsWithIoError() {
    server.stop(0);
    CompletionException e = assertThrows(CompletionException.class, () -> api().requestCode(NOTCH, "Notch").join());
    assertInstanceOf(IOException.class, e.getCause());
  }

  private LinkApiException failure() {
    CompletionException e = assertThrows(CompletionException.class, () -> api().requestCode(NOTCH, "Notch").join());
    return assertInstanceOf(LinkApiException.class, e.getCause());
  }
}
