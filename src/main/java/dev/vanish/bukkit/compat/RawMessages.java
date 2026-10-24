package dev.vanish.bukkit.compat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessageLegacy;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Sends messages straight to one connection, skipping PacketEvents listeners.
 * Vanished staff chat and fake join/quit notices must not pass through the
 * mention filter that intentionally hides those names from ordinary viewers.
 */
public final class RawMessages {
  private RawMessages() {}

  /** Sends a system message without triggering the vanish packet filter. */
  public static void send(Player viewer, Component message) {
    User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
    if (user == null) {
      viewer.sendMessage(PlainTextComponentSerializer.plainText().serialize(message));
      return;
    }
    // Branch on the server protocol, not the client's: PacketEvents encodes at the
    // server version and protocol translators (ViaVersion, ViaBackwards,
    // ViaRewind, ProtocolSupport) rewrite the packet for the client afterwards.
    if (PacketEvents.getAPI().getServerManager().getVersion()
        .isOlderThan(ServerVersion.V_1_19)) {
      user.sendPacketSilently(
          new WrapperPlayServerChatMessage(new ChatMessageLegacy(message, ChatTypes.SYSTEM)));
    } else {
      user.sendPacketSilently(new WrapperPlayServerSystemChatMessage(false, message));
    }
  }

  /** Sends an action bar (1.8+ uses the title packet under PacketEvents). */
  public static void actionBar(Player viewer, Component message) {
    User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
    if (user == null) {
      viewer.sendMessage(PlainTextComponentSerializer.plainText().serialize(message));
      return;
    }
    user.sendPacketSilently(new WrapperPlayServerActionBar(message));
  }

  /** Clears a stale action bar on every supported version. */
  public static void clearActionBar(Player viewer) {
    actionBar(viewer, Component.empty());
  }
}
