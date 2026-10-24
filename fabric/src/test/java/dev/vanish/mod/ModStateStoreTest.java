package dev.vanish.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModStateStoreTest {
  @TempDir Path directory;

  @Test
  void roundTripsVisibilityAndBaseline() throws Exception {
    UUID id = UUID.randomUUID();
    VanishBaseline baseline = new VanishBaseline(true, false, false, true, false, true, "CREATIVE");
    Set<UUID> vanished = new LinkedHashSet<UUID>();
    vanished.add(id);
    Map<UUID, VanishBaseline> baselines = new LinkedHashMap<UUID, VanishBaseline>();
    baselines.put(id, baseline);
    VanishState state = new VanishState(vanished, baselines);

    ModStateStore store = new ModStateStore(directory.resolve("vanish.json"));
    store.save(state);

    assertEquals(state, store.load());
  }

  @Test
  void readsSchemaOneWithoutBaselines() throws Exception {
    UUID id = UUID.randomUUID();
    Path file = directory.resolve("vanish.json");
    Files.write(
        file,
        ("{\"schema\":1,\"vanished\":[\"" + id + "\"]}").getBytes(StandardCharsets.UTF_8));

    VanishState state = new ModStateStore(file).load();

    assertTrue(state.vanished().contains(id));
    assertTrue(state.baselines().isEmpty());
  }
}
