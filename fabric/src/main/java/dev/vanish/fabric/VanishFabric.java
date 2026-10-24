package dev.vanish.fabric;

import dev.vanish.mod.LoaderRuntime;
import dev.vanish.mod.SimpleModPlatform;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint; all vanish logic lives in the shared mod service. */
public final class VanishFabric implements DedicatedServerModInitializer {
  private final LoaderRuntime runtime = new LoaderRuntime();

  @Override
  public void onInitializeServer() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> runtime.start(new FabricPlatform(server)));
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> runtime.stop());
    ServerTickEvents.END_SERVER_TICK.register(server -> runtime.tick());
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> runtime.join(handler.player));
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> runtime.left(handler.player));
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> runtime.commands(dispatcher));
  }

  private static final class FabricPlatform extends SimpleModPlatform {
    private final MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();

    private FabricPlatform(MinecraftServer server) {
      super("Fabric", server, FabricLoader.getInstance().getConfigDir().resolve("vanish"));
    }

    @Override
    public String mapFieldName(String officialClass, String field, String descriptor) {
      return mappings.mapFieldName("official", officialClass, field, descriptor);
    }

    @Override
    public String mapMethodName(String officialClass, String method, String descriptor) {
      return mappings.mapMethodName("official", officialClass, method, descriptor);
    }
  }
}
