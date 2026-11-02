package gg.mcrep.verification;

import static net.kyori.adventure.text.Component.text;

import io.papermc.paper.configuration.ServerConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs the mcrep.gg Link Server. Players join, type /link, and get a Link Code to enter on the website.
 *
 * <p>The website trusts these codes to say who a Player is, so this plugin refuses to run unless Mojang has verified
 * every Player who joins (online mode, directly or through a proxy).
 */
public final class VerificationPlugin extends JavaPlugin implements Listener {
  private HttpClient http;
  private LinkCommand linkCommand;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    if (!playersAreVerified()) {
      stop("This server is in offline mode, so anyone can join with any username. Turn on online-mode in server.properties"
          + " (or proxy online mode with Velocity forwarding) before running the Link Server.");
      return;
    }
    String secret = getConfig().getString("secret", "").trim();
    if (secret.isEmpty()) {
      stop("Set secret in plugins/McrepVerification/config.yml to the website's LINK_SERVER_SECRET.");
      return;
    }
    URI apiUrl;
    try {
      apiUrl = URI.create(getConfig().getString("api-url", "https://mcrep.gg"));
    } catch (IllegalArgumentException e) {
      stop("api-url in config.yml is not a valid URL.");
      return;
    }
    if (!"https".equals(apiUrl.getScheme()) && !"http".equals(apiUrl.getScheme())) {
      stop("api-url in config.yml must start with https://.");
      return;
    }
    if ("http".equals(apiUrl.getScheme()) && !isLocal(apiUrl)) {
      getLogger().warning("api-url uses http://, so the secret is sent unencrypted. Use https:// outside local testing.");
    }

    // HTTP/1.1: over http:// the client otherwise asks to upgrade to HTTP/2, and Node servers hang up on that.
    http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    linkCommand = new LinkCommand(
        this,
        new LinkApi(http, apiUrl, secret),
        Duration.ofSeconds(Math.max(0, getConfig().getInt("cooldown-seconds", 10))),
        getConfig().getString("sign-in-url", "https://mcrep.gg/sign-in"));
    registerCommand("link", "Get a Link Code to sign in to mcrep.gg", linkCommand);
    getServer().getPluginManager().registerEvents(this, this);
    getLogger().info("Link Server ready. Players can type /link to get a Link Code from " + apiUrl + ".");
  }

  @Override
  public void onDisable() {
    if (http != null) http.shutdownNow();
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    event.getPlayer().sendMessage(text("Welcome to the mcrep.gg Link Server. Type ", NamedTextColor.GRAY)
        .append(text("/link", LinkCommand.GREEN)
            .clickEvent(ClickEvent.runCommand("/link"))
            .hoverEvent(HoverEvent.showText(text("Click to run /link"))))
        .append(text(" to get your code.", NamedTextColor.GRAY)));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    linkCommand.forget(event.getPlayer().getUniqueId());
  }

  private boolean playersAreVerified() {
    if (getServer().getOnlineMode()) return true;
    ServerConfiguration config = getServer().getServerConfig();
    return config.isProxyEnabled() && config.isProxyOnlineMode();
  }

  private static boolean isLocal(URI uri) {
    String host = uri.getHost();
    return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
  }

  private void stop(String reason) {
    getLogger().severe(reason + " Disabling.");
    getServer().getPluginManager().disablePlugin(this);
  }
}
