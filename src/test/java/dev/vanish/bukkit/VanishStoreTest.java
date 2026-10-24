package dev.vanish.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanishStoreTest {
  @TempDir Path directory;

  @Test
  void stateAndBaselinesRoundTrip() throws IOException {
    UUID id = UUID.randomUUID();
    VanishBaseline baseline =
        new VanishBaseline(true, false, true, false, true, true, "ADVENTURE");
    Map<UUID, VanishBaseline> baselines = new LinkedHashMap<UUID, VanishBaseline>();
    baselines.put(id, baseline);
    VanishState state =
        new VanishState(Collections.singleton(id), baselines);

    Path file = directory.resolve("vanish.yml");
    VanishStore store = new VanishStore(file);
    store.save(state);

    VanishState loaded = new VanishStore(file).load();
    assertEquals(Collections.singleton(id), loaded.vanished());
    assertEquals(baseline, loaded.baseline(id));
  }

  @Test
  void schemaOneFilesRemainReadable() throws IOException {
    UUID id = UUID.randomUUID();
    Path file = directory.resolve("vanish.yml");
    Files.write(
        file,
        ("schema: 1\nvanished:\n- " + id + "\n").getBytes(StandardCharsets.UTF_8));

    VanishState loaded = new VanishStore(file).load();
    assertEquals(Collections.singleton(id), loaded.vanished());
    assertNull(loaded.baseline(id));
  }

  @Test
  void corruptStateFailsClosedInsteadOfSilentlyRevealingStaff() throws IOException {
    Path file = directory.resolve("vanish.yml");
    Files.write(file, "schema: 2\nvanished: [not-a-uuid]\n".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, () -> new VanishStore(file).load());
    assertTrue(Files.exists(file));
  }
}
