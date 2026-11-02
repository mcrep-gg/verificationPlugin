package gg.mcrep.verification;

import static net.kyori.adventure.text.Component.text;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** /link: gets a Link Code from the website and shows it to the Player in chat. */
final class LinkCommand implements BasicCommand {
  static final TextColor GREEN = TextColor.color(0x5BE38C);

  private final Plugin plugin;
  private final LinkApi api;
  private final Duration cooldown;
  private final String signInUrl;
  private final Map<UUID, Instant> lastRequest = new ConcurrentHashMap<>();
  private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

  LinkCommand(Plugin plugin, LinkApi api, Duration cooldown, String signInUrl) {
    this.plugin = plugin;
    this.api = api;
    this.cooldown = cooldown;
    this.signInUrl = signInUrl;
  }

  @Override
  public void execute(CommandSourceStack source, String[] args) {
    if (!(source.getSender() instanceof Player player)) {
      source.getSender().sendMessage(text("Only Players can get a Link Code.", NamedTextColor.RED));
      return;
    }
    UUID uuid = player.getUniqueId();
    Instant now = Instant.now();
    Instant last = lastRequest.get(uuid);
    if (last != null && now.isBefore(last.plus(cooldown))) {
      long wait = Math.max(1, Duration.between(now, last.plus(cooldown)).toSeconds());
      player.sendMessage(text("Wait " + wait + "s before asking for another code.", NamedTextColor.GRAY));
      return;
    }
    // One request at a time per Player.
    if (!pending.add(uuid)) return;
    lastRequest.put(uuid, now);

    player.sendMessage(text("Getting your Link Code...", NamedTextColor.GRAY));
    api.requestCode(uuid, player.getName()).whenComplete((code, error) -> {
      // The answer arrives on an HTTP thread; talk to the Player on their own thread.
      player.getScheduler().run(plugin, task -> {
        pending.remove(uuid);
        if (error == null) sendCode(player, code);
        else fail(player, error);
      }, () -> pending.remove(uuid));
    });
  }

  /** Forgets a Player who left. */
  void forget(UUID uuid) {
    lastRequest.remove(uuid);
  }

  private void sendCode(Player player, LinkCode code) {
    long minutes = Math.max(1, (Duration.between(Instant.now(), code.expiresAt()).toSeconds() + 59) / 60);
    Component codeText = text(code.code(), GREEN, TextDecoration.BOLD)
        .clickEvent(ClickEvent.copyToClipboard(code.code()))
        .hoverEvent(HoverEvent.showText(text("Click to copy")));
    Component link = text(signInUrl.replaceFirst("^https?://", ""), GREEN, TextDecoration.UNDERLINED)
        .clickEvent(ClickEvent.openUrl(signInUrl))
        .hoverEvent(HoverEvent.showText(text("Open the sign-in page")));

    player.sendMessage(Component.empty());
    player.sendMessage(text("Your Link Code: ", NamedTextColor.WHITE).append(codeText).append(text("  (click to copy)", NamedTextColor.DARK_GRAY)));
    player.sendMessage(text("Enter it at ", NamedTextColor.GRAY).append(link).append(text(" within " + minutes + (minutes == 1 ? " minute." : " minutes."), NamedTextColor.GRAY)));
    player.sendMessage(text("Never share it. Anyone with this code can sign in as you.", NamedTextColor.GRAY));
    player.sendMessage(Component.empty());
  }

  private void fail(Player player, Throwable error) {
    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof LinkApiException e && e.reason() != LinkApiException.Reason.FAILED) {
      plugin.getLogger().severe(e.getMessage());
    } else {
      plugin.getLogger().log(Level.WARNING, "Could not get a Link Code for " + player.getName() + ": " + cause.getMessage());
    }
    // Let them try again straight away; the failure was not their fault.
    lastRequest.remove(player.getUniqueId());
    player.sendMessage(text("Could not get a Link Code right now. Try again in a minute.", NamedTextColor.RED));
  }
}
