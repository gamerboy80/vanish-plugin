package dev.vanish.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class VanishSettingsTest {
  @Test
  void paletteTokensResolveInPrefixAndMessages() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("palette.primary", "&6");
    yaml.set("messages.prefix", "{muted}[{primary}Vanish{muted}]{reset} ");
    yaml.set("messages.enabled", "{accent}You are now vanished.");

    VanishSettings settings = VanishSettings.from(yaml);

    assertEquals(
        "\u00a78[\u00a76Vanish\u00a78]\u00a7r " +
            "\u00a7aYou are now vanished.",
        settings.message("enabled", "unused"));
  }

  @Test
  void namedPlaceholdersAndTheirPaletteTokensResolve() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("messages.prefix", "");
    yaml.set("messages.status", "{muted}{player} {text}is {state}{text}.");
    VanishSettings settings = VanishSettings.from(yaml);

    String message =
        settings.message(
            "status",
            "unused",
            Collections.singletonMap("player", "Staff"));

    assertEquals("\u00a78Staff \u00a7fis {state}\u00a7f.", message);

    Map<String, String> placeholders = new LinkedHashMap<String, String>();
    placeholders.put("player", "Staff");
    placeholders.put("state", settings.format("{accent}vanished", null));
    assertEquals(
        "\u00a78Staff \u00a7fis \u00a7avanished\u00a7f.",
        settings.format("{muted}{player} {text}is {state}{text}.", placeholders));
  }
}
