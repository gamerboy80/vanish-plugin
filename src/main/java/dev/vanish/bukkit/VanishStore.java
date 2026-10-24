package dev.vanish.bukkit;

import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishState;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * UUID-only vanish state with saved baselines, committed atomically before any
 * visibility change. Schema 1 files from the Epic SMP plugin are accepted so an
 * existing server keeps its vanished staff after the split.
 */
final class VanishStore {
  private static final int SCHEMA = 2;

  private final Path file;

  VanishStore(Path file) {
    this.file = file.toAbsolutePath();
  }

  VanishState load() throws IOException {
    if (!Files.exists(file)) {
      return VanishState.EMPTY;
    }
    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.load(file.toFile());
      int schema = yaml.getInt("schema", 1);
      if (schema < 1 || schema > SCHEMA) {
        throw new IllegalArgumentException("Unsupported vanish state schema " + schema);
      }
      Set<UUID> vanished = new LinkedHashSet<UUID>();
      List<?> values = yaml.getList("vanished");
      if (values != null) {
        for (Object value : values) {
          if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid vanished UUID entry");
          }
          if (!vanished.add(UUID.fromString((String) value))) {
            throw new IllegalArgumentException("Duplicate vanished UUID");
          }
        }
      }
      Map<UUID, VanishBaseline> baselines = new LinkedHashMap<UUID, VanishBaseline>();
      if (schema >= 2) {
        ConfigurationSection section = yaml.getConfigurationSection("baselines");
        if (section != null) {
          for (String key : section.getKeys(false)) {
            UUID id;
            try {
              id = UUID.fromString(key);
            } catch (IllegalArgumentException error) {
              throw new IllegalArgumentException("Invalid baseline UUID " + key);
            }
            ConfigurationSection values2 = section.getConfigurationSection(key);
            if (values2 == null) {
              throw new IllegalArgumentException("Invalid baseline for " + key);
            }
            baselines.put(
                id,
                new VanishBaseline(
                    values2.getBoolean("flight"),
                    values2.getBoolean("flying"),
                    values2.getBoolean("collision", true),
                    values2.getBoolean("silent"),
                    values2.getBoolean("sleeping"),
                    values2.getBoolean("spawning", true),
                    values2.getString("mode", "SURVIVAL")));
          }
        }
      }
      return new VanishState(vanished, baselines);
    } catch (InvalidConfigurationException | IllegalArgumentException error) {
      throw new IOException("Cannot load vanish state from " + file, error);
    }
  }

  void save(VanishState state) throws IOException {
    Files.createDirectories(file.getParent());
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("schema", Integer.valueOf(SCHEMA));

    List<String> vanished = new ArrayList<String>();
    for (UUID id : state.vanished()) {
      vanished.add(id.toString());
    }
    Collections.sort(vanished);
    yaml.set("vanished", vanished);

    if (!state.baselines().isEmpty()) {
      ConfigurationSection baselines = yaml.createSection("baselines");
      List<UUID> ids = new ArrayList<UUID>(state.baselines().keySet());
      Collections.sort(ids);
      for (UUID id : ids) {
        VanishBaseline baseline = state.baselines().get(id);
        ConfigurationSection section = baselines.createSection(id.toString());
        section.set("flight", Boolean.valueOf(baseline.flight()));
        section.set("flying", Boolean.valueOf(baseline.flying()));
        section.set("collision", Boolean.valueOf(baseline.collision()));
        section.set("silent", Boolean.valueOf(baseline.silent()));
        section.set("sleeping", Boolean.valueOf(baseline.sleeping()));
        section.set("spawning", Boolean.valueOf(baseline.spawning()));
        section.set("mode", baseline.mode());
      }
    }

    Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
    yaml.save(temporary.toFile());
    try {
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  File file() {
    return file.toFile();
  }
}
