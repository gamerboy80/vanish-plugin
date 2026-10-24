package dev.vanish.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The complete persisted vanish state: hidden UUIDs and their saved baselines. */
public final class VanishState {
  public static final VanishState EMPTY =
      new VanishState(Collections.<UUID>emptySet(), Collections.<UUID, VanishBaseline>emptyMap());

  private final Set<UUID> vanished;
  private final Map<UUID, VanishBaseline> baselines;

  public VanishState(Set<UUID> vanished, Map<UUID, VanishBaseline> baselines) {
    this.vanished = Collections.unmodifiableSet(new LinkedHashSet<UUID>(vanished));
    this.baselines = Collections.unmodifiableMap(
        new LinkedHashMap<UUID, VanishBaseline>(baselines));
  }

  public Set<UUID> vanished() {
    return vanished;
  }

  public Map<UUID, VanishBaseline> baselines() {
    return baselines;
  }

  public VanishBaseline baseline(UUID id) {
    return baselines.get(id);
  }

  /**
   * Returns a new state with the hidden flag and baseline for one player.
   * Passing <code>null</code> as the baseline removes the saved baseline.
   */
  public VanishState update(UUID id, boolean hidden, VanishBaseline baseline) {
    Set<UUID> nextVanished = new LinkedHashSet<UUID>(vanished);
    Map<UUID, VanishBaseline> nextBaselines =
        new LinkedHashMap<UUID, VanishBaseline>(baselines);
    if (hidden) {
      nextVanished.add(id);
      if (baseline != null) {
        nextBaselines.put(id, baseline);
      }
    } else {
      nextVanished.remove(id);
      nextBaselines.remove(id);
    }
    return new VanishState(nextVanished, nextBaselines);
  }

  /** Returns a new state with all baselines cleared but visibility preserved. */
  public VanishState withoutBaselines() {
    return new VanishState(vanished, Collections.<UUID, VanishBaseline>emptyMap());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof VanishState)) {
      return false;
    }
    VanishState state = (VanishState) other;
    return vanished.equals(state.vanished) && baselines.equals(state.baselines);
  }

  @Override
  public int hashCode() {
    return 31 * vanished.hashCode() + baselines.hashCode();
  }
}
