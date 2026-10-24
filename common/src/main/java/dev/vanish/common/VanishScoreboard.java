package dev.vanish.common;

import com.github.retrooper.packetevents.protocol.score.FixedScoreFormat;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Per-connection scoreboard originals and filtered projections, including split
 * text rows. Every public method is synchronized because teams, objectives and
 * scores are touched from several packet threads.
 */
public final class VanishScoreboard {
  private static final class ScoreKey {
    private final String owner;
    private final String objective;

    private ScoreKey(String owner, String objective) {
      this.owner = owner;
      this.objective = objective;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ScoreKey)) {
        return false;
      }
      ScoreKey key = (ScoreKey) other;
      return owner.equals(key.owner)
          && (objective == null ? key.objective == null : objective.equals(key.objective));
    }

    @Override
    public int hashCode() {
      return 31 * owner.hashCode() + (objective == null ? 0 : objective.hashCode());
    }
  }

  private static final class Team {
    private final ScoreBoardTeamInfo info;
    private final Set<String> members;

    private Team(ScoreBoardTeamInfo info, Set<String> members) {
      this.info = info;
      this.members = members;
    }
  }

  private final Map<String, Team> teams = new HashMap<String, Team>();
  private final Map<ScoreKey, WrapperPlayServerUpdateScore> scores =
      new HashMap<ScoreKey, WrapperPlayServerUpdateScore>();
  private final Map<String, WrapperPlayServerScoreboardObjective> objectives =
      new HashMap<String, WrapperPlayServerScoreboardObjective>();

  /** Applies one team packet and returns the filtered version for this viewer. */
  public synchronized WrapperPlayServerTeams team(
      WrapperPlayServerTeams source, VanishView view, UUID viewer) {
    String name = source.getTeamName();
    Team previous = teams.get(name);
    Set<String> members =
        new HashSet<String>(previous == null ? new HashSet<String>() : previous.members);
    ScoreBoardTeamInfo info = previous == null ? null : previous.info;

    switch (source.getTeamMode()) {
      case CREATE:
        members = new HashSet<String>(source.getPlayers());
        info = source.getTeamInfo().isPresent() ? copyInfo(source.getTeamInfo().get()) : null;
        break;
      case UPDATE:
        info = source.getTeamInfo().isPresent() ? copyInfo(source.getTeamInfo().get()) : info;
        break;
      case ADD_ENTITIES:
        members.addAll(source.getPlayers());
        break;
      case REMOVE_ENTITIES:
        members.removeAll(source.getPlayers());
        break;
      case REMOVE:
        teams.remove(name);
        break;
      default:
        throw new IllegalArgumentException("Unknown team operation");
    }
    if (source.getTeamMode() != TeamMode.REMOVE) {
      // Membership is exclusive: a move implicitly removes an entry from its old team.
      if (source.getTeamMode() == TeamMode.CREATE
          || source.getTeamMode() == TeamMode.ADD_ENTITIES) {
        for (Map.Entry<String, Team> entry : teams.entrySet()) {
          if (!entry.getKey().equals(name)) {
            entry.getValue().members.removeAll(source.getPlayers());
          }
        }
      }
      teams.put(name, new Team(info, members));
    }
    List<String> visible = new ArrayList<String>();
    for (String member : source.getPlayers()) {
      if (!view.mentions(viewer, member)) {
        visible.add(member);
      }
    }
    Set<String> teamMembers = new HashSet<String>(members);
    ScoreBoardTeamInfo filtered =
        source.getTeamInfo().isPresent()
            ? filteredInfo(source.getTeamInfo().get(), teamMembers, view, viewer)
            : null;
    return new WrapperPlayServerTeams(name, source.getTeamMode(), filtered, visible);
  }

  /** Stores a score and reports whether it must be hidden from this viewer. */
  public synchronized boolean score(
      WrapperPlayServerUpdateScore source, VanishView view, UUID viewer) {
    scores.put(new ScoreKey(source.getEntityName(), source.getObjectiveName()), copyScore(source));
    return hiddenScore(source, view, viewer);
  }

  /** Forget a score the server removed. */
  public synchronized void reset(WrapperPlayServerResetScore source) {
    Set<ScoreKey> stale = new HashSet<ScoreKey>();
    for (ScoreKey key : scores.keySet()) {
      if (key.owner.equals(source.getTargetName())
          && (source.getObjective() == null || key.objective.equals(source.getObjective()))) {
        stale.add(key);
      }
    }
    for (ScoreKey key : stale) {
      scores.remove(key);
    }
  }

  /** Applies one objective packet and returns the filtered version. */
  public synchronized WrapperPlayServerScoreboardObjective objective(
      WrapperPlayServerScoreboardObjective source, VanishView view, UUID viewer) {
    if (source.getMode() == WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE) {
      objectives.remove(source.getName());
      Set<ScoreKey> stale = new HashSet<ScoreKey>();
      for (ScoreKey key : scores.keySet()) {
        if (key.objective.equals(source.getName())) {
          stale.add(key);
        }
      }
      for (ScoreKey key : stale) {
        scores.remove(key);
      }
    } else {
      objectives.put(source.getName(), copyObjective(source));
    }
    WrapperPlayServerScoreboardObjective output = copyObjective(source);
    if (view.mentions(viewer, output.getDisplayName())) {
      output.setDisplayName(Component.empty());
    }
    if (formatMentions(output.getScoreFormat(), view, viewer)) {
      output.setScoreFormat(null);
    }
    return output;
  }

  /** Replays client projections without mutating plugin-owned scoreboards. */
  public synchronized List<PacketWrapper<?>> projection(VanishView view, UUID viewer) {
    List<PacketWrapper<?>> output = new ArrayList<PacketWrapper<?>>();
    for (WrapperPlayServerScoreboardObjective original : objectives.values()) {
      WrapperPlayServerScoreboardObjective update = copyObjective(original);
      update.setMode(WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE);
      if (view.mentions(viewer, update.getDisplayName())) {
        update.setDisplayName(Component.empty());
      }
      if (formatMentions(update.getScoreFormat(), view, viewer)) {
        update.setScoreFormat(null);
      }
      output.add(update);
    }
    for (Map.Entry<String, Team> entry : teams.entrySet()) {
      Team team = entry.getValue();
      if (team.info != null) {
        output.add(
            new WrapperPlayServerTeams(
                entry.getKey(),
                TeamMode.UPDATE,
                filteredInfo(team.info, team.members, view, viewer),
                new ArrayList<String>()));
      }
      // Remove originals first, then add only the permitted membership.
      output.add(
          new WrapperPlayServerTeams(
              entry.getKey(),
              TeamMode.REMOVE_ENTITIES,
              (ScoreBoardTeamInfo) null,
              new ArrayList<String>(team.members)));
      List<String> allowed = new ArrayList<String>();
      for (String member : team.members) {
        if (!view.mentions(viewer, member)) {
          allowed.add(member);
        }
      }
      output.add(
          new WrapperPlayServerTeams(
              entry.getKey(), TeamMode.ADD_ENTITIES, (ScoreBoardTeamInfo) null, allowed));
    }
    output.addAll(scoreProjection(view, viewer));
    return output;
  }

  /** Replays only the score rows. */
  public synchronized List<PacketWrapper<?>> scoreProjection(VanishView view, UUID viewer) {
    List<PacketWrapper<?>> output = new ArrayList<PacketWrapper<?>>();
    for (WrapperPlayServerUpdateScore score : scores.values()) {
      if (hiddenScore(score, view, viewer)) {
        output.add(
            new WrapperPlayServerResetScore(score.getEntityName(), score.getObjectiveName()));
      } else {
        output.add(copyScore(score));
      }
    }
    return output;
  }

  private boolean hiddenScore(
      WrapperPlayServerUpdateScore score, VanishView view, UUID viewer) {
    if (view.mentions(viewer, score.getEntityName())
        || view.mentions(viewer, score.getEntityDisplayName())
        || formatMentions(score.getScoreFormat(), view, viewer)) {
      return true;
    }
    for (Team team : teams.values()) {
      if (team.info != null && team.members.contains(score.getEntityName())) {
        String rendered =
            plain(team.info.getPrefix())
                + score.getEntityName()
                + plain(team.info.getSuffix());
        if (view.mentions(viewer, rendered)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean formatMentions(ScoreFormat format, VanishView view, UUID viewer) {
    return format instanceof FixedScoreFormat
        && view.mentions(viewer, ((FixedScoreFormat) format).getValue());
  }

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static ScoreBoardTeamInfo filteredInfo(
      ScoreBoardTeamInfo original, Set<String> members, VanishView view, UUID viewer) {
    ScoreBoardTeamInfo copy = copyInfo(original);
    if (view.mentions(viewer, copy.getDisplayName())) {
      copy.setDisplayName(Component.empty());
    }
    boolean mentionsMember = false;
    for (String member : members) {
      if (view.mentions(viewer, plain(copy.getPrefix()) + member + plain(copy.getSuffix()))) {
        mentionsMember = true;
        break;
      }
    }
    if (view.mentions(viewer, copy.getPrefix())
        || view.mentions(viewer, copy.getSuffix())
        || view.mentions(viewer, plain(copy.getPrefix()) + plain(copy.getSuffix()))
        || mentionsMember) {
      copy.setPrefix(Component.empty());
      copy.setSuffix(Component.empty());
    }
    return copy;
  }

  private static ScoreBoardTeamInfo copyInfo(ScoreBoardTeamInfo source) {
    return new ScoreBoardTeamInfo(
        source.getDisplayName(),
        source.getPrefix(),
        source.getSuffix(),
        source.getTagVisibility(),
        source.getCollisionRule(),
        source.getColor(),
        source.getOptionData());
  }

  private static WrapperPlayServerUpdateScore copyScore(WrapperPlayServerUpdateScore source) {
    return new WrapperPlayServerUpdateScore(
        source.getEntityName(),
        WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
        source.getObjectiveName(),
        source.getValue().isPresent() ? source.getValue().get().intValue() : 0,
        source.getEntityDisplayName(),
        source.getScoreFormat());
  }

  private static WrapperPlayServerScoreboardObjective copyObjective(
      WrapperPlayServerScoreboardObjective source) {
    return new WrapperPlayServerScoreboardObjective(
        source.getName(),
        source.getMode(),
        source.getDisplayName(),
        source.getRenderType(),
        source.getScoreFormat());
  }
}
