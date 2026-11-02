package gg.mcrep.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.mcrep.verification.LinkApiException.Reason;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the website for Link Codes: POST /api/link-codes. Only the Link Server holds the secret, because only it runs in
 * online mode and can vouch for who a Player is.
 */
final class LinkApi {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient http;
  private final URI endpoint;
  private final String secret;

  LinkApi(HttpClient http, URI apiUrl, String secret) {
    this.http = http;
    this.endpoint = apiUrl.resolve("/api/link-codes");
    this.secret = secret;
  }

  /** Completes with the code, or fails with a {@link LinkApiException} (or an IOException if the website is unreachable). */
  CompletableFuture<LinkCode> requestCode(UUID uuid, String username) {
    JsonObject body = new JsonObject();
    body.addProperty("uuid", uuid.toString());
    body.addProperty("username", username);
    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(TIMEOUT)
        .header("Authorization", "Bearer " + secret)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();
    return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(LinkApi::parse);
  }

  static LinkCode parse(HttpResponse<String> response) {
    int status = response.statusCode();
    if (status == 401) {
      throw new LinkApiException(Reason.UNAUTHORIZED, "The website rejected the secret. Check that secret in config.yml matches LINK_SERVER_SECRET.");
    }
    if (status == 503) {
      throw new LinkApiException(Reason.NOT_CONFIGURED, "The website has no LINK_SERVER_SECRET set.");
    }
    if (status != 200) {
      throw new LinkApiException(Reason.FAILED, "The website answered " + status + ": " + response.body());
    }
    try {
      JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
      return new LinkCode(json.get("code").getAsString(), Instant.parse(json.get("expiresAt").getAsString()));
    } catch (RuntimeException e) {
      throw new LinkApiException(Reason.FAILED, "The website sent a response we could not read: " + response.body());
    }
  }
}
