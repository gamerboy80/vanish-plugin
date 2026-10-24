package dev.vanish.bukkit;

import java.util.UUID;
import java.util.logging.Logger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI expansion. Registered when PlaceholderAPI is present
 * on the server; otherwise the class loads normally but performs no work.
 */
final class VanishPlaceholders {

  private VanishPlaceholders() {}

  /** Registers the expansion if PlaceholderAPI is enabled, failing soft otherwise. */
  static void register(VanishPlugin plugin, VanishService service) {
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return;
    }
    try {
      new Expansion(plugin, service).register();
      plugin.getLogger().info("PlaceholderAPI expansion registered.");
    } catch (RuntimeException | LinkageError error) {
      plugin.getLogger().warning("PlaceholderAPI hook failed: " + error.getMessage());
    }
  }

  private static final class Expansion extends PlaceholderExpansion {
    private final VanishPlugin plugin;
    private final VanishService service;

    Expansion(VanishPlugin plugin, VanishService service) {
      this.plugin = plugin;
      this.service = service;
    }

    @Override
    public String getIdentifier() {
      return "vanish";
    }

    @Override
    public String getAuthor() {
      return String.join(", ", "CrimsonWarpedcraft");
    }

    @Override
    public String getVersion() {
      return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
      return true;
    }

    @Override
    public String onPlaceholderRequest(Player viewer, String identifier) {
      if (viewer == null) {
        return "";
      }
      UUID viewerId = viewer.getUniqueId();
      if (identifier.equals("visible_player_count")) {
        int visible = 0;
        for (Player subject : Bukkit.getOnlinePlayers()) {
          if (!viewer.equals(subject) && service.canSee(viewerId, subject.getUniqueId())) {
            visible++;
          }
        }
        return Integer.toString(visible);
      }
      if (identifier.equals("visible_players")) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (Player subject : Bukkit.getOnlinePlayers()) {
          if (!viewer.equals(subject) && service.canSee(viewerId, subject.getUniqueId())) {
            names.add(subject.getName());
          }
        }
        java.util.Collections.sort(names);
        return String.join(", ", names);
      }
      return null;
    }
  }
}
