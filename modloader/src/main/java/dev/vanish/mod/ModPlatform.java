package dev.vanish.mod;

import java.nio.file.Path;
import java.util.logging.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * The only surface a mod loader supplies. Everything below this interface is
 * shared by Fabric, Forge and NeoForge, so the vanish policy has one
 * implementation and one behaviour.
 */
public interface ModPlatform {
  /** Human-readable loader name used in log lines. */
  String name();

  /** The running integrated/dedicated server. */
  MinecraftServer server();

  /** Directory for config.json and vanish.json. */
  Path dataDirectory();

  Logger logger();

  /** Sends a system message to one player. */
  void send(ServerPlayer player, Component message);

  /**
   * Modded servers have no permission nodes; operators (command level 2) are
   * the staff group that may vanish and see vanished players.
   */
  default boolean isStaff(ServerPlayer player) {
    return player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
  }

  /**
   * Translates an official-mapping field name to the runtime name. Forge and
   * NeoForge run under Mojang names and keep the default. Fabric resolves the
   * intermediary name so reflection works in a production Fabric server.
   */
  default String mapFieldName(String officialClass, String field, String descriptor) {
    return field;
  }

  /** Translates an official-mapping method name to the runtime name. */
  default String mapMethodName(String officialClass, String method, String descriptor) {
    return method;
  }
}
