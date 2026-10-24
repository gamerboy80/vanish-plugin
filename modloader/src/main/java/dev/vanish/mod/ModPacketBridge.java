package dev.vanish.mod;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import dev.vanish.common.VanishActions;
import dev.vanish.common.VanishPackets;
import dev.vanish.common.VanishView;
import io.github.retrooper.packetevents.impl.netty.BuildData;
import io.github.retrooper.packetevents.impl.netty.factory.NettyPacketEventsBuilder;
import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

/**
 * A small PacketEvents platform for modded servers. The outbound encoder is
 * inserted into each player's Netty pipeline after login, so no mixin or
 * platform-specific PacketEvents module is required.
 */
public final class ModPacketBridge {
  private final ModPlatform platform;
  private final Map<UUID, User> users = new ConcurrentHashMap<UUID, User>();
  private PacketEventsAPI<?> api;
  private VanishPackets listener;
  private Field listenerConnectionField;
  private Field channelField;

  public ModPacketBridge(ModPlatform platform) {
    this.platform = platform;
  }

  /** Builds and initializes the private PacketEvents instance. */
  public void install(
      Supplier<VanishView> policy,
      BooleanSupplier clientVision,
      VanishActions actions,
      Logger logger) {
    api =
        NettyPacketEventsBuilder.build(
            new BuildData("Vanish"),
            new NoopInjector(),
            new PlatformProtocolManager(),
            new PlatformServerManager(),
            new PlatformPlayerManager());
    PacketEvents.setAPI(api);
    api.getSettings()
        .checkForUpdates(false)
        .bStats(false)
        .debug(false)
        .fullStackTrace(true)
        .kickOnPacketException(false)
        .reEncodeByDefault(false);
    api.load();
    api.init();
    listener = new VanishPackets(policy, clientVision, actions, logger);
    api.getEventManager().registerListener(listener);
  }

  /** Starts filtering one player's outbound traffic. */
  public void attach(ServerPlayer player) {
    Channel channel = channel(player);
    if (channel == null || !channel.isOpen()) {
      return;
    }
    User existing = users.get(player.getUUID());
    if (existing != null) {
      if (existing.getChannel() == channel) {
        return;
      }
      // A reconnect can race the disconnect callback. Move filtering to the
      // new channel instead of leaving the fresh connection unfiltered.
      remove((Channel) existing.getChannel(), player.getUUID());
    }
    ClientVersion clientVersion = clientVersion();
    User user =
        new User(
            channel,
            ConnectionState.PLAY,
            clientVersion,
            new UserProfile(player.getUUID(), player.getGameProfile().name()));
    user.setEntityId(player.getId());
    user.setEncoderState(ConnectionState.PLAY);
    user.setDecoderState(ConnectionState.PLAY);

    // PacketEvents resolves a wrapper's send target through these maps, and
    // sendPacketSilently writes at the encoder handler by name.
    ProtocolManager.USERS.put(channel, user);
    ProtocolManager.CHANNELS.put(player.getUUID(), channel);

    ChannelPipeline pipeline = channel.pipeline();
    if (pipeline.get(PacketEvents.ENCODER_NAME) == null) {
      ModPacketEncoder encoder = new ModPacketEncoder(user, player, platform.logger());
      if (pipeline.get("encoder") != null) {
        pipeline.addBefore("encoder", PacketEvents.ENCODER_NAME, encoder);
      } else {
        pipeline.addLast(PacketEvents.ENCODER_NAME, encoder);
      }
    }
    users.put(player.getUUID(), user);
  }

  /** Stops filtering and drops cached connection state. */
  public void detach(ServerPlayer player) {
    User user = users.get(player.getUUID());
    if (user != null) {
      remove((Channel) user.getChannel(), player.getUUID());
    }
    if (listener != null) {
      listener.forget(player.getUUID());
    }
  }

  private void remove(Channel channel, UUID player) {
    users.remove(player);
    ProtocolManager.CHANNELS.remove(player);
    if (channel == null) {
      return;
    }
    ProtocolManager.USERS.remove(channel);
    if (channel.pipeline().get(PacketEvents.ENCODER_NAME) != null) {
      channel.pipeline().remove(PacketEvents.ENCODER_NAME);
    }
  }

  public User user(ServerPlayer player) {
    return users.get(player.getUUID());
  }

  /** Replays cached scoreboard/map projections after a visibility change. */
  public void refresh(ServerPlayer player) {
    User user = users.get(player.getUUID());
    if (listener != null && user != null) {
      listener.refresh(player.getUUID(), user);
    }
  }

  public void close() {
    if (api != null && api.isInitialized()) {
      api.terminate();
    }
    users.clear();
  }

  private static ClientVersion clientVersion() {
    int protocol = SharedConstants.getCurrentVersion().protocolVersion();
    ClientVersion version = ClientVersion.getById(protocol);
    return version == null ? ClientVersion.getLatest() : version;
  }

  private Channel channel(ServerPlayer player) {
    try {
      Object listenerObject = player.connection;
      if (listenerConnectionField == null) {
        listenerConnectionField =
            findField(
                listenerObject.getClass(),
                "net.minecraft.server.network.ServerCommonPacketListenerImpl",
                "connection",
                "Lnet/minecraft/network/Connection;");
      }
      Object connection = listenerConnectionField.get(listenerObject);
      if (channelField == null) {
        channelField =
            findField(
                Connection.class,
                "net.minecraft.network.Connection",
                "channel",
                "Lio/netty/channel/Channel;");
      }
      return (Channel) channelField.get(connection);
    } catch (ReflectiveOperationException | RuntimeException error) {
      platform
          .logger()
          .log(
              Level.FINE,
              "Cannot reach the Netty channel for " + player.getGameProfile().name(),
              error);
      return null;
    }
  }

  private Field findField(Class<?> owner, String officialClass, String name, String descriptor)
      throws NoSuchFieldException {
    Field field = owner.getDeclaredField(platform.mapFieldName(officialClass, name, descriptor));
    field.setAccessible(true);
    return field;
  }

  private final class PlatformPlayerManager extends PlayerManagerAbstract {
    @Override
    public int getPing(Object player) {
      return 0;
    }

    @Override
    public Object getChannel(Object player) {
      return player instanceof ServerPlayer ? channel((ServerPlayer) player) : null;
    }

    @Override
    public User getUser(Object player) {
      return player instanceof ServerPlayer ? users.get(((ServerPlayer) player).getUUID()) : null;
    }
  }

  private static final class PlatformServerManager extends ServerManagerAbstract {
    @Override
    public ServerVersion getVersion() {
      ServerVersion version =
          ServerVersion.getById(SharedConstants.getCurrentVersion().protocolVersion());
      return version == null ? ServerVersion.getLatest() : version;
    }
  }

  private static final class PlatformProtocolManager extends ProtocolManagerAbstract {
    @Override
    public ProtocolVersion getPlatformVersion() {
      return ProtocolVersion.UNKNOWN;
    }
  }

  private static final class NoopInjector implements ChannelInjector {
    @Override
    public void inject() {
      // Per-player injection is handled by ModPacketBridge#attach.
    }

    @Override
    public void uninject() {
      // No server-wide handler to remove.
    }

    @Override
    public void updateUser(Object channel, User user) {
      ProtocolManager.USERS.put(channel, user);
    }

    @Override
    public void setPlayer(Object channel, Object player) {
      // Not needed: the encoder keeps its own ServerPlayer reference.
    }

    @Override
    public boolean isPlayerSet(Object channel) {
      return true;
    }

    @Override
    public boolean isProxy() {
      return false;
    }
  }
}
