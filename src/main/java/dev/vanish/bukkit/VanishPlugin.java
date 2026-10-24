package dev.vanish.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import dev.vanish.common.VanishAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit loader shared by Spigot and Paper.
 *
 * <p>The jar compiles against the 1.8.8 API and shades its own PacketEvents, so
 * a server owner installs one jar and does not add PacketEvents separately.
 * Event and API differences after 1.8.8 are handled in
 * {@code dev.vanish.bukkit.compat.Compat} and {@link OptionalEvents}.
 */
public final class VanishPlugin extends JavaPlugin implements VanishAPI.Provider {
  public static final String PERM_USE = "vanish.use";
  public static final String PERM_AUTO = "vanish.auto";
  public static final String PERM_SEE = "vanish.see";
  public static final String PERM_OTHERS = "vanish.others";
  public static final String PERM_INSPECT = "vanish.inspect";
  public static final String PERM_ADMIN = "vanish.admin";

  private PacketEventsAPI<?> packetEvents;
  private VanishService service;
  private volatile VanishSettings settings;

  @Override
  public void onLoad() {
    packetEvents = SpigotPacketEventsBuilder.build(this);
    PacketEvents.setAPI(packetEvents);
    packetEvents
        .getSettings()
        .checkForUpdates(false)
        .bStats(false)
        .debug(false)
        .fullStackTrace(true)
        .kickOnPacketException(true)
        .reEncodeByDefault(false);
    packetEvents.load();
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    try {
      settings = VanishSettings.load(new File(getDataFolder(), "config.yml"));
      packetEvents.init();
      service = new VanishService(this);
      service.start();
      VanishPlaceholders.register(this, service);
    } catch (IOException | RuntimeException error) {
      getLogger().severe("Cannot initialize Vanish: " + error.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    VanishAPI.init(this);
    String translators = VanishIntegrations.translators();
    if (!translators.isEmpty()) {
      getLogger()
          .info(
              "Protocol translation active via "
                  + translators
                  + "; visibility follows each client's protocol.");
    }
    getLogger().info("Vanish enabled.");
  }

  @Override
  public void onDisable() {
    VanishAPI.shutdown();
    if (service != null) {
      service.stop();
      service = null;
    }
    if (packetEvents != null) {
      try {
        if (packetEvents.isInitialized()) {
          packetEvents.terminate();
        }
      } catch (RuntimeException error) {
        getLogger().warning("PacketEvents shutdown failed: " + error.getMessage());
      }
      packetEvents = null;
    }
  }

  /** Returns the active configuration snapshot. */
  public VanishSettings settings() {
    return settings;
  }

  /** Returns the vanish service for integrations. */
  public VanishService vanish() {
    return service;
  }

  /** Delegates vanish-state queries to the active service for {@link VanishAPI}. */
  @Override
  public boolean isVanished(UUID id) {
    return service != null && service.isVanished(id);
  }

  /** Delegates visibility queries to the active service for {@link VanishAPI}. */
  @Override
  public boolean canSee(UUID viewer, UUID subject) {
    return service == null || service.canSee(viewer, subject);
  }

  /** Builds prefixed text with palette tokens and ampersand colors resolved. */
  public String msg(String text) {
    return settings.prefix() + settings.format(text, null);
  }

  void registerVanishCommand(VanishService vanishService) {
    PluginCommand command = getCommand("vanish");
    if (command == null) {
      throw new IllegalStateException("plugin.yml is missing the vanish command");
    }
    VanishCommand executor = new VanishCommand(this, vanishService);
    command.setExecutor(executor);
    command.setTabCompleter(executor);
  }

  /** Reloads config.yml without touching visibility if validation fails. */
  void reloadVanish() throws IOException {
    VanishSettings next = VanishSettings.load(new File(getDataFolder(), "config.yml"));
    settings = next;
    if (service != null) {
      service.reload();
    }
  }

  /** Persists one option and reloads the active snapshot. */
  void updateOption(String name, boolean value) throws IOException {
    Map<String, Boolean> change = new java.util.LinkedHashMap<String, Boolean>();
    change.put(name, Boolean.valueOf(value));
    updateOptions(change);
  }

  /** Persists a group of option changes as one configuration transaction. */
  void updateOptions(Map<String, Boolean> changes) throws IOException {
    File configFile = new File(getDataFolder(), "config.yml");
    YamlConfiguration next = new YamlConfiguration();
    try {
      next.load(configFile);
    } catch (InvalidConfigurationException error) {
      throw new IOException("Invalid config.yml", error);
    }
    for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
      next.set("vanish." + entry.getKey(), entry.getValue());
    }
    // Validate the whole document before touching the active file.
    VanishSettings.from(next);

    Path destination = configFile.toPath();
    Path temporary = configFile.toPath().resolveSibling("config.yml.tmp");
    next.save(temporary.toFile());
    try {
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }
    reloadVanish();
  }
}
