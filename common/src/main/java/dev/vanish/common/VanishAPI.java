package dev.vanish.common;

import java.util.UUID;

/**
 * Static access point for external plugins to query vanish state.
 * Initialized by the bukkit loader during onEnable; null-safe when
 * the Vanish plugin is not installed.
 */
public final class VanishAPI {

  /** Minimal contract the platform loader implements. */
  public interface Provider {
    boolean isVanished(UUID id);

    boolean canSee(UUID viewer, UUID subject);
  }

  private static volatile Provider provider;

  private VanishAPI() {}

  /** Called by the bukkit loader during onEnable. */
  public static void init(Provider svc) {
    provider = svc;
  }

  /** Called by the bukkit loader during onDisable. */
  public static void shutdown() {
    provider = null;
  }

  /** Whether the given player is currently vanished. */
  public static boolean isVanished(UUID id) {
    Provider p = provider;
    return p != null && p.isVanished(id);
  }

  /** Whether a viewer may see a subject. */
  public static boolean canSee(UUID viewer, UUID subject) {
    Provider p = provider;
    return p == null || p.canSee(viewer, subject);
  }

  /** Whether the Vanish plugin is loaded and its API available. */
  public static boolean isAvailable() {
    return provider != null;
  }
}
