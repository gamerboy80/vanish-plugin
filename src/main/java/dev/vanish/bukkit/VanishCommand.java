package dev.vanish.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import dev.vanish.common.VanishOptions;
import dev.vanish.common.VanishView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Native {@code /vanish} command with explicit permissions and read-only status. */
final class VanishCommand implements CommandExecutor, TabCompleter {
  private static final List<String> ACTIONS =
      Arrays.asList("on", "off", "toggle", "status", "help", "pickup", "inspect", "chat");
  private static final List<String> ADMIN_ACTIONS =
      Arrays.asList("option", "options", "interactions", "reload", "info", "doctor");
  private static final List<String> SWITCHES =
      Arrays.asList("on", "off", "true", "false", "toggle");

  private final VanishPlugin plugin;
  private final VanishService vanish;

  VanishCommand(VanishPlugin plugin, VanishService vanish) {
    this.plugin = plugin;
    this.vanish = vanish;
  }

  @Override
  public boolean onCommand(
      CommandSender sender, Command command, String label, String[] args) {
    try {
      if (!canUse(sender)) {
        throw new IllegalArgumentException(
            plugin.settings().template(
                "permission",
                "{error}You need {permission}.",
                Collections.singletonMap("permission", VanishPlugin.PERM_USE)));
      }
      String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

      if (action.equals("help")) {
        sender.sendMessage(
            plugin.settings().message(
                "help",
                "{muted}/vanish [on|off|toggle|status] [player] | list | pickup | inspect |"
                    + " chat | options | interactions | info | doctor | reload"));
        return true;
      }
      if (action.equals("reload")) {
        requireAdmin(sender);
        plugin.reloadVanish();
        sender.sendMessage(
            plugin.settings().message("reloaded", "{success}Configuration reloaded."));
        return true;
      }
      if (action.equals("doctor")) {
        requireAdmin(sender);
        for (String line : vanish.doctor()) {
          sender.sendMessage(plugin.msg(line));
        }
        return true;
      }
      if (action.equals("info")) {
        requireAdmin(sender);
        Map<String, String> info = new LinkedHashMap<String, String>();
        info.put("version", plugin.getDescription().getVersion());
        info.put("server", Bukkit.getBukkitVersion());
        sender.sendMessage(
            plugin.msg(
                plugin.settings()
                    .format("{muted}Vanish {primary}{version} {muted}| {text}{server}", info)));
        info.put("packetevents", String.valueOf(PacketEvents.getAPI().getVersion()));
        info.put("vanished", String.valueOf(vanish.view().hidden().size()));
        info.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        sender.sendMessage(
            plugin.msg(
                plugin.settings()
                    .format(
                        "{muted}PacketEvents {accent}{packetevents} {muted}| "
                            + "{accent}{vanished} {muted}vanished / {text}{online} {muted}online",
                        info)));
        String translators = VanishIntegrations.translators();
        if (!translators.isEmpty()) {
          sender.sendMessage(
              plugin.msg("{muted}Translators: {accent}" + translators));
        }
        int bedrock = VanishIntegrations.bedrockPlayers();
        if (bedrock >= 0) {
          sender.sendMessage(
              plugin.msg("{muted}Bedrock players: {accent}" + bedrock));
        }
        return true;
      }
      if (action.equals("chat")) {
        if (!(sender instanceof Player) || args.length > 2) {
          throw new IllegalArgumentException(
              "{error}/vanish chat <private|public|toggle|status>");
        }
        vanish.chatMode(
            (Player) sender,
            args.length == 1 ? "toggle" : args[1].toLowerCase(Locale.ROOT));
        return true;
      }
      if (action.equals("interactions")) {
        requireAdmin(sender);
        if (args.length > 2) {
          throw new IllegalArgumentException(
              "{error}/vanish interactions <on|off|toggle|status>");
        }
        String mode = args.length == 1 ? "toggle" : args[1].toLowerCase(Locale.ROOT);
        boolean allowed = interactionsAllowed();
        if (mode.equals("status")) {
          sender.sendMessage(
              plugin.settings().message(
                  "interactions-status",
                  "{muted}Interactions: {state}.",
                  Collections.singletonMap(
                      "state", plugin.settings().format(allowed ? "{accent}on" : "{warning}restricted", null))));
          return true;
        }
        boolean next;
        if (mode.equals("on")) {
          next = true;
        } else if (mode.equals("off")) {
          next = false;
        } else if (mode.equals("toggle")) {
          next = !allowed;
        } else {
          throw new IllegalArgumentException(
              "{error}/vanish interactions <on|off|toggle|status>");
        }
        Map<String, Boolean> changes = new LinkedHashMap<String, Boolean>();
        for (String option : VanishOptions.INTERACTION_GUARDS) {
          changes.put(option, Boolean.valueOf(!next));
        }
        changes.put("default-item-pickup", Boolean.valueOf(next));
        changes.put("silent-container-inspection", Boolean.valueOf(!next));
        plugin.updateOptions(changes);
        vanish.resetPickupOverrides();
        sender.sendMessage(
            plugin.settings().message(
                "interactions-saved",
                "{success}Interactions: {state}.",
                Collections.singletonMap(
                    "state", plugin.settings().format(next ? "{accent}on" : "{warning}off", null))));
        return true;
      }
      if (action.equals("options")) {
        requireAdmin(sender);
        if (args.length == 1) {
          sender.sendMessage(
              plugin.settings().message("options-header", "{muted}Vanish options:"));
          for (String option : VanishOptions.names()) {
            boolean enabled = plugin.settings().flag(option);
            Map<String, String> placeholders = new LinkedHashMap<String, String>();
            placeholders.put("option", option);
            placeholders.put(
                "state",
                plugin.settings().format(enabled ? "{success}on" : "{muted}off", null));
            sender.sendMessage(
                plugin.settings().message(
                    "option-line", "{muted}  {option} {state}", placeholders));
          }
          return true;
        }
        action = "option";
      }
      if (action.equals("option")) {
        requireAdmin(sender);
        if (args.length != 3
            || !VanishOptions.known(args[1])
            || !SWITCHES.contains(args[2].toLowerCase(Locale.ROOT))) {
          throw new IllegalArgumentException(
              "{error}/vanish option <name> <on|off>; see /vanish options");
        }
        boolean value;
        if (args[2].equalsIgnoreCase("toggle")) {
          value = !plugin.settings().flag(args[1]);
        } else {
          value =
              args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
        }
        plugin.updateOption(args[1], value);
        sender.sendMessage(
            plugin.settings().message(
                "option-saved",
                "{success}Saved vanish option: {option}.",
                Collections.singletonMap("option", args[1])));
        return true;
      }
      if (action.equals("list")) {
        requireOthers(sender);
        List<String> names = new ArrayList<String>();
        for (VanishView.Identity identity : vanish.view().hidden().values()) {
          names.add(identity.name());
        }
        Collections.sort(names);
        if (names.isEmpty()) {
          sender.sendMessage(
              plugin.settings().message("list-empty", "{muted}No vanished staff online."));
        } else {
          Map<String, String> placeholders = new LinkedHashMap<String, String>();
          placeholders.put("count", Integer.toString(names.size()));
          placeholders.put("players", String.join("{muted}, {accent}", names));
          sender.sendMessage(
              plugin.settings().message(
                  "list",
                  "{muted}Vanished ({count}{muted}): {accent}{players}",
                  placeholders));
        }
        return true;
      }
      if (action.equals("pickup") || action.equals("inspect")) {
        if (args.length != 1 || !(sender instanceof Player)) {
          throw new IllegalArgumentException(
              plugin.settings().template("player-only", "{error}Run this command as a player.", null));
        }
        if (action.equals("pickup")) {
          vanish.togglePickup((Player) sender);
        } else {
          VanishListener.inspect(plugin, vanish, (Player) sender);
        }
        return true;
      }

      if (!Arrays.asList("on", "off", "toggle", "status").contains(action)) {
        if (args.length != 1) {
          throw new IllegalArgumentException(
              "{error}/vanish [on|off|toggle|status] [player]");
        }
        args = new String[] {"toggle", args[0]};
        action = "toggle";
      }
      if (args.length > 2) {
        throw new IllegalArgumentException(
            "{error}/vanish [on|off|toggle|status] [player]");
      }

      Player target;
      if (args.length == 2) {
        requireOthers(sender);
        target = Bukkit.getPlayerExact(args[1]);
      } else if (sender instanceof Player) {
        target = (Player) sender;
        if (!VanishService.permitted(target)) {
          throw new IllegalArgumentException(
              plugin.settings().template(
                  "permission",
                  "{error}You need {permission}.",
                  Collections.singletonMap("permission", VanishPlugin.PERM_USE)));
        }
      } else {
        target = null;
      }
      if (target == null) {
        throw new IllegalArgumentException(
            plugin.settings().template(
                "unknown-player", "{error}Specify an online player.", null));
      }
      if (action.equals("status")) {
        boolean hidden = vanish.isVanished(target.getUniqueId());
        sender.sendMessage(
            plugin.settings().message(
                hidden ? "status-vanished" : "status-visible",
                hidden
                    ? "{muted}{player} {text}is {accent}vanished{text}."
                    : "{muted}{player} {text}is {primary}visible{text}.",
                Collections.singletonMap("player", target.getName())));
        return true;
      }
      boolean hidden =
          action.equals("on")
              || (action.equals("toggle") && !vanish.isVanished(target.getUniqueId()));
      vanish.setVanished(target, hidden);
      if (!sender.equals(target)) {
        sender.sendMessage(
            plugin.settings().message(
                hidden ? "target-vanished" : "target-visible",
                hidden
                    ? "{muted}{player} {text}is now {accent}vanished{text}."
                    : "{muted}{player} {text}is now {primary}visible{text}.",
                Collections.singletonMap("player", target.getName())));
      }
      return true;
    } catch (IllegalArgumentException error) {
      sender.sendMessage(plugin.msg(error.getMessage()));
      return true;
    } catch (IOException error) {
      plugin.getLogger().severe("Vanish save failed: " + error.getMessage());
      sender.sendMessage(
          plugin.settings().message(
              "save-failed", "{error}Vanish save failed; contact an admin."));
      return true;
    }
  }

  private boolean interactionsAllowed() {
    for (String option : VanishOptions.INTERACTION_GUARDS) {
      if (plugin.settings().flag(option)) {
        return false;
      }
    }
    return plugin.settings().flag("default-item-pickup")
        && !plugin.settings().flag("silent-container-inspection");
  }

  private void requireAdmin(CommandSender sender) {
    if (!sender.hasPermission(VanishPlugin.PERM_ADMIN)) {
      throw new IllegalArgumentException(
          plugin.settings().template(
              "permission",
              "{error}You need {permission}.",
              Collections.singletonMap("permission", VanishPlugin.PERM_ADMIN)));
    }
  }

  private void requireOthers(CommandSender sender) {
    if (!sender.hasPermission(VanishPlugin.PERM_OTHERS)) {
      throw new IllegalArgumentException(
          plugin.settings().template(
              "permission",
              "{error}You need {permission}.",
              Collections.singletonMap("permission", VanishPlugin.PERM_OTHERS)));
    }
  }

  private static boolean canUse(CommandSender sender) {
    return sender.hasPermission(VanishPlugin.PERM_ADMIN)
        || sender.hasPermission(VanishPlugin.PERM_USE)
        || sender.hasPermission(VanishPlugin.PERM_AUTO)
        || sender.hasPermission(VanishPlugin.PERM_OTHERS);
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!canUse(sender)) {
      return Collections.emptyList();
    }
    List<String> options = new ArrayList<String>();
    if (args.length <= 1) {
      options.addAll(ACTIONS);
      if (sender.hasPermission(VanishPlugin.PERM_ADMIN)) {
        options.addAll(ADMIN_ACTIONS);
      }
      if (sender.hasPermission(VanishPlugin.PERM_OTHERS)) {
        options.add("list");
      }
    } else if (args.length == 2 && args[0].equalsIgnoreCase("interactions")) {
      if (sender.hasPermission(VanishPlugin.PERM_ADMIN)) {
        options.addAll(Arrays.asList("on", "off", "toggle", "status"));
      }
    } else if (args.length == 2 && args[0].equalsIgnoreCase("chat")) {
      options.addAll(Arrays.asList("private", "public", "toggle", "status"));
    } else if (args.length == 2
        && (args[0].equalsIgnoreCase("option") || args[0].equalsIgnoreCase("options"))
        && sender.hasPermission(VanishPlugin.PERM_ADMIN)) {
      options.addAll(VanishOptions.names());
    } else if (args.length == 3
        && args[0].equalsIgnoreCase("option")
        && sender.hasPermission(VanishPlugin.PERM_ADMIN)) {
      options.addAll(SWITCHES);
    } else if (args.length == 2
        && sender.hasPermission(VanishPlugin.PERM_OTHERS)
        && Arrays.asList("on", "off", "toggle", "status")
            .contains(args[0].toLowerCase(Locale.ROOT))) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (!(sender instanceof Player)
            || (vanish.canSee(((Player) sender).getUniqueId(), player.getUniqueId())
                && ((Player) sender).canSee(player))) {
          options.add(player.getName());
        }
      }
    }
    String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    List<String> filtered = new ArrayList<String>();
    for (String option : options) {
      if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
        filtered.add(option);
      }
    }
    Collections.sort(filtered);
    return filtered;
  }
}
