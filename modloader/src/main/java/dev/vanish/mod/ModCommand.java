package dev.vanish.mod;

import java.io.IOException;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared command behaviour. Each loader only registers the Brigadier literal
 * and forwards the parsed arguments here, so all three accept the same syntax.
 */
public final class ModCommand {
  private final ModVanish vanish;

  public ModCommand(ModVanish vanish) {
    this.vanish = vanish;
  }

  /**
   * Runs one command invocation.
   *
   * @return feedback for the sender, or {@code null} when the service already
   *     sent the message to that player
   */
  public String execute(ServerPlayer sender, String action, String targetName)
      throws IOException {
    if (!vanish.isStaff(sender)) {
      throw new IllegalArgumentException(
          vanish.config().template("permission", "{error}You need operator permission.", null));
    }
    if (action == null || action.isEmpty()) {
      action = "toggle";
    }
    action = action.toLowerCase(Locale.ROOT);

    if (action.equals("reload")) {
      vanish.reload();
      return vanish.config().message("reloaded", "{success}Configuration reloaded.");
    }
    if (action.equals("info")) {
      return vanish.describe();
    }
    if (action.equals("doctor")) {
      return vanish.doctor();
    }
    if (action.equals("list")) {
      java.util.List<String> names = vanish.vanishedNames();
      if (names.isEmpty()) {
        return vanish.config().message("list-empty", "{muted}No vanished staff online.");
      }
      java.util.Map<String, String> placeholders = new java.util.LinkedHashMap<String, String>();
      placeholders.put("count", Integer.toString(names.size()));
      placeholders.put("players", String.join("{muted}, {accent}", names));
      return vanish.config()
          .message(
              "list",
              "{muted}Vanished ({count}{muted}): {accent}{players}",
              placeholders);
    }
    if (action.equals("status")) {
      ServerPlayer target = targetName == null ? sender : vanish.onlinePlayer(targetName);
      if (target == null) {
        throw new IllegalArgumentException(
            vanish.config().template("unknown-player", "{error}Specify an online player.", null));
      }
      return vanish.statusText(target);
    }
    if (!action.equals("on") && !action.equals("off") && !action.equals("toggle")) {
      throw new IllegalArgumentException(
          "/vanish [on|off|toggle|status] [player] | list | info | doctor | reload");
    }

    ServerPlayer target = targetName == null ? sender : vanish.onlinePlayer(targetName);
    if (target == null) {
      throw new IllegalArgumentException(
          vanish.config().template("unknown-player", "{error}Specify an online player.", null));
    }
    boolean hidden =
        action.equals("on")
            || (action.equals("toggle") && !vanish.isVanished(target.getUUID()));
    vanish.setVanished(target, hidden);
    if (target != sender) {
      return vanish.config()
          .message(
              hidden ? "target-vanished" : "target-visible",
              hidden
                  ? "{muted}{player} {text}is now {accent}vanished{text}."
                  : "{muted}{player} {text}is now {primary}visible{text}.",
              java.util.Collections.singletonMap(
                  "player", target.getGameProfile().name()));
    }
    return null;
  }
}
