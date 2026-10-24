package dev.vanish.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validated, immutable view of the plugin's behavioural switches.
 *
 * <p>Defaults live here rather than in the loader so every platform enforces the
 * same policy, and a missing key from an older config keeps the documented
 * default instead of failing open.
 */
public final class VanishOptions {
  /** Guards flipped together by <code>/vanish interactions</code>. */
  public static final List<String> INTERACTION_GUARDS;

  private static final Map<String, Boolean> DEFAULTS;

  static {
    Map<String, Boolean> defaults = new LinkedHashMap<String, Boolean>();
    defaults.put("grant-flight", Boolean.TRUE);
    defaults.put("client-night-vision", Boolean.TRUE);
    defaults.put("action-bar", Boolean.TRUE);
    defaults.put("fake-join-quit", Boolean.TRUE);
    defaults.put("hide-from-server-list", Boolean.TRUE);
    defaults.put("hide-from-query", Boolean.TRUE);
    defaults.put("prevent-incoming-damage", Boolean.TRUE);
    defaults.put("prevent-hunger", Boolean.TRUE);
    defaults.put("prevent-xp-pickup", Boolean.TRUE);
    defaults.put("prevent-mob-targeting", Boolean.TRUE);
    defaults.put("prevent-collision", Boolean.TRUE);
    defaults.put("prevent-spawning", Boolean.TRUE);
    defaults.put("ignore-sleep", Boolean.TRUE);
    defaults.put("prevent-gliding", Boolean.TRUE);
    defaults.put("prevent-physical-triggers", Boolean.TRUE);
    defaults.put("prevent-vibrations", Boolean.TRUE);
    defaults.put("prevent-raids", Boolean.TRUE);
    defaults.put("silent-entities", Boolean.TRUE);
    defaults.put("silent-block-actions", Boolean.TRUE);
    defaults.put("silent-container-inspection", Boolean.TRUE);
    defaults.put("retain-death-inventory", Boolean.TRUE);
    defaults.put("default-item-pickup", Boolean.FALSE);
    defaults.put("default-chat-public", Boolean.FALSE);
    defaults.put("prevent-attacking", Boolean.FALSE);
    defaults.put("prevent-block-breaking", Boolean.FALSE);
    defaults.put("prevent-block-placing", Boolean.FALSE);
    defaults.put("prevent-world-interactions", Boolean.FALSE);
    defaults.put("prevent-entity-interactions", Boolean.FALSE);
    defaults.put("prevent-item-drop", Boolean.FALSE);
    defaults.put("prevent-projectiles", Boolean.FALSE);
    defaults.put("prevent-buckets", Boolean.FALSE);
    defaults.put("prevent-item-consumption", Boolean.FALSE);
    defaults.put("prevent-mounting", Boolean.FALSE);
    defaults.put("prevent-bed-use", Boolean.FALSE);
    defaults.put("prevent-inventory-access", Boolean.FALSE);
    DEFAULTS = Collections.unmodifiableMap(defaults);

    List<String> guards = new ArrayList<String>();
    guards.add("prevent-attacking");
    guards.add("prevent-block-breaking");
    guards.add("prevent-block-placing");
    guards.add("prevent-world-interactions");
    guards.add("prevent-entity-interactions");
    guards.add("prevent-item-drop");
    guards.add("prevent-projectiles");
    guards.add("prevent-buckets");
    guards.add("prevent-item-consumption");
    guards.add("prevent-mounting");
    guards.add("prevent-bed-use");
    guards.add("prevent-inventory-access");
    guards.add("prevent-physical-triggers");
    guards.add("prevent-vibrations");
    guards.add("prevent-raids");
    guards.add("prevent-xp-pickup");
    guards.add("prevent-gliding");
    INTERACTION_GUARDS = Collections.unmodifiableList(guards);
  }

  private final Map<String, Boolean> values;

  /**
   * Builds an option snapshot.
   *
   * @param configured values read from the loader config; unknown keys are
   *     rejected so a typo cannot silently disable a guard
   */
  public VanishOptions(Map<String, Boolean> configured) {
    Map<String, Boolean> next = new LinkedHashMap<String, Boolean>(DEFAULTS);
    if (configured != null) {
      for (Map.Entry<String, Boolean> entry : configured.entrySet()) {
        if (!DEFAULTS.containsKey(entry.getKey())) {
          throw new IllegalArgumentException("Unknown vanish option: " + entry.getKey());
        }
        if (entry.getValue() == null) {
          throw new IllegalArgumentException("Vanish option must be a boolean: " + entry.getKey());
        }
        next.put(entry.getKey(), entry.getValue());
      }
    }
    values = Collections.unmodifiableMap(next);
  }

  /** Returns the switch value; unknown names are a programming error. */
  public boolean flag(String name) {
    Boolean value = values.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Unknown vanish option: " + name);
    }
    return value.booleanValue();
  }

  /** Returns every option in stable order. */
  public Map<String, Boolean> values() {
    return values;
  }

  /** Returns every option name in stable order. */
  public static List<String> names() {
    return Collections.unmodifiableList(new ArrayList<String>(DEFAULTS.keySet()));
  }

  /** Whether a name is part of the public option vocabulary. */
  public static boolean known(String name) {
    return DEFAULTS.containsKey(name);
  }

  /** Default value for a known option. */
  public static boolean defaultValue(String name) {
    Boolean value = DEFAULTS.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Unknown vanish option: " + name);
    }
    return value.booleanValue();
  }
}
