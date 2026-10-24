package dev.vanish.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VanishScoreboardTest {
  private final UUID hidden = UUID.randomUUID();
  private final UUID viewer = UUID.randomUUID();
  private final UUID staff = UUID.randomUUID();
  private VanishView view;

  @BeforeEach
  void setup() {
    TestPacketEvents.install();
    Map<UUID, VanishView.Identity> identities =
        new LinkedHashMap<UUID, VanishView.Identity>();
    identities.put(hidden, new VanishView.Identity(hidden, "Alice", 17));
    Set<UUID> staffIds = new LinkedHashSet<UUID>(Arrays.asList(staff));
    view =
        new VanishView(
            identities,
            staffIds,
            Collections.<UUID>emptySet(),
            Collections.<VanishView.Identity>emptyList());
  }

  @Test
  void scoresHideOnToggleAndRestoreOriginalOnRevealOrPermissionChange() {
    VanishScoreboard board = new VanishScoreboard();
    assertFalse(board.score(score("Alice", null), VanishView.EMPTY, viewer));
    assertTrue(hasReset(board.projection(view, viewer)));
    assertTrue(hasScore(board.projection(VanishView.EMPTY, viewer)));
    assertTrue(hasScore(board.projection(view, staff)));
    board.reset(new WrapperPlayServerResetScore("Alice", "sidebar"));
    assertTrue(board.projection(VanishView.EMPTY, viewer).isEmpty());
  }

  @Test
  void teamFilteringNeverMutatesSharedOriginalOrOtherViewers() {
    VanishScoreboard board = new VanishScoreboard();
    ScoreBoardTeamInfo info = info("[Vanished] Alice", "");
    WrapperPlayServerTeams original =
        new WrapperPlayServerTeams(
            "staff", TeamMode.CREATE, info, Arrays.asList("Alice", "Bob"));
    WrapperPlayServerTeams filtered = board.team(original, view, viewer);
    assertEquals(Collections.singletonList("Bob"), filtered.getPlayers());
    assertEquals(Component.empty(), filtered.getTeamInfo().get().getPrefix());
    assertEquals(Component.text("[Vanished] Alice"), info.getPrefix());
    assertEquals(Arrays.asList("Alice", "Bob"), original.getPlayers());

    WrapperPlayServerTeams allowed = new VanishScoreboard().team(original, view, staff);
    assertEquals(Arrays.asList("Alice", "Bob"), allowed.getPlayers());
    assertEquals(info.getPrefix(), allowed.getTeamInfo().get().getPrefix());
  }

  @Test
  void splitRowsAndMetadataMentionsAreFiltered() {
    VanishScoreboard board = new VanishScoreboard();
    WrapperPlayServerTeams original =
        new WrapperPlayServerTeams(
            "row", TeamMode.CREATE, info("Al", "ce"), Collections.singletonList("i"));
    WrapperPlayServerTeams filtered = board.team(original, view, viewer);
    assertEquals(Component.empty(), filtered.getTeamInfo().get().getPrefix());
    assertTrue(board.score(score("i", null), view, viewer));

    Component metadata =
        Component.text("Details")
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/tp Alice"));
    assertTrue(view.mentions(viewer, metadata));
    assertFalse(view.mentions(staff, metadata));
  }

  @Test
  void objectiveDeletionCannotResurrectStaleRows() {
    VanishScoreboard board = new VanishScoreboard();
    board.team(
        new WrapperPlayServerTeams(
            "old", TeamMode.CREATE, info("Alice", ""), Collections.singletonList("\u00a70")),
        view,
        viewer);
    board.score(score("\u00a70", null), view, viewer);
    board.objective(
        new WrapperPlayServerScoreboardObjective(
            "sidebar",
            WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
            Component.empty(),
            null),
        view,
        viewer);
    assertTrue(board.scoreProjection(VanishView.EMPTY, viewer).isEmpty());
  }

  private static boolean hasReset(List<PacketWrapper<?>> packets) {
    for (PacketWrapper<?> packet : packets) {
      if (packet instanceof WrapperPlayServerResetScore) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasScore(List<PacketWrapper<?>> packets) {
    for (PacketWrapper<?> packet : packets) {
      if (packet instanceof WrapperPlayServerUpdateScore) {
        return true;
      }
    }
    return false;
  }

  private static WrapperPlayServerUpdateScore score(String owner, Component display) {
    return new WrapperPlayServerUpdateScore(
        owner,
        WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
        "sidebar",
        5,
        display,
        null);
  }

  private static ScoreBoardTeamInfo info(String prefix, String suffix) {
    return new ScoreBoardTeamInfo(
        Component.text("Team"),
        Component.text(prefix),
        Component.text(suffix),
        WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
        WrapperPlayServerTeams.CollisionRule.ALWAYS,
        NamedTextColor.WHITE,
        WrapperPlayServerTeams.OptionData.NONE);
  }
}
