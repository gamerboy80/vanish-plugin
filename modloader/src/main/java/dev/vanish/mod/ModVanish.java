package dev.vanish.mod;

import dev.vanish.common.VanishActions;
import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishState;
import dev.vanish.common.VanishView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Main-thread vanish lifecycle for the mod loaders. The visibility policy is the
 * same immutable {@link VanishView} the Bukkit loader uses; only the platform
 * adapter differs. Packet work is delegated to {@link ModPacketBridge}.
 */
public final class ModVanish {
  private static final class Pair {
    private final UUID viewer;
    private final UUID subject;

    private Pair(UUID viewer, UUID subject) {
      this.viewer = viewer;
      this.subject = subject;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Pair)) {
        return false;
      }
      Pair pair = (Pair) other;
      return viewer.equals(pair.viewer) && subject.equals(pair.subject);
    }

    @Override
    public int hashCode() {
      return 31 * viewer.hashCode() + subject.hashCode();
    }
  }

  private final ModPlatform platform;
  private final ModStateStore store;
  private final Path configFile;
  private final VanishActions actions = new VanishActions();
  private final ModPacketBridge packets;
  private final ModVisibility visibility;
  private final Map<UUID, ServerPlayer> online = new LinkedHashMap<UUID, ServerPlayer>();
  private final Set<Pair> hiddenPairs = new HashSet<Pair>();

  private volatile ModConfig config;
  private volatile VanishState state = VanishState.EMPTY;
  private volatile VanishView view = VanishView.EMPTY;
  private int ticks;

  public ModVanish(ModPlatform platform) {
    this.platform = platform;
    Path data = platform.dataDirectory();
    this.configFile = data.resolve("config.json");
    this.store = new ModStateStore(data.resolve("vanish.json"));
    this.packets = new ModPacketBridge(platform);
    this.visibility = new ModVisibility(platform);
  }

  /** Loads state, installs packet filtering and prepares online players. */
  public void start() throws IOException {
    Files.createDirectories(platform.dataDirectory());
    config = ModConfig.load(configFile);
    state = store.load();
    packets.install(
        () -> view,
        () -> config.flag("client-night-vision"),
        actions,
        platform.logger());
    for (ServerPlayer player : platform.server().getPlayerList().getPlayers()) {
      joinInternal(player);
    }
    publish();
    sync();
    platform
        .logger()
        .info(
            "Vanish enabled for "
                + platform.name()
                + " "
                + platform.server().getServerVersion()
                + " with "
                + state.vanished().size()
                + " hidden player(s).");
  }

  /** Called from the loader's player-join event. */
  public void joined(ServerPlayer player) {
    if (online.containsKey(player.getUUID())) {
      return;
    }
    joinInternal(player);
    publish();
    sync();
    if (isVanished(player.getUUID())) {
      platform.send(
          player, text(config.message("joined-vanished", "{accent}You joined vanished.")));
    }
  }

  private void joinInternal(ServerPlayer player) {
    if (online.containsKey(player.getUUID())) {
      return;
    }
    online.put(player.getUUID(), player);
    packets.attach(player);
    UUID id = player.getUUID();
    boolean hidden =
        platform.isStaff(player)
            && (state.vanished().contains(id) || config.autoVanishStaff());
    if (hidden) {
      VanishBaseline baseline = state.baseline(id);
      if (baseline == null) {
        baseline = capture(player);
      }
      try {
        commit(id, true, baseline);
      } catch (IOException error) {
        platform.logger().severe("Cannot save vanish state: " + error.getMessage());
      }
      apply(player, baseline);
    }
  }

  /** Called from the loader's player-disconnect event. */
  public void left(ServerPlayer player) {
    UUID id = player.getUUID();
    if (!online.containsKey(id)) {
      return;
    }
    restore(player, state.baseline(id));
    packets.detach(player);
    online.remove(id);
    hiddenPairs.removeIf(pair -> pair.viewer.equals(id) || pair.subject.equals(id));
    publish();
  }

  /** Called once per server tick by the loader adapter. */
  public void tick() {
    reconcileOnline();
    ticks++;
    if (ticks % 20 != 0) {
      return;
    }
    for (ServerPlayer player : new ArrayList<ServerPlayer>(online.values())) {
      UUID id = player.getUUID();
      if (packets.user(player) == null) {
        packets.attach(player);
      }
      if (!isVanished(id)) {
        continue;
      }
      if (!platform.isStaff(player)) {
        try {
          setVanished(player, false);
        } catch (IOException error) {
          platform.logger().severe("Cannot clear revoked vanish: " + error.getMessage());
        }
      } else {
        apply(player, state.baseline(id));
      }
    }
    VanishView previous = view;
    publish();
    if (!view.equals(previous)) {
      sync();
    }
  }

  /** Polls in case a loader does not expose a join/quit callback. */
  private void reconcileOnline() {
    Set<UUID> current = new LinkedHashSet<UUID>();
    for (ServerPlayer player : platform.server().getPlayerList().getPlayers()) {
      current.add(player.getUUID());
      if (!online.containsKey(player.getUUID())) {
        joined(player);
      }
    }
    for (UUID id : new ArrayList<UUID>(online.keySet())) {
      if (current.contains(id)) {
        continue;
      }
      ServerPlayer player = online.get(id);
      if (player != null) {
        left(player);
      }
    }
  }

  public boolean isVanished(UUID id) {
    return view.hidden().containsKey(id);
  }

  public boolean canSee(UUID viewer, UUID subject) {
    return !view.hides(viewer, subject);
  }

  public boolean isStaff(ServerPlayer player) {
    return platform.isStaff(player);
  }

  public VanishView view() {
    return view;
  }

  public ModConfig config() {
    return config;
  }

  public ServerPlayer onlinePlayer(String name) {
    for (ServerPlayer player : online.values()) {
      if (player.getGameProfile().name().equalsIgnoreCase(name)) {
        return player;
      }
    }
    return null;
  }

  public List<String> onlineNames() {
    List<String> names = new ArrayList<String>();
    for (ServerPlayer player : online.values()) {
      names.add(player.getGameProfile().name());
    }
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  /** Persists the toggle before changing packets, flight or announcements. */
  public void setVanished(ServerPlayer player, boolean hidden) throws IOException {
    UUID id = player.getUUID();
    if (isVanished(id) == hidden) {
      return;
    }
    if (hidden && !platform.isStaff(player)) {
      throw new IllegalArgumentException("Vanish is available to operators");
    }
    Set<UUID> previouslyVisible = new HashSet<UUID>();
    for (ServerPlayer viewer : online.values()) {
      if (!view.hides(viewer.getUUID(), id)) {
        previouslyVisible.add(viewer.getUUID());
      }
    }

    VanishBaseline baseline = state.baseline(id);
    if (hidden && baseline == null) {
      baseline = capture(player);
    }
    commit(id, hidden, baseline);
    platform
        .logger()
        .info(
            player.getGameProfile().name()
                + (hidden ? " is now vanished." : " is visible again."));
    if (hidden) {
      apply(player, baseline);
    } else {
      restore(player, baseline);
    }
    publish();
    sync();

    platform.send(
        player,
        text(
            config.message(
                hidden ? "enabled" : "disabled",
                hidden ? "{accent}You are now vanished." : "{primary}You are visible again.")));
    notifyStaff(
        player,
        hidden ? "staff-vanished" : "staff-visible",
        hidden ? "{muted}{player} {text}vanished." : "{muted}{player} {text}became visible.");
    fakeJoinQuit(player, hidden, previouslyVisible);
  }

  /**
   * Rebuilds the policy, repairs drifted tab/entity hide state and replays
   * cached scoreboard/map projections for every viewer.
   */
  public String doctor() {
    publish();
    Set<Pair> expected = new HashSet<Pair>();
    int repaired = 0;
    for (ServerPlayer viewer : online.values()) {
      for (ServerPlayer subject : online.values()) {
        if (viewer == subject) {
          continue;
        }
        Pair pair = new Pair(viewer.getUUID(), subject.getUUID());
        if (!canSee(pair.viewer, pair.subject)) {
          expected.add(pair);
          if (hiddenPairs.add(pair)) {
            visibility.apply(viewer, subject, false);
            repaired++;
          }
        }
      }
      packets.refresh(viewer);
    }
    for (Iterator<Pair> iterator = hiddenPairs.iterator(); iterator.hasNext(); ) {
      Pair pair = iterator.next();
      if (expected.contains(pair)) {
        continue;
      }
      ServerPlayer viewer = online.get(pair.viewer);
      ServerPlayer subject = online.get(pair.subject);
      if (viewer != null && subject != null) {
        visibility.apply(viewer, subject, true);
      }
      iterator.remove();
      repaired++;
    }
    return config.format(
        "{muted}Doctor: {accent}"
            + view.hidden().size()
            + " {muted}vanished / {text}"
            + online.size()
            + " {muted}online, {text}"
            + expected.size()
            + (expected.size() == 1
                ? " {muted}hidden pair checked, {text}"
                : " {muted}hidden pairs checked, {text}")
            + repaired
            + " {muted}repaired, {text}"
            + online.size()
            + " {muted}projections replayed.",
        null);
  }

  /** One-line diagnostics for {@code /vanish info}. */
  public String describe() {
    return config.format(
        "{muted}"
            + platform.name()
            + " {primary}"
            + platform.server().getServerVersion()
            + " {muted}| {accent}"
            + view.hidden().size()
            + " {muted}vanished / {text}"
            + online.size()
            + " {muted}online | {text}"
            + platform.dataDirectory(),
        null);
  }

  public String statusText(ServerPlayer player) {
    boolean hidden = isVanished(player.getUUID());
    return config.message(
        hidden ? "status-vanished" : "status-visible",
        hidden
            ? "{muted}{player} {text}is {accent}vanished{text}."
            : "{muted}{player} {text}is {primary}visible{text}.",
        Collections.singletonMap("player", player.getGameProfile().name()));
  }

  public List<String> vanishedNames() {
    List<String> names = new ArrayList<String>();
    for (VanishView.Identity identity : view.hidden().values()) {
      names.add(identity.name());
    }
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    return names;
  }

  public void reload() throws IOException {
    config = ModConfig.load(configFile);
    for (ServerPlayer player : online.values()) {
      if (isVanished(player.getUUID())) {
        apply(player, state.baseline(player.getUUID()));
      }
    }
    publish();
    sync();
  }

  public void stop() {
    for (ServerPlayer player : online.values()) {
      restore(player, state.baseline(player.getUUID()));
    }
    for (Pair pair : hiddenPairs) {
      ServerPlayer viewer = online.get(pair.viewer);
      ServerPlayer subject = online.get(pair.subject);
      if (viewer != null && subject != null) {
        visibility.apply(viewer, subject, true);
      }
    }
    hiddenPairs.clear();
    packets.close();
    online.clear();
    view = VanishView.EMPTY;
  }

  private void commit(UUID id, boolean hidden, VanishBaseline baseline) throws IOException {
    VanishState next = state.update(id, hidden, baseline);
    if (!next.equals(state)) {
      store.save(next);
      state = next;
    }
  }

  private VanishBaseline capture(ServerPlayer player) {
    Abilities abilities = player.getAbilities();
    return new VanishBaseline(
        abilities.mayfly, abilities.flying, true, false, false, true, "SURVIVAL");
  }

  private void apply(ServerPlayer player, VanishBaseline baseline) {
    if (baseline == null || !config.flag("grant-flight")) {
      return;
    }
    Abilities abilities = player.getAbilities();
    abilities.mayfly = true;
    player.onUpdateAbilities();
  }

  private void restore(ServerPlayer player, VanishBaseline baseline) {
    if (baseline == null) {
      return;
    }
    if (!player.isCreative() && !player.isSpectator()) {
      Abilities abilities = player.getAbilities();
      abilities.mayfly = baseline.flight();
      abilities.flying = baseline.flight() && baseline.flying();
      player.onUpdateAbilities();
    }
  }

  private void publish() {
    Map<UUID, VanishView.Identity> identities =
        new LinkedHashMap<UUID, VanishView.Identity>();
    Set<UUID> staff = new LinkedHashSet<UUID>();
    List<VanishView.Identity> players = new ArrayList<VanishView.Identity>();
    for (ServerPlayer player : online.values()) {
      VanishView.Identity identity =
          new VanishView.Identity(
              player.getUUID(), player.getGameProfile().name(), player.getId());
      players.add(identity);
      if (platform.isStaff(player)) {
        staff.add(player.getUUID());
      }
      if (state.vanished().contains(player.getUUID())) {
        identities.put(player.getUUID(), identity);
      }
    }
    view =
        new VanishView(
            identities,
            staff,
            Collections.<UUID>emptySet(),
            players);
  }

  private void sync() {
    for (ServerPlayer viewer : online.values()) {
      for (ServerPlayer subject : online.values()) {
        if (viewer == subject) {
          continue;
        }
        Pair pair = new Pair(viewer.getUUID(), subject.getUUID());
        boolean hide = view.hides(pair.viewer, pair.subject);
        if (hide && hiddenPairs.add(pair)) {
          visibility.apply(viewer, subject, false);
        } else if (!hide && hiddenPairs.remove(pair)) {
          visibility.apply(viewer, subject, true);
        }
      }
      packets.refresh(viewer);
    }
  }

  private void notifyStaff(ServerPlayer subject, String key, String fallback) {
    for (ServerPlayer viewer : online.values()) {
      if (viewer != subject && platform.isStaff(viewer)) {
        platform.send(
            viewer,
            text(
                config.message(
                    key,
                    fallback,
                    Collections.singletonMap(
                        "player", subject.getGameProfile().name()))));
      }
    }
  }

  private void fakeJoinQuit(
      ServerPlayer subject, boolean hidden, Set<UUID> previouslyVisible) {
    if (!config.flag("fake-join-quit")) {
      return;
    }
    String key = hidden ? "fake-quit" : "fake-join";
    String fallback = hidden ? "{muted}{player} left the game" : "{muted}{player} joined the game";
    String notice =
        config.template(
            key,
            fallback,
            Collections.singletonMap("player", subject.getGameProfile().name()));
    for (ServerPlayer viewer : online.values()) {
      if (viewer == subject || platform.isStaff(viewer)) {
        continue;
      }
      if (!hidden || previouslyVisible.contains(viewer.getUUID())) {
        platform.send(viewer, text(notice));
      }
    }
  }

  private static Component text(String value) {
    return ModText.parse(value);
  }
}
