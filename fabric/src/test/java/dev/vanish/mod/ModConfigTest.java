package dev.vanish.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
  @TempDir Path directory;

  @Test
  void writesDocumentedDefaults() throws Exception {
    Path file = directory.resolve("config.json");
    ModConfig config = ModConfig.load(file);

    assertTrue(Files.exists(file));
    assertTrue(config.flag("grant-flight"));
    assertTrue(
        new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("\"palette\""));
  }

  @Test
  void resolvesPaletteTokensAndPlaceholders() throws Exception {
    Path file = directory.resolve("config.json");
    Files.write(
        file,
        ("{\"palette\":{\"primary\":\"&6\"},"
                + "\"messages\":{\"prefix\":\"{muted}[{primary}Vanish{muted}]{reset} \","
                + "\"enabled\":\"{accent}You are now vanished.\","
                + "\"status\":\"{muted}{player} {text}is {state}{text}.\"}}")
            .getBytes(StandardCharsets.UTF_8));

    ModConfig config = ModConfig.load(file);

    assertEquals(
        "\u00a78[\u00a76Vanish\u00a78]\u00a7r \u00a7aYou are now vanished.",
        config.message("enabled", "unused"));
    assertEquals(
        "\u00a78Staff \u00a7fis \u00a7avanished\u00a7f.",
        config.format(
            config.template("status", "unused", Collections.singletonMap("player", "Staff")),
            Collections.singletonMap("state", config.format("{accent}vanished", null))));
  }

  @Test
  void rejectsUnknownOptions() throws Exception {
    Path file = directory.resolve("config.json");
    Files.write(
        file,
        "{\"options\":{\"not-a-real-switch\":true}}".getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> ModConfig.load(file));
  }
}
