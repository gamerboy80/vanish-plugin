package dev.vanish.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VanishActionsTest {
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID VIEWER = UUID.randomUUID();
  private static final UUID WORLD = UUID.randomUUID();

  private static VanishActions actions() {
    VanishActions actions = new VanishActions();
    Map<UUID, UUID> worlds = new HashMap<UUID, UUID>();
    worlds.put(ACTOR, WORLD);
    worlds.put(VIEWER, WORLD);
    actions.worlds(worlds);
    return actions;
  }

  @Test
  void actorIsReportedForOtherViewersInTheSameWorld() {
    VanishActions actions = actions();
    actions.block(ACTOR, WORLD, 10, 64, -3);

    assertEquals(ACTOR, actions.actorAt(VIEWER, 10.9, 64.5, -3.0));
    assertEquals(ACTOR, actions.actorAt(VIEWER, 10, 64, -3));
  }

  @Test
  void actorIsNotReportedToTheActor() {
    VanishActions actions = actions();
    actions.block(ACTOR, WORLD, 10, 64, -3);

    assertNull(actions.actorAt(ACTOR, 10, 64, -3));
  }

  @Test
  void differentWorldAndPositionDoNotMatch() {
    VanishActions actions = actions();
    actions.block(ACTOR, WORLD, 10, 64, -3);

    assertNull(actions.actorAt(VIEWER, 11, 64, -3));
    assertNull(actions.actorAt(VIEWER, 10, 63, -3));

    Map<UUID, UUID> otherWorld = new HashMap<UUID, UUID>();
    otherWorld.put(VIEWER, UUID.randomUUID());
    actions.worlds(otherWorld);
    assertNull(actions.actorAt(VIEWER, 10, 64, -3));
  }

  @Test
  void actionsExpire() throws InterruptedException {
    VanishActions actions = actions();
    Map<UUID, UUID> worlds = Collections.emptyMap();
    actions.worlds(worlds);
    actions.block(ACTOR, WORLD, 10, 64, -3);

    Thread.sleep(450L);
    Map<UUID, UUID> viewerWorld = new HashMap<UUID, UUID>();
    viewerWorld.put(VIEWER, WORLD);
    actions.worlds(viewerWorld);
    assertNull(actions.actorAt(VIEWER, 10, 64, -3));
  }
}
