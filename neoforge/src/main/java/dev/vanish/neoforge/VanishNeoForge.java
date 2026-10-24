package dev.vanish.neoforge;

import dev.vanish.mod.LoaderRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge entrypoint; all vanish logic lives in the shared mod service. */
@Mod("vanish")
public final class VanishNeoForge {
  private final LoaderRuntime runtime = new LoaderRuntime();

  public VanishNeoForge(IEventBus modBus, ModContainer container) {
    NeoForge.EVENT_BUS.register(this);
  }

  @SubscribeEvent
  public void onServerStarted(ServerStartedEvent event) {
    runtime.start("NeoForge", event.getServer(), FMLPaths.CONFIGDIR.get().resolve("vanish"));
  }

  @SubscribeEvent
  public void onServerStopping(ServerStoppingEvent event) {
    runtime.stop();
  }

  @SubscribeEvent
  public void onServerTick(ServerTickEvent.Post event) {
    runtime.tick();
  }

  @SubscribeEvent
  public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer) {
      runtime.join((ServerPlayer) event.getEntity());
    }
  }

  @SubscribeEvent
  public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer) {
      runtime.left((ServerPlayer) event.getEntity());
    }
  }

  @SubscribeEvent
  public void onRegisterCommands(RegisterCommandsEvent event) {
    runtime.commands(event.getDispatcher());
  }
}
