package dev.vanish.mod;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adds or removes one viewer from the server's entity tracker. Using the
 * tracker, rather than hand-built packets, sends the correct spawn, metadata,
 * equipment and tab-list bundle and keeps later movement updates flowing.
 * A packet fallback keeps visibility correct if the tracker internals change.
 */
final class ModVisibility {
  private static final String TRACKED_ENTITY = "net.minecraft.server.level.ChunkMap$TrackedEntity";
  private static final String PLAYER_DESCRIPTOR =
      "(Lnet/minecraft/server/level/ServerPlayer;)V";

  private final ModPlatform platform;
  private final Logger logger;
  private Field entityMapField;
  private Method addPairing;
  private Method removePlayer;
  private boolean warned;

  ModVisibility(ModPlatform platform) {
    this.platform = platform;
    this.logger = platform.logger();
  }

  /** Makes a subject visible or invisible to one viewer. */
  void apply(ServerPlayer viewer, ServerPlayer subject, boolean visible) {
    if (tracker(viewer, subject, visible)) {
      return;
    }
    if (!warned) {
      warned = true;
      logger.log(
          Level.WARNING,
          "Entity tracker unavailable; using the packet visibility fallback. "
              + "Vanished players stay protected, but equipment may lag on reveal.");
    }
    if (visible) {
      viewer.connection.send(
          ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(subject)));
      viewer.connection.send(
          new ClientboundAddEntityPacket(subject, 0, subject.blockPosition()));
    } else {
      viewer.connection.send(new ClientboundRemoveEntitiesPacket(subject.getId()));
      viewer.connection.send(
          new ClientboundPlayerInfoRemovePacket(List.of(subject.getUUID())));
    }
  }

  private boolean tracker(ServerPlayer viewer, ServerPlayer subject, boolean visible) {
    try {
      ChunkMap map = subject.level().getChunkSource().chunkMap;
      if (entityMapField == null) {
        entityMapField =
            ChunkMap.class.getDeclaredField(
                platform.mapFieldName(
                    "net.minecraft.server.level.ChunkMap",
                    "entityMap",
                    "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;"));
        entityMapField.setAccessible(true);
      }
      Object tracked = ((Int2ObjectMap<?>) entityMapField.get(map)).get(subject.getId());
      if (tracked == null) {
        return false;
      }
      Method method = visible ? addPairing(tracked) : removePlayer(tracked);
      method.invoke(tracked, viewer);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
      if (!warned) {
        logger.log(Level.FINE, "Cannot use the entity tracker", error);
      }
      return false;
    }
  }

  private Method addPairing(Object tracked) throws NoSuchMethodException {
    if (addPairing == null) {
      addPairing =
          tracked
              .getClass()
              .getMethod(
                  platform.mapMethodName(TRACKED_ENTITY, "addPairing", PLAYER_DESCRIPTOR),
                  ServerPlayer.class);
      addPairing.setAccessible(true);
    }
    return addPairing;
  }

  private Method removePlayer(Object tracked) throws NoSuchMethodException {
    if (removePlayer == null) {
      removePlayer =
          tracked
              .getClass()
              .getMethod(
                  platform.mapMethodName(TRACKED_ENTITY, "removePlayer", PLAYER_DESCRIPTOR),
                  ServerPlayer.class);
      removePlayer.setAccessible(true);
    }
    return removePlayer;
  }
}
