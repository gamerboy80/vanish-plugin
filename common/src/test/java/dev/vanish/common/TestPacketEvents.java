package dev.vanish.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;

/** PacketEvents boundary for tests; production always uses the shaded instance. */
final class TestPacketEvents {
  private TestPacketEvents() {}

  static void install() {
    PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
    ServerManager server = mock(ServerManager.class);
    when(server.getVersion()).thenReturn(ServerVersion.V_26_2);
    when(api.getServerManager()).thenReturn(server);
    when(api.getPlayerManager()).thenReturn(mock(PlayerManager.class));

    NettyManager netty = mock(NettyManager.class);
    ChannelOperator channels = mock(ChannelOperator.class);
    when(api.getNettyManager()).thenReturn(netty);
    when(netty.getChannelOperator()).thenReturn(channels);
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(channels)
        .runInEventLoop(any(), any(Runnable.class));

    when(api.getEventManager()).thenReturn(new EventManager());
    when(api.getSettings()).thenReturn(new PacketEventsSettings());
    when(api.isInitialized()).thenReturn(true);
    when(api.getVersion())
        .thenReturn(new com.github.retrooper.packetevents.util.PEVersion(2, 14, 0));
    PacketEvents.setAPI(api);
  }
}
