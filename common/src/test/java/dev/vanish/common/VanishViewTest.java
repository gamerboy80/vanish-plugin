package dev.vanish.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class VanishViewTest {
  private final UUID hidden = UUID.randomUUID();
  private final UUID viewer = UUID.randomUUID();
  private final UUID staff = UUID.randomUUID();

  private VanishView view(Set<UUID> chatPublic) {
    Map<UUID, VanishView.Identity> identities =
        new LinkedHashMap<UUID, VanishView.Identity>();
    identities.put(hidden, new VanishView.Identity(hidden, "Alice", 17));
    Set<UUID> staffIds = new LinkedHashSet<UUID>(Arrays.asList(staff));
    return new VanishView(
        identities,
        staffIds,
        chatPublic,
        Collections.<VanishView.Identity>emptyList());
  }

  @Test
  void policySeparatesSelfStaffPublicAndUnrelatedNames() {
    VanishView policy = view(Collections.<UUID>emptySet());
    assertTrue(policy.hides(viewer, hidden));
    assertFalse(policy.hides(hidden, hidden));
    assertFalse(policy.hides(staff, hidden));
    assertTrue(policy.hides(null, hidden));
    assertTrue(policy.hidesEntity(viewer, 17));
    assertFalse(policy.hidesEntity(viewer, 18));
    assertTrue(policy.mentions(viewer, "\u00a76[#1] \u00a7aALICE\u00a7r: 5"));
    assertTrue(policy.mentions(viewer, hidden.toString()));
    assertFalse(policy.mentions(viewer, "Malice and Alice_2"));
    assertFalse(policy.mentions(staff, "Alice"));
  }

  @Test
  void publicChatOnlyRelaxesChatMentions() {
    VanishView policy = view(Collections.singleton(hidden));
    assertTrue(policy.mentions(viewer, Component.text("Alice: hi")));
    assertFalse(policy.mentionsChat(viewer, Component.text("Alice: hi")));
    assertTrue(policy.mentionsChat(viewer, hidden.toString()));
  }
}
