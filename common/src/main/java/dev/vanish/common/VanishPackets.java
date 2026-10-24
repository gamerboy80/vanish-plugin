package dev.vanish.common;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDamageEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recipient-specific packet firewall with no server-API calls from Netty
 * threads. The same listener serves every loader: Spigot, Paper and future
 * platforms only translate their player state into {@link VanishView}.
 */
public final class VanishPackets extends PacketListenerAbstract {
  private final Supplier<VanishView> policy;
  private final BooleanSupplier clientVision;
  private final VanishActions actions;
  private final Logger logger;
  private final AtomicBoolean warned = new AtomicBoolean();

  // User identity binds each cache to one connection, even when a UUID reconnects.
  // Weak keys also release late writes from a channel that has already disconnected.
  private final Map<User, VanishScoreboard> boards =
      Collections.synchronizedMap(new WeakHashMap<User, VanishScoreboard>());
  private final Map<User, Map<Integer, WrapperPlayServerMapData>> maps =
      Collections.synchronizedMap(
          new WeakHashMap<User, Map<Integer, WrapperPlayServerMapData>>());

  public VanishPackets(
      Supplier<VanishView> policy,
      BooleanSupplier clientVision,
      VanishActions actions,
      Logger logger) {
    super(PacketListenerPriority.HIGHEST);
    this.policy = policy;
    this.clientVision = clientVision;
    this.actions = actions;
    this.logger = logger;
  }

  /** Sends client-only night vision through PacketEvents, without a Bukkit effect. */
  public static boolean nightVision(
      User user,
      int entityId,
      boolean enabled,
      int duration,
      int amplifier,
      boolean ambient,
      boolean particles,
      boolean icon) {
    if (user == null || user.getEncoderState() != ConnectionState.PLAY) {
      return false;
    }
    if (enabled) {
      user.sendPacketSilently(
          new WrapperPlayServerEntityEffect(
              entityId,
              com.github.retrooper.packetevents.protocol.potion.PotionTypes.NIGHT_VISION,
              amplifier,
              duration,
              effectFlags(ambient, particles, icon)));
    } else {
      user.sendPacketSilently(
          new WrapperPlayServerRemoveEntityEffect(
              entityId,
              com.github.retrooper.packetevents.protocol.potion.PotionTypes.NIGHT_VISION));
      if (duration > 0) {
        user.sendPacketSilently(
            new WrapperPlayServerEntityEffect(
                entityId,
                com.github.retrooper.packetevents.protocol.potion.PotionTypes.NIGHT_VISION,
                amplifier,
                duration,
                effectFlags(ambient, particles, icon)));
      }
    }
    return true;
  }

  private static byte effectFlags(boolean ambient, boolean particles, boolean icon) {
    return (byte)
        ((ambient ? 1 : 0) | (particles ? 2 : 0) | (icon ? 4 : 0));
  }

  /** Drops cached scoreboard/map state for a disconnecting connection. */
  public void forget(UUID player) {
    synchronized (boards) {
      for (User user : new ArrayList<User>(boards.keySet())) {
        if (player.equals(user.getUUID())) {
          boards.remove(user);
        }
      }
    }
    synchronized (maps) {
      for (User user : new ArrayList<User>(maps.keySet())) {
        if (player.equals(user.getUUID())) {
          maps.remove(user);
        }
      }
    }
  }

  /** Replays cached projections after a visibility change. */
  public void refresh(UUID viewer, User user) {
    if (user == null) {
      return;
    }
    try {
      com.github.retrooper.packetevents.PacketEvents.getAPI()
          .getNettyManager()
          .getChannelOperator()
          .runInEventLoop(
              user.getChannel(),
              new Runnable() {
                @Override
                public void run() {
                  try {
                    VanishView current = policy.get();
                    Map<Integer, WrapperPlayServerMapData> knownMaps = maps.get(user);
                    if (knownMaps != null) {
                      for (WrapperPlayServerMapData map : knownMaps.values()) {
                        user.sendPacketSilently(projectMap(map, current, viewer));
                      }
                    }
                    VanishScoreboard board = boards.get(user);
                    if (board != null) {
                      for (PacketWrapper<?> packet : board.projection(current, viewer)) {
                        // Already projected: never cache our own filtered state as original.
                        user.sendPacketSilently(packet);
                      }
                    }
                  } catch (RuntimeException error) {
                    failClosed(user, error);
                  } catch (LinkageError error) {
                    failClosed(user, error);
                  }
                }
              });
    } catch (RuntimeException error) {
      failClosed(user, error);
    } catch (LinkageError error) {
      failClosed(user, error);
    }
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    try {
      filter(event);
    } catch (RuntimeException error) {
      // Disconnect also removes cached rows that could no longer be filtered.
      event.setCancelled(true);
      failClosed(event.getUser(), error);
    } catch (LinkageError error) {
      event.setCancelled(true);
      failClosed(event.getUser(), error);
    }
  }

  private void failClosed(User user, Throwable error) {
    if (warned.compareAndSet(false, true)) {
      logger.log(Level.SEVERE, "Vanish packet filtering failed closed", error);
    }
    user.closeConnection();
  }

  private void filter(PacketSendEvent event) {
    if (event.isCancelled() || !(event.getPacketType() instanceof PacketType.Play.Server)) {
      return;
    }
    PacketType.Play.Server type = (PacketType.Play.Server) event.getPacketType();
    UUID viewer = event.getUser().getUUID();
    if (viewer == null) {
      return;
    }
    VanishView view = policy.get();
    if (view.hidden().containsKey(viewer) && clientVision.getAsBoolean()) {
      if (type == PacketType.Play.Server.ENTITY_EFFECT) {
        WrapperPlayServerEntityEffect effect = new WrapperPlayServerEntityEffect(event);
        if (effect.getEntityId() == view.hidden().get(viewer).entityId()
            && effect.getPotionType()
                == com.github.retrooper.packetevents.protocol.potion.PotionTypes.NIGHT_VISION) {
          event.setCancelled(true);
          return;
        }
      } else if (type == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
        WrapperPlayServerRemoveEntityEffect effect =
            new WrapperPlayServerRemoveEntityEffect(event);
        if (effect.getEntityId() == view.hidden().get(viewer).entityId()
            && effect.getPotionType()
                == com.github.retrooper.packetevents.protocol.potion.PotionTypes.NIGHT_VISION) {
          event.setCancelled(true);
          return;
        }
      }
    }

    switch (type) {
      case PLAYER_INFO:
        filterLegacyPlayerInfo(event, view, viewer);
        return;
      case PLAYER_INFO_UPDATE:
        filterPlayerInfo(event, view, viewer);
        return;
      case SPAWN_PLAYER:
        filterSpawnPlayer(event, view, viewer);
        return;
      case SPAWN_ENTITY:
        filterSpawnEntity(event, view, viewer);
        return;
      case TAB_COMPLETE:
        filterTabComplete(event, view, viewer);
        return;
      case TEAMS:
        filterTeams(event, view, viewer);
        return;
      case UPDATE_SCORE:
        filterScore(event, view, viewer);
        return;
      case RESET_SCORE:
        VanishScoreboard resetBoard = boards.get(event.getUser());
        if (resetBoard != null) {
          resetBoard.reset(new WrapperPlayServerResetScore(event));
        }
        return;
      case SCOREBOARD_OBJECTIVE:
        filterObjective(event, view, viewer);
        return;
      case SET_PASSENGERS:
        filterPassengers(event, view, viewer);
        return;
      case ATTACH_ENTITY:
        WrapperPlayServerAttachEntity attach = new WrapperPlayServerAttachEntity(event);
        if (view.hidesEntity(viewer, attach.getAttachedId())
            || view.hidesEntity(viewer, attach.getHoldingId())) {
          event.setCancelled(true);
        }
        return;
      case SOUND_EFFECT:
        filterSound(event, view, viewer);
        return;
      case EFFECT:
        filterEffect(event, view, viewer);
        return;
      case PARTICLE:
        // Block particles (place, break, bonemeal, redstone) at a tracked block.
        WrapperPlayServerParticle particle = new WrapperPlayServerParticle(event);
        com.github.retrooper.packetevents.util.Vector3d particleAt = particle.getPosition();
        UUID particleActor =
            actions.actorAt(viewer, particleAt.getX(), particleAt.getY(), particleAt.getZ());
        if (particleActor != null && view.hides(viewer, particleActor)) {
          event.setCancelled(true);
        }
        return;
      case BLOCK_ACTION:
        // Chest, shulker, door, button, lever and noteblock animations.
        WrapperPlayServerBlockAction blockAction = new WrapperPlayServerBlockAction(event);
        UUID blockActor =
            actions.actorAt(
                viewer,
                blockAction.getBlockPosition().getX(),
                blockAction.getBlockPosition().getY(),
                blockAction.getBlockPosition().getZ());
        if (blockActor != null && view.hides(viewer, blockActor)) {
          event.setCancelled(true);
        }
        return;
      case BLOCK_BREAK_ANIMATION:
        WrapperPlayServerBlockBreakAnimation animation =
            new WrapperPlayServerBlockBreakAnimation(event);
        if (view.hidesEntity(viewer, animation.getEntityId())) {
          event.setCancelled(true);
        }
        return;
      case DAMAGE_EVENT:
        filterDamage(event, view, viewer);
        return;
      case SYSTEM_CHAT_MESSAGE:
        filterSystemChat(event, view, viewer);
        return;
      case CHAT_MESSAGE:
        filterChat(event, view, viewer);
        return;
      case MAP_DATA:
        filterMap(event, view, viewer);
        return;
      default:
        int id = entityId(type, event);
        if (id != -1 && view.hidesEntity(viewer, id)) {
          event.setCancelled(true);
        }
    }
  }

  private void filterLegacyPlayerInfo(
      PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo(event);
    // Never suppress a tab-list removal: it is how hidePlayer() makes a vanished
    // player disappear on 1.8-1.19.2, and blocking it leaves the name visible.
    if (packet.getAction() == WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER) {
      return;
    }
    List<WrapperPlayServerPlayerInfo.PlayerData> visible =
        new ArrayList<WrapperPlayServerPlayerInfo.PlayerData>();
    for (WrapperPlayServerPlayerInfo.PlayerData data : packet.getPlayerDataList()) {
      if (!view.hides(viewer, data.getUserProfile().getUUID())) {
        visible.add(data);
      }
    }
    if (visible.isEmpty()) {
      event.setCancelled(true);
    } else if (visible.size() != packet.getPlayerDataList().size()) {
      packet.setPlayerDataList(visible);
      event.markForReEncode(true);
    }
  }

  private void filterPlayerInfo(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
    List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> visible =
        new ArrayList<WrapperPlayServerPlayerInfoUpdate.PlayerInfo>();
    for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
      if (!view.hides(viewer, entry.getProfileId())) {
        visible.add(entry);
      }
    }
    if (visible.isEmpty()) {
      event.setCancelled(true);
    } else if (visible.size() != packet.getEntries().size()) {
      packet.setEntries(visible);
      event.markForReEncode(true);
    }
  }

  private void filterSpawnPlayer(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
    if (view.hides(viewer, packet.getUUID())
        || view.hidesEntity(viewer, packet.getEntityId())) {
      event.setCancelled(true);
    }
  }

  private void filterSpawnEntity(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
    if (view.hidesEntity(viewer, packet.getEntityId())
        || (packet.getUUID().isPresent() && view.hides(viewer, packet.getUUID().get()))) {
      event.setCancelled(true);
    }
  }

  private void filterTabComplete(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerTabComplete packet = new WrapperPlayServerTabComplete(event);
    List<WrapperPlayServerTabComplete.CommandMatch> matches =
        new ArrayList<WrapperPlayServerTabComplete.CommandMatch>();
    for (WrapperPlayServerTabComplete.CommandMatch match : packet.getCommandMatches()) {
      boolean tooltipMentions =
          match.getTooltip().isPresent() && view.mentions(viewer, match.getTooltip().get());
      if (!view.mentions(viewer, match.getText()) && !tooltipMentions) {
        matches.add(match);
      }
    }
    packet.setCommandMatches(matches);
    event.markForReEncode(true);
  }

  private void filterTeams(PacketSendEvent event, VanishView view, UUID viewer) {
    VanishScoreboard board = board(event.getUser());
    WrapperPlayServerTeams original = new WrapperPlayServerTeams(event);
    event.setLastUsedWrapper(board.team(original, view, viewer));
    event.markForReEncode(true);
    // Run after this team update is written; split prefix/entry/suffix rows may change.
    final UUID finalViewer = viewer;
    event
        .getTasksAfterSend()
        .add(
            new Runnable() {
              @Override
              public void run() {
                try {
                  for (PacketWrapper<?> update :
                      board.scoreProjection(policy.get(), finalViewer)) {
                    event.getUser().sendPacketSilently(update);
                  }
                } catch (RuntimeException error) {
                  failClosed(event.getUser(), error);
                } catch (LinkageError error) {
                  failClosed(event.getUser(), error);
                }
              }
            });
  }

  private void filterScore(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerUpdateScore packet = new WrapperPlayServerUpdateScore(event);
    if (board(event.getUser()).score(packet, view, viewer)) {
      event.setCancelled(true);
      event
          .getUser()
          .sendPacketSilently(
              new WrapperPlayServerResetScore(
                  packet.getEntityName(), packet.getObjectiveName()));
    }
  }

  private void filterObjective(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerScoreboardObjective packet = new WrapperPlayServerScoreboardObjective(event);
    event.setLastUsedWrapper(board(event.getUser()).objective(packet, view, viewer));
    event.markForReEncode(true);
  }

  private void filterPassengers(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
    if (view.hidesEntity(viewer, packet.getEntityId())) {
      event.setCancelled(true);
      return;
    }
    List<Integer> passengers = new ArrayList<Integer>();
    for (int id : packet.getPassengers()) {
      if (!view.hidesEntity(viewer, id)) {
        passengers.add(Integer.valueOf(id));
      }
    }
    int[] next = new int[passengers.size()];
    for (int i = 0; i < passengers.size(); i++) {
      next[i] = passengers.get(i).intValue();
    }
    packet.setPassengers(next);
    event.markForReEncode(true);
  }

  private void filterSound(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
    String key = sound.getSound().getName().getKey();
    com.github.retrooper.packetevents.util.Vector3d position = sound.getPosition();
    if (!key.startsWith("block.")) {
      return;
    }
    UUID actor =
        actions.actorAt(viewer, position.getX(), position.getY(), position.getZ());
    if (actor != null && view.hides(viewer, actor)) {
      event.setCancelled(true);
    }
  }

  private void filterEffect(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerEffect effect = new WrapperPlayServerEffect(event);
    com.github.retrooper.packetevents.util.Vector3i position = effect.getPosition();
    if (effect.getType() != 2001) {
      return;
    }
    UUID actor =
        actions.actorAt(viewer, position.getX(), position.getY(), position.getZ());
    if (actor != null && view.hides(viewer, actor)) {
      event.setCancelled(true);
    }
  }

  private void filterDamage(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerDamageEvent packet = new WrapperPlayServerDamageEvent(event);
    if (view.hidesEntity(viewer, packet.getEntityId())) {
      event.setCancelled(true);
    } else if (view.hidesEntity(viewer, packet.getSourceCauseId())
        || view.hidesEntity(viewer, packet.getSourceDirectId())) {
      packet.setSourceCauseId(-1);
      packet.setSourceDirectId(-1);
      packet.setSourcePosition(null);
      event.markForReEncode(true);
    }
  }

  private void filterSystemChat(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
    if (view.mentionsChat(viewer, packet.getMessage())) {
      event.setCancelled(true);
    }
  }

  private void filterChat(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerChatMessage packet = new WrapperPlayServerChatMessage(event);
    if (view.mentionsChat(viewer, packet.getMessage().getChatContent())) {
      event.setCancelled(true);
    }
  }

  private void filterMap(PacketSendEvent event, VanishView view, UUID viewer) {
    WrapperPlayServerMapData packet = new WrapperPlayServerMapData(event);
    if (packet.getDecorations() == null) {
      return;
    }
    WrapperPlayServerMapData original =
        new WrapperPlayServerMapData(
            packet.getMapId(),
            packet.getScale(),
            packet.isTrackingPosition(),
            packet.isLocked(),
            new ArrayList<WrapperPlayServerMapData.MapDecoration>(packet.getDecorations()),
            0,
            0,
            0,
            0,
            null);
    maps.computeIfAbsent(event.getUser(), ignored -> new ConcurrentHashMap<Integer, WrapperPlayServerMapData>())
        .put(Integer.valueOf(packet.getMapId()), original);
    packet.setDecorations(projectMap(original, view, viewer).getDecorations());
    event.markForReEncode(true);
  }

  private VanishScoreboard board(User user) {
    VanishScoreboard board = boards.get(user);
    if (board == null) {
      board = new VanishScoreboard();
      boards.put(user, board);
    }
    return board;
  }

  private static WrapperPlayServerMapData projectMap(
      WrapperPlayServerMapData map, VanishView view, UUID viewer) {
    List<WrapperPlayServerMapData.MapDecoration> decorations =
        new ArrayList<WrapperPlayServerMapData.MapDecoration>();
    if (map.getDecorations() != null) {
      for (WrapperPlayServerMapData.MapDecoration marker : map.getDecorations()) {
        if (view.mentions(viewer, marker.getDisplayName())) {
          continue;
        }
        if (hasHiddenOthers(view, viewer)
            && marker.getType().getName().getKey().startsWith("player")) {
          continue;
        }
        decorations.add(marker);
      }
    }
    return new WrapperPlayServerMapData(
        map.getMapId(),
        map.getScale(),
        map.isTrackingPosition(),
        map.isLocked(),
        decorations,
        0,
        0,
        0,
        0,
        null);
  }

  private static boolean hasHiddenOthers(VanishView view, UUID viewer) {
    for (UUID id : view.hidden().keySet()) {
      if (view.hides(viewer, id)) {
        return true;
      }
    }
    return false;
  }

  private static int entityId(PacketType.Play.Server type, PacketSendEvent event) {
    switch (type) {
      case ENTITY_ANIMATION:
        return new WrapperPlayServerEntityAnimation(event).getEntityId();
      case ENTITY_RELATIVE_MOVE:
        return new WrapperPlayServerEntityRelativeMove(event).getEntityId();
      case ENTITY_RELATIVE_MOVE_AND_ROTATION:
        return new WrapperPlayServerEntityRelativeMoveAndRotation(event).getEntityId();
      case ENTITY_ROTATION:
        return new WrapperPlayServerEntityRotation(event).getEntityId();
      case ENTITY_HEAD_LOOK:
        return new WrapperPlayServerEntityHeadLook(event).getEntityId();
      case ENTITY_METADATA:
        return new WrapperPlayServerEntityMetadata(event).getEntityId();
      case ENTITY_VELOCITY:
        return new WrapperPlayServerEntityVelocity(event).getEntityId();
      case ENTITY_EQUIPMENT:
        return new WrapperPlayServerEntityEquipment(event).getEntityId();
      case ENTITY_TELEPORT:
        return new WrapperPlayServerEntityTeleport(event).getEntityId();
      case ENTITY_EFFECT:
        return new WrapperPlayServerEntityEffect(event).getEntityId();
      case REMOVE_ENTITY_EFFECT:
        return new WrapperPlayServerRemoveEntityEffect(event).getEntityId();
      case ENTITY_SOUND_EFFECT:
        return new WrapperPlayServerEntitySoundEffect(event).getEntityId();
      case ENTITY_STATUS:
        return new WrapperPlayServerEntityStatus(event).getEntityId();
      case CAMERA:
        return new WrapperPlayServerCamera(event).getCameraId();
      case ENTITY_POSITION_SYNC:
        return new WrapperPlayServerEntityPositionSync(event).getId();
      default:
        return -1;
    }
  }
}
