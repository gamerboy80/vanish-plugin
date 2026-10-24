package dev.vanish.mod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * Atomic schema-2 state file. The same document shape is used by the Bukkit
 * loader, so a server moving between platforms keeps its vanished staff.
 */
public final class ModStateStore {
  private static final int SCHEMA = 2;

  private final Path file;

  public ModStateStore(Path file) {
    this.file = file.toAbsolutePath();
  }

  public VanishState load() throws IOException {
    if (!Files.exists(file)) {
      return VanishState.EMPTY;
    }
    try {
      JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
      int schema = root.has("schema") ? root.get("schema").getAsInt() : 1;
      if (schema < 1 || schema > SCHEMA) {
        throw new IllegalArgumentException("Unsupported vanish state schema " + schema);
      }
      Set<UUID> vanished = new LinkedHashSet<UUID>();
      if (root.has("vanished")) {
        JsonArray values = root.getAsJsonArray("vanished");
        for (JsonElement value : values) {
          if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid vanished UUID entry");
          }
          if (!vanished.add(UUID.fromString(value.getAsString()))) {
            throw new IllegalArgumentException("Duplicate vanished UUID");
          }
        }
      }
      Map<UUID, VanishBaseline> baselines = new LinkedHashMap<UUID, VanishBaseline>();
      if (schema >= 2 && root.has("baselines")) {
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("baselines").entrySet()) {
          UUID id;
          try {
            id = UUID.fromString(entry.getKey());
          } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid baseline UUID " + entry.getKey());
          }
          JsonObject values = entry.getValue().getAsJsonObject();
          baselines.put(
              id,
              new VanishBaseline(
                  bool(values, "flight", false),
                  bool(values, "flying", false),
                  bool(values, "collision", true),
                  bool(values, "silent", false),
                  bool(values, "sleeping", false),
                  bool(values, "spawning", true),
                  values.has("mode") ? values.get("mode").getAsString() : "SURVIVAL"));
        }
      }
      return new VanishState(vanished, baselines);
    } catch (RuntimeException error) {
      throw new IOException("Cannot load vanish state from " + file, error);
    }
  }

  private static boolean bool(JsonObject object, String name, boolean fallback) {
    return object.has(name) ? object.get(name).getAsBoolean() : fallback;
  }

  public void save(VanishState state) throws IOException {
    Files.createDirectories(file.getParent());
    JsonObject root = new JsonObject();
    root.addProperty("schema", Integer.valueOf(SCHEMA));

    List<String> vanished = new ArrayList<String>();
    for (UUID id : state.vanished()) {
      vanished.add(id.toString());
    }
    Collections.sort(vanished);
    JsonArray values = new JsonArray();
    for (String id : vanished) {
      values.add(id);
    }
    root.add("vanished", values);

    if (!state.baselines().isEmpty()) {
      JsonObject baselines = new JsonObject();
      List<UUID> ids = new ArrayList<UUID>(state.baselines().keySet());
      Collections.sort(ids);
      for (UUID id : ids) {
        VanishBaseline baseline = state.baselines().get(id);
        JsonObject entry = new JsonObject();
        entry.addProperty("flight", Boolean.valueOf(baseline.flight()));
        entry.addProperty("flying", Boolean.valueOf(baseline.flying()));
        entry.addProperty("collision", Boolean.valueOf(baseline.collision()));
        entry.addProperty("silent", Boolean.valueOf(baseline.silent()));
        entry.addProperty("sleeping", Boolean.valueOf(baseline.sleeping()));
        entry.addProperty("spawning", Boolean.valueOf(baseline.spawning()));
        entry.addProperty("mode", baseline.mode());
        baselines.add(id.toString(), entry);
      }
      root.add("baselines", baselines);
    }

    Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
    Files.write(
        temporary,
        new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
    try {
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
