package dev.vanish.mod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanish.common.VanishOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated configuration snapshot for the mod loaders. */
public final class ModConfig {
  private static final String DEFAULT_PREFIX = "{muted}[{primary}Vanish{muted}]{reset} ";
  private static final Map<String, String> DEFAULT_PALETTE = defaultPalette();

  private final VanishOptions options;
  private final String prefix;
  private final Map<String, String> palette;
  private final boolean autoVanishStaff;
  private final Map<String, String> messages;

  private ModConfig(
      VanishOptions options,
      String prefix,
      Map<String, String> palette,
      boolean autoVanishStaff,
      Map<String, String> messages) {
    this.options = options;
    this.palette = palette;
    this.prefix = format(prefix, null);
    this.autoVanishStaff = autoVanishStaff;
    this.messages = messages;
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
    return colors;
  }

  private static Map<String, String> loadPalette(JsonObject root) {
    Map<String, String> colors = new LinkedHashMap<String, String>(DEFAULT_PALETTE);
    JsonObject configured = object(root, "palette");
    for (String name : DEFAULT_PALETTE.keySet()) {
      JsonElement value = configured.get(name);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
        colors.put(name, value.getAsString());
      }
    }
    return colors;
  }

  /** Loads the file, writing documented defaults the first time. */
  public static ModConfig load(Path file) throws IOException {
    if (!Files.exists(file)) {
      ModConfig defaults = defaults();
      Files.createDirectories(file.getParent());
      Files.write(
          file,
          new GsonBuilder().setPrettyPrinting().create().toJson(defaults.toJson()).getBytes(StandardCharsets.UTF_8));
      return defaults;
    }
    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
      root = parsed.getAsJsonObject();
    } catch (RuntimeException error) {
      throw new IOException("Invalid config.json", error);
    }

    Map<String, Boolean> configured = new LinkedHashMap<String, Boolean>();
    JsonObject optionsJson = object(root, "options");
    for (Map.Entry<String, JsonElement> entry : optionsJson.entrySet()) {
      if (!VanishOptions.known(entry.getKey())) {
        throw new IOException("Unknown vanish option: " + entry.getKey());
      }
      if (!entry.getValue().isJsonPrimitive()
          || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
        throw new IOException("Vanish option must be a boolean: " + entry.getKey());
      }
      configured.put(entry.getKey(), Boolean.valueOf(entry.getValue().getAsBoolean()));
    }
    VanishOptions options;
    try {
      options = new VanishOptions(configured);
    } catch (IllegalArgumentException error) {
      throw new IOException(error.getMessage(), error);
    }

    Map<String, String> messages = new LinkedHashMap<String, String>();
    JsonObject messagesJson = object(root, "messages");
    for (Map.Entry<String, JsonElement> entry : messagesJson.entrySet()) {
      if (entry.getValue().isJsonPrimitive()
          && entry.getValue().getAsJsonPrimitive().isString()) {
        messages.put(entry.getKey(), entry.getValue().getAsString());
      }
    }
    boolean auto =
        root.has("auto-vanish-staff")
            && root.get("auto-vanish-staff").isJsonPrimitive()
            && root.get("auto-vanish-staff").getAsBoolean();
    String prefix =
        messages.containsKey("prefix") ? messages.get("prefix") : DEFAULT_PREFIX;
    return new ModConfig(options, prefix, loadPalette(root), auto, messages);
  }

  private static JsonObject object(JsonObject root, String name) {
    JsonElement element = root.get(name);
    return element != null && element.isJsonObject()
        ? element.getAsJsonObject()
        : new JsonObject();
  }

  private static ModConfig defaults() {
    Map<String, String> messages = new LinkedHashMap<String, String>();
    messages.put("prefix", DEFAULT_PREFIX);
    messages.put("enabled", "{accent}You are now vanished.");
    messages.put("disabled", "{primary}You are visible again.");
    messages.put("joined-vanished", "{accent}You joined vanished.");
    messages.put("status-vanished", "{muted}{player} {text}is {accent}vanished{text}.");
    messages.put("status-visible", "{muted}{player} {text}is {primary}visible{text}.");
    messages.put("list", "{muted}Vanished ({count}{muted}): {accent}{players}");
    messages.put("list-empty", "{muted}No vanished staff online.");
    messages.put("reloaded", "{success}Configuration reloaded.");
    messages.put("target-vanished", "{muted}{player} {text}is now {accent}vanished{text}.");
    messages.put("target-visible", "{muted}{player} {text}is now {primary}visible{text}.");
    messages.put("staff-vanished", "{muted}{player} {text}vanished.");
    messages.put("staff-visible", "{muted}{player} {text}became visible.");
    messages.put("fake-quit", "{muted}{player} left the game");
    messages.put("fake-join", "{muted}{player} joined the game");
    messages.put("permission", "{error}You need operator permission.");
    messages.put("player-only", "{error}Run this command as a player.");
    messages.put("unknown-player", "{error}Specify an online player.");
    messages.put("save-failed", "{error}Vanish state could not be saved; contact an admin.");
    return new ModConfig(
        new VanishOptions(null),
        DEFAULT_PREFIX,
        new LinkedHashMap<String, String>(DEFAULT_PALETTE),
        false,
        messages);
  }

  private JsonObject toJson() {
    JsonObject root = new JsonObject();
    JsonObject optionsJson = new JsonObject();
    for (Map.Entry<String, Boolean> entry : options.values().entrySet()) {
      optionsJson.addProperty(entry.getKey(), entry.getValue());
    }
    root.add("options", optionsJson);

    JsonObject messagesJson = new JsonObject();
    for (Map.Entry<String, String> entry : messages.entrySet()) {
      messagesJson.addProperty(entry.getKey(), entry.getValue());
    }
    root.add("messages", messagesJson);

    JsonObject paletteJson = new JsonObject();
    for (Map.Entry<String, String> entry : palette.entrySet()) {
      paletteJson.addProperty(entry.getKey(), entry.getValue());
    }
    root.add("palette", paletteJson);
    root.addProperty("auto-vanish-staff", Boolean.valueOf(autoVanishStaff));
    return root;
  }

  public VanishOptions options() {
    return options;
  }

  public boolean flag(String name) {
    return options.flag(name);
  }

  public boolean autoVanishStaff() {
    return autoVanishStaff;
  }

  public String prefix() {
    return prefix;
  }

  public String message(String key, String fallback) {
    return message(key, fallback, null);
  }

  public String message(String key, String fallback, Map<String, String> placeholders) {
    String value = messages.get(key);
    return prefix() + format(value == null ? fallback : value, placeholders);
  }

  /** Resolves a message without the prefix, for command feedback built in code. */
  public String template(String key, String fallback, Map<String, String> placeholders) {
    String value = messages.get(key);
    return format(value == null ? fallback : value, placeholders);
  }

  /** Resolves palette tokens and named placeholders, then legacy colour codes. */
  public String format(String template, Map<String, String> placeholders) {
    String text = template == null ? "" : template;
    text = replace(text, palette);
    if (placeholders != null) {
      text = replace(text, placeholders);
    }
    return color(replace(text, palette));
  }

  private static String replace(String text, Map<String, String> values) {
    String result = text;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  /** Converts ampersand colour codes to the section-sign form. */
  public static String color(String text) {
    return text == null ? "" : text.replace('&', '\u00a7');
  }
}
