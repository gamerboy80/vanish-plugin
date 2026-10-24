package dev.vanish.forge;

import dev.vanish.mod.LoaderRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge entrypoint; all vanish logic lives in the shared mod service. */
@Mod("vanish")
public final class VanishForge {
  private final LoaderRuntime runtime = new LoaderRuntime();

  public VanishForge() {
    MinecraftForge.EVENT_BUS.register(this);
  }

  @SubscribeEvent
  public void onServerStarted(ServerStartedEvent event) {
    runtime.start("Forge", event.getServer(), FMLPaths.CONFIGDIR.get().resolve("vanish"));
  }

  @SubscribeEvent
  public void onServerStopping(ServerStoppingEvent event) {
    runtime.stop();
  }

  @SubscribeEvent
  public void onServerTick(TickEvent.ServerTickEvent.Post event) {
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
