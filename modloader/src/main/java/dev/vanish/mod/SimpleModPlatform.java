package dev.vanish.mod;

import java.nio.file.Path;
import java.util.logging.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The loader-neutral part of {@link ModPlatform}; loaders supply only identity and paths. */
public class SimpleModPlatform implements ModPlatform {
  private final String name;
  private final MinecraftServer server;
  private final Path dataDirectory;
  private final Logger logger;

  public SimpleModPlatform(String name, MinecraftServer server, Path dataDirectory) {
    this.name = name;
    this.server = server;
    this.dataDirectory = dataDirectory;
    this.logger = Logger.getLogger("Vanish");
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public MinecraftServer server() {
    return server;
  }

  @Override
  public Path dataDirectory() {
    return dataDirectory;
  }

  @Override
  public Logger logger() {
    return logger;
  }

  @Override
  public void send(ServerPlayer player, Component message) {
    player.sendSystemMessage(message);
  }
}
