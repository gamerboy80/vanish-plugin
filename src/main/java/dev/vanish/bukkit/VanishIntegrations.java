package dev.vanish.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Optional public-API hooks; releases only the visibility claims owned by this plugin. */
final class VanishIntegrations {
  private final VanishPlugin plugin;
  private final Map<UUID, Boolean> essentialsBaseline = new HashMap<UUID, Boolean>();
  private final Set<String> warned = new HashSet<String>();

  VanishIntegrations(VanishPlugin plugin) {
    this.plugin = plugin;
  }

  /** Enabled protocol translators, so the log shows which paths are active. */
  static String translators() {
    StringBuilder names = new StringBuilder();
    for (String name :
        new String[] {
          "ViaVersion", "ViaBackwards", "ViaRewind", "ProtocolSupport", "Geyser-Spigot", "floodgate"
        }) {
      Plugin translator = Bukkit.getPluginManager().getPlugin(name);
      if (translator != null && translator.isEnabled()) {
        if (names.length() > 0) {
          names.append(", ");
        }
        names.append(translator.getName());
      }
    }
    return names.toString();
  }

  void hidden(Player player, boolean hidden) {
    Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
    if (dynmap != null && dynmap.isEnabled()) {
      try {
        dynmap
            .getClass()
            .getMethod("assertPlayerInvisibility", String.class, boolean.class, String.class)
            .invoke(dynmap, player.getName(), Boolean.valueOf(hidden), plugin.getName());
      } catch (ReflectiveOperationException error) {
        warning("dynmap", error);
      }
    }

    Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
    if (essentials != null && essentials.isEnabled()) {
      try {
        Object user =
            essentials.getClass().getMethod("getUser", Player.class).invoke(essentials, player);
        if (user == null) {
          return;
        }
        if (hidden) {
          boolean prior = ((Boolean) user.getClass().getMethod("isHidden").invoke(user));
          essentialsBaseline.putIfAbsent(player.getUniqueId(), Boolean.valueOf(prior));
          user.getClass().getMethod("setHidden", boolean.class).invoke(user, Boolean.TRUE);
        } else {
          Boolean prior = essentialsBaseline.remove(player.getUniqueId());
          if (prior != null) {
            user.getClass().getMethod("setHidden", boolean.class).invoke(user, prior);
          }
        }
      } catch (ReflectiveOperationException error) {
        warning("Essentials", error);
      }
    } else if (!hidden) {
      essentialsBaseline.remove(player.getUniqueId());
    }
  }

  /**
   * Bedrock sessions whose Java player is online, or {@code -1} when Geyser is
   * absent or its API is not ready. Reflective so a server without Geyser loads
   * normally and Geyser API changes stay non-fatal.
   */
  static int bedrockPlayers() {
    Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
    if (geyser == null || !geyser.isEnabled()) {
      return -1;
    }
    try {
      Class<?> apiType = Class.forName("org.geysermc.geyser.api.GeyserApi");
      Object api = apiType.getMethod("api").invoke(null);
      if (api == null) {
        return -1;
      }
      Object connections = apiType.getMethod("onlineConnections").invoke(api);
      if (!(connections instanceof Iterable)) {
        return -1;
      }
      Class<?> connectionType = Class.forName("org.geysermc.api.connection.Connection");
      java.lang.reflect.Method javaUuid = connectionType.getMethod("javaUuid");
      int count = 0;
      for (Object connection : (Iterable<?>) connections) {
        Object uuid = javaUuid.invoke(connection);
        if (uuid instanceof UUID && Bukkit.getPlayer((UUID) uuid) != null) {
          count++;
        }
      }
      return count;
    } catch (ReflectiveOperationException error) {
      return -1;
    } catch (RuntimeException error) {
      return -1;
    } catch (LinkageError error) {
      return -1;
    }
  }

  private void warning(String name, ReflectiveOperationException error) {
    if (warned.add(name)) {
      Throwable cause =
          error instanceof InvocationTargetException
              ? ((InvocationTargetException) error).getTargetException()
              : error;
      plugin.getLogger().warning(name + " vanish integration unavailable: " + cause.getMessage());
    }
  }
}
