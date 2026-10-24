package dev.vanish.bukkit;

import dev.vanish.common.VanishOptions;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validated configuration snapshot; a reload never mutates the active rules. */
public final class VanishSettings {
  public static final String DEFAULT_PREFIX = "{muted}[{primary}Vanish{muted}]{reset} ";
  private static final Map<String, String> DEFAULT_PALETTE = defaultPalette();

  private final VanishOptions options;
  private final String prefix;
  private final Map<String, String> palette;
  private final YamlConfiguration raw;

  private VanishSettings(YamlConfiguration raw) {
    this.raw = raw;
    Map<String, Boolean> configured = new LinkedHashMap<String, Boolean>();
    ConfigurationSection section = raw.getConfigurationSection("vanish");
    if (section != null) {
      for (String name : VanishOptions.names()) {
        if (!section.isSet(name)) {
          continue;
        }
        if (!section.isBoolean(name)) {
          throw new IllegalArgumentException("vanish." + name + " must be a boolean");
        }
        configured.put(name, Boolean.valueOf(section.getBoolean(name)));
      }
    }
    options = new VanishOptions(configured);
    palette = loadPalette(raw);
    prefix = format(raw.getString("messages.prefix", DEFAULT_PREFIX), null);
  }

  private static Map<String, String> defaultPalette() {
    Map<String, String> colors = new LinkedHashMap<String, String>();
    colors.put("primary", "&d");
    colors.put("secondary", "&5");
    colors.put("accent", "&a");
    colors.put("success", "&a");
    colors.put("warning", "&e");
    colors.put("error", "&c");
    colors.put("muted", "&8");
    colors.put("text", "&f");
    colors.put("reset", "&r");
    return Collections.unmodifiableMap(colors);
  }

  private static Map<String, String> loadPalette(YamlConfiguration raw) {
    Map<String, String> colors = new LinkedHashMap<String, String>(DEFAULT_PALETTE);
    ConfigurationSection section = raw.getConfigurationSection("palette");
    if (section == null) {
      return colors;
    }
    for (String name : DEFAULT_PALETTE.keySet()) {
      if (!section.isSet(name)) {
        continue;
      }
      String value = section.getString(name);
      if (value == null) {
        throw new IllegalArgumentException("palette." + name + " must be a color string");
      }
      colors.put(name, value);
    }
    return Collections.unmodifiableMap(colors);
  }

  /** Validates and wraps an in-memory config document. */
  static VanishSettings from(YamlConfiguration yaml) {
    return new VanishSettings(yaml);
  }

  /** Loads a fresh snapshot from disk. */
  public static VanishSettings load(File file) throws IOException {
    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.load(file);
    } catch (InvalidConfigurationException error) {
      throw new IOException("Invalid config.yml", error);
    }
    try {
      return new VanishSettings(yaml);
    } catch (IllegalArgumentException error) {
      throw new IOException(error.getMessage(), error);
    }
  }

  public VanishOptions options() {
    return options;
  }

  public boolean flag(String name) {
    return options.flag(name);
  }

  public String prefix() {
    return prefix;
  }

  public String message(String key, String fallback) {
    return message(key, fallback, null);
  }

  /**
   * Resolves {@code {palette}} tokens and named placeholders in one message.
   * Palette keys can be overridden under {@code palette:} in config.yml.
   */
  public String message(String key, String fallback, Map<String, String> placeholders) {
    return prefix + format(raw.getString("messages." + key, fallback), placeholders);
  }

  /** Resolves a configured message without adding the prefix (for error throws). */
  public String template(String key, String fallback, Map<String, String> placeholders) {
    return format(raw.getString("messages." + key, fallback), placeholders);
  }

  /** Resolves palette tokens and named placeholders without adding the prefix. */
  public String format(String template, Map<String, String> placeholders) {
    String text = template == null ? "" : template;
    text = replace(text, palette);
    if (placeholders != null) {
      text = replace(text, placeholders);
    }
    // Placeholder values may themselves contain palette tokens.
    return color(replace(text, palette));
  }

  private static String replace(String text, Map<String, String> values) {
    String result = text;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  /** Returns the raw config for an option write. */
  public YamlConfiguration raw() {
    return raw;
  }

  /** Converts ampersand color codes to the section-sign form used by Bukkit. */
  public static String color(String text) {
    return text == null ? "" : text.replace('&', '\u00a7');
  }
}
