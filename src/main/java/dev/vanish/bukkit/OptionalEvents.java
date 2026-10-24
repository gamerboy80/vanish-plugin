package dev.vanish.bukkit;

import java.lang.reflect.Method;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Events that only exist on newer servers.
 *
 * <p>Registering these by name keeps the plugin loadable on 1.8.8, where the
 * event classes and their method signatures do not exist. Each registration
 * fails soft: a missing class or method simply means that specific guard is not
 * available on that server version.
 */
final class OptionalEvents {
  private interface Handler {
    void handle(Event event) throws ReflectiveOperationException;
  }

  private OptionalEvents() {}

  static void register(final VanishPlugin plugin, final VanishService vanish) {
    final Listener listener = new Listener() {};

    register(
        plugin,
        listener,
        "org.bukkit.event.entity.EntityToggleGlideEvent",
        new Handler() {
          @Override
          public void handle(Event event) throws ReflectiveOperationException {
            if (!plugin.settings().flag("prevent-gliding")) {
              return;
            }
            Object entity = invoke(event, "getEntity");
            Object gliding = invoke(event, "isGliding");
            if (entity instanceof org.bukkit.entity.Player
                && Boolean.TRUE.equals(gliding)
                && vanish.isVanished(((org.bukkit.entity.Player) entity).getUniqueId())) {
              setCancelled(event);
            }
          }
        });

    register(
        plugin,
        listener,
        "org.bukkit.event.raid.RaidTriggerEvent",
        new Handler() {
          @Override
          public void handle(Event event) throws ReflectiveOperationException {
            if (!plugin.settings().flag("prevent-raids")) {
              return;
            }
            Object player = invoke(event, "getPlayer");
            if (player instanceof org.bukkit.entity.Player
                && vanish.isVanished(((org.bukkit.entity.Player) player).getUniqueId())) {
              setCancelled(event);
            }
          }
        });

    register(
        plugin,
        listener,
        "org.bukkit.event.block.BlockReceiveGameEvent",
        new Handler() {
          @Override
          public void handle(Event event) throws ReflectiveOperationException {
            cancelForHiddenEntity(event, plugin, vanish, "prevent-vibrations");
          }
        });

    register(
        plugin,
        listener,
        "org.bukkit.event.world.GenericGameEvent",
        new Handler() {
          @Override
          public void handle(Event event) throws ReflectiveOperationException {
            cancelForHiddenEntity(event, plugin, vanish, "prevent-vibrations");
          }
        });

    register(
        plugin,
        listener,
        "com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent",
        new Handler() {
          @Override
          public void handle(Event event) throws ReflectiveOperationException {
            if (!plugin.settings().flag("prevent-xp-pickup")) {
              return;
            }
            Object player = invoke(event, "getPlayer");
            if (player instanceof org.bukkit.entity.Player
                && vanish.isVanished(((org.bukkit.entity.Player) player).getUniqueId())) {
              setCancelled(event);
            }
          }
        });
  }

  private static void cancelForHiddenEntity(
      Event event, VanishPlugin plugin, VanishService vanish, String option)
      throws ReflectiveOperationException {
    if (!plugin.settings().flag(option)) {
      return;
    }
    Object entity = invoke(event, "getEntity");
    if (entity instanceof org.bukkit.entity.Player
        && vanish.isVanished(((org.bukkit.entity.Player) entity).getUniqueId())) {
      setCancelled(event);
    }
  }

  private static void register(
      JavaPlugin plugin, Listener listener, String className, final Handler handler) {
    Class<?> type;
    try {
      type = Class.forName(className);
    } catch (ClassNotFoundException ignored) {
      return;
    }
    if (!Event.class.isAssignableFrom(type)) {
      return;
    }
    final Method setCancelled = method(type, "setCancelled", boolean.class);
    final Method getEntity = method(type, "getEntity");
    final Method getPlayer = method(type, "getPlayer");
    final Method isGliding = method(type, "isGliding");
    if (setCancelled == null || (getEntity == null && getPlayer == null)) {
      return;
    }
    // Keep reflection handles out of the handler's hot path.
    plugin.getServer().getPluginManager().registerEvent(
        type.asSubclass(Event.class),
        listener,
        EventPriority.HIGHEST,
        new EventExecutor() {
          @Override
          public void execute(Listener ignored, Event event) throws EventException {
            try {
              handler.handle(event);
            } catch (ReflectiveOperationException error) {
              throw new EventException(error);
            }
          }
        },
        plugin);
  }

  /** Adjusts the player count on Paper forks that expose the setter. */
  static void setPingPlayers(ServerListPingEvent event, int count) {
    try {
      // Paper's event subclass exposes setNumPlayers; 1.8 Spigot does not.
      event.getClass().getMethod("setNumPlayers", int.class).invoke(event, Integer.valueOf(count));
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // Spigot 1.8 has no setter; the MOTD still hides the vanished names.
    }
  }

  private static void setCancelled(Event event) throws ReflectiveOperationException {
    Method method = event.getClass().getMethod("setCancelled", boolean.class);
    method.invoke(event, Boolean.TRUE);
  }

  private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
    try {
      return owner.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException | SecurityException ignored) {
      return null;
    }
  }

  private static Object invoke(Event event, String name) throws ReflectiveOperationException {
    return event.getClass().getMethod(name).invoke(event);
  }
}
