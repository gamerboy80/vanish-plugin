package dev.vanish.mod;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owns the shared service for one server lifetime. Loader adapters create one
 * of these and forward their lifecycle events; no adapter needs its own
 * null-checking or startup error handling.
 */
public final class LoaderRuntime {
  private ModVanish vanish;

  public void start(String loaderName, MinecraftServer server, Path dataDirectory) {
    start(new SimpleModPlatform(loaderName, server, dataDirectory));
  }

  public void start(ModPlatform platform) {
    if (vanish != null) {
      return;
    }
    ModVanish next = new ModVanish(platform);
    try {
      next.start();
      vanish = next;
    } catch (IOException error) {
      throw new IllegalStateException("Cannot initialize Vanish", error);
    }
  }

  public void stop() {
    if (vanish != null) {
      vanish.stop();
      vanish = null;
    }
  }

  public void tick() {
    if (vanish != null) {
      vanish.tick();
    }
  }

  public void join(ServerPlayer player) {
    if (vanish != null) {
      vanish.joined(player);
    }
  }

  public void left(ServerPlayer player) {
    if (vanish != null) {
      vanish.left(player);
    }
  }

  public void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
    ModCommands.register(dispatcher, () -> vanish);
  }
}
