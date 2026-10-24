package dev.vanish.bukkit.compat;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Every Bukkit method added after 1.8.8 is reached through this class. The
 * plugin compiles against the 1.8.8 API and degrades gracefully when a newer
 * method or event is absent, which is what lets one jar serve 1.8.8 through
 * 26.x instead of failing class verification on old servers.
 */
public final class Compat {
  private static final Method SET_SILENT = method(Player.class, "setSilent", boolean.class);
  private static final Method IS_SILENT = method(Player.class, "isSilent");
  private static final Method SET_COLLIDABLE =
      method(Player.class, "setCollidable", boolean.class);
  private static final Method IS_COLLIDABLE = method(Player.class, "isCollidable");
  private static final Method SET_AFFECTS_SPAWNING =
      method(Player.class, "setAffectsSpawning", boolean.class);
  private static final Method GET_AFFECTS_SPAWNING =
      method(Player.class, "getAffectsSpawning");
  private static final Method SET_GLIDING = method(Player.class, "setGliding", boolean.class);
  private static final Method HIDE_PLAYER =
      method(Player.class, "hidePlayer", Plugin.class, Player.class);
  private static final Method SHOW_PLAYER =
      method(Player.class, "showPlayer", Plugin.class, Player.class);

  private Compat() {}

  private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
    try {
      return owner.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException | SecurityException ignored) {
      return null;
    }
  }

  /** Entity silence exists on 1.9+; 1.8 has no equivalent and silently no-ops. */
  public static void setSilent(Player player, boolean silent) {
    if (SET_SILENT != null) {
      invoke(SET_SILENT, player, Boolean.valueOf(silent));
    }
  }

  public static boolean isSilent(Player player) {
    if (IS_SILENT == null) {
      return false;
    }
    Object value = invoke(IS_SILENT, player);
    return value instanceof Boolean && ((Boolean) value).booleanValue();
  }

  /** Collision toggling exists on 1.9+. */
  public static void setCollidable(Player player, boolean collidable) {
    if (SET_COLLIDABLE != null) {
      invoke(SET_COLLIDABLE, player, Boolean.valueOf(collidable));
    }
  }

  public static boolean isCollidable(Player player) {
    if (IS_COLLIDABLE == null) {
      return true;
    }
    Object value = invoke(IS_COLLIDABLE, player);
    return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
  }

  /** Paper/Spigot spawning influence; absent on vanilla 1.8. */
  public static void setAffectsSpawning(Player player, boolean affects) {
    if (SET_AFFECTS_SPAWNING != null) {
      invoke(SET_AFFECTS_SPAWNING, player, Boolean.valueOf(affects));
    }
  }

  public static boolean getAffectsSpawning(Player player) {
    if (GET_AFFECTS_SPAWNING == null) {
      return true;
    }
    Object value = invoke(GET_AFFECTS_SPAWNING, player);
    return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
  }

  /** Elytra gliding exists on 1.9+. */
  public static void setGliding(Player player, boolean gliding) {
    if (SET_GLIDING != null) {
      invoke(SET_GLIDING, player, Boolean.valueOf(gliding));
    }
  }

  /**
   * Uses the plugin-aware API on servers that have it so another plugin's hide
   * is not undone when vanish ends. 1.8.8 only has the deprecated variant.
   */
  public static void hidePlayer(Plugin plugin, Player viewer, Player subject) {
    if (HIDE_PLAYER != null) {
      invoke(HIDE_PLAYER, viewer, plugin, subject);
    } else {
      viewer.hidePlayer(subject);
    }
  }

  /** Counterpart to {@link #hidePlayer(Plugin, Player, Player)}. */
  public static void showPlayer(Plugin plugin, Player viewer, Player subject) {
    if (SHOW_PLAYER != null) {
      invoke(SHOW_PLAYER, viewer, plugin, subject);
    } else {
      viewer.showPlayer(subject);
    }
  }

  /** Parses a persisted game mode, defaulting to survival on unknown values. */
  public static GameMode gameMode(String name) {
    try {
      return GameMode.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return GameMode.SURVIVAL;
    }
  }

  /** Cross-version target block lookup (1.8 has no getTargetBlockExact). */
  public static Block targetBlock(Player player, int distance) {
    Set<Material> transparent = new HashSet<Material>();
    return player.getTargetBlock(transparent, distance);
  }

  private static Object invoke(Method method, Object target, Object... arguments) {
    try {
      return method.invoke(target, arguments);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }
}
