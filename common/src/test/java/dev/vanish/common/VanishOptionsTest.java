package dev.vanish.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VanishOptionsTest {
  @Test
  void defaultsAreStableAndMissingKeysKeepTheDefault() {
    VanishOptions options = new VanishOptions(Collections.<String, Boolean>emptyMap());
    assertTrue(options.flag("grant-flight"));
    assertFalse(options.flag("prevent-attacking"));
    assertFalse(options.flag("default-chat-public"));
    assertTrue(VanishOptions.known("client-night-vision"));
  }

  @Test
  void unknownAndNonBooleanValuesAreRejected() {
    Map<String, Boolean> unknown = new LinkedHashMap<String, Boolean>();
    unknown.put("prevent-teleportation", Boolean.TRUE);
    assertThrows(IllegalArgumentException.class, () -> new VanishOptions(unknown));

    Map<String, Boolean> nullValue = new LinkedHashMap<String, Boolean>();
    nullValue.put("grant-flight", null);
    assertThrows(IllegalArgumentException.class, () -> new VanishOptions(nullValue));
  }

  @Test
  void overridesAreImmutableSnapshots() {
    Map<String, Boolean> change = new LinkedHashMap<String, Boolean>();
    change.put("prevent-attacking", Boolean.TRUE);
    VanishOptions options = new VanishOptions(change);
    assertTrue(options.flag("prevent-attacking"));
    assertEquals(Boolean.TRUE, options.values().get("prevent-attacking"));
  }
}
