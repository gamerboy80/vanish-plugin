package dev.vanish.mod;

import com.github.retrooper.packetevents.protocol.PacketSide;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs after the vanilla packet encoder and before compression, so PacketEvents
 * sees a complete uncompressed packet body. Writes issued at this handler (the
 * silent path used for scoreboard/map replays) skip the filter and continue
 * through compression and framing.
 */
final class ModPacketEncoder extends ChannelOutboundHandlerAdapter {
  private final User user;
  private final Object player;
  private final Logger logger;

  ModPacketEncoder(User user, Object player, Logger logger) {
    this.user = user;
    this.player = player;
    this.logger = logger;
  }

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
      throws Exception {
    if (!(message instanceof ByteBuf)) {
      context.write(message, promise);
      return;
    }
    ByteBuf buffer = (ByteBuf) message;
    if (!buffer.isReadable()) {
      buffer.release();
      return;
    }
    try {
      PacketEventsImplHelper.handlePacket(
          context.channel(), user, player, buffer, false, PacketSide.CLIENT);
    } catch (Exception error) {
      logger.log(Level.SEVERE, "Vanish packet filtering failed closed", error);
      buffer.release();
      promise.tryFailure(error);
      user.closeConnection();
      return;
    }
    if (buffer.isReadable()) {
      context.write(buffer, promise);
    } else {
      buffer.release();
    }
  }
}
