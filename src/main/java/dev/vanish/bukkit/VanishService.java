package dev.vanish.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import dev.vanish.common.VanishActions;
import dev.vanish.common.VanishBaseline;
import dev.vanish.common.VanishPackets;
import dev.vanish.common.VanishState;
import dev.vanish.common.VanishView;
import dev.vanish.bukkit.compat.Compat;
import dev.vanish.bukkit.compat.RawMessages;
import dev.vanish.bukkit.event.VanishStateChangedEvent;
import dev.vanish.bukkit.event.VanishToggleEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main-thread vanish lifecycle and the public visibility API used by other
 * plugins. Packet work is delegated to {@link VanishPackets}; nothing here is
 * called from a Netty thread.
 */
public final class VanishService {
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

  private final VanishPlugin plugin;
  private final VanishStore store;
  private final VanishActions actions = new VanishActions();
  private final VanishPackets packets;
  private final VanishIntegrations integrations;
  private final Set<Pair> hiddenPairs = new HashSet<Pair>();
  private final Map<UUID, Boolean> pickup = new HashMap<UUID, Boolean>();
  private final Map<UUID, Boolean> chatPublic = new HashMap<UUID, Boolean>();
  private final Map<UUID, Integer> visionEntities = new HashMap<UUID, Integer>();
  private final Set<UUID> visionDirty = new HashSet<UUID>();

  private volatile VanishState state;
  private volatile VanishView view = VanishView.EMPTY;
  private volatile boolean registered;
  private BukkitTask task;

  VanishService(VanishPlugin plugin) throws IOException {
    this.plugin = plugin;
    store = new VanishStore(plugin.getDataFolder().toPath().resolve("vanish.yml"));
    state = store.load();
    integrations = new VanishIntegrations(plugin);
    packets =
        new VanishPackets(
            new java.util.function.Supplier<VanishView>() {
              @Override
              public VanishView get() {
                return view;
              }
            },
            new java.util.function.BooleanSupplier() {
              @Override
              public boolean getAsBoolean() {
                return plugin.settings().flag("client-night-vision");
              }
            },
            actions,
            plugin.getLogger());
    // Keep chat-public IDs in the view so the packet filter can allow public chat.
    publish();
  }

  /** Registers packet filtering, events, the command and the periodic task. */
  void start() {
    PacketEvents.getAPI().getEventManager().registerListener(packets);
    registered = true;
    VanishListener listener = new VanishListener(plugin, this);
    Bukkit.getPluginManager().registerEvents(listener, plugin);
    listener.registerOptionalEvents();
    plugin.registerVanishCommand(this);
    for (Player player : Bukkit.getOnlinePlayers()) {
      try {
        prepare(player);
      } catch (IOException error) {
        player.kickPlayer(plugin.settings().prefix() + "Vanish state unavailable.");
        plugin.getLogger().severe("Cannot initialize vanish: " + error.getMessage());
      }
    }
    task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
      @Override
      public void run() {
        tick();
      }
    }, 20L, 20L);
    plugin
        .getLogger()
        .info(
            "Vanish ready for "
                + PacketEvents.getAPI().getServerManager().getVersion()
                + " (1.8.8 through 26.x)");
  }

  /** Whether this UUID is currently vanished, safe for async consumers. */
  public boolean isVanished(UUID id) {
    return view.hidden().containsKey(id);
  }

  /** Whether a viewer may see a subject; callers should also honor Player.canSee. */
  public boolean canSee(UUID viewer, UUID subject) {
    return !view.hides(viewer, subject);
  }

  /** Immutable policy snapshot for integrations and placeholders. */
  public VanishView view() {
    return view;
  }

  static boolean permitted(Player player) {
    return player.hasPermission(VanishPlugin.PERM_USE)
        || player.hasPermission(VanishPlugin.PERM_AUTO);
  }

  /** Applies the persisted/auto vanish decision and synchronizes visibility. */
  void prepare(Player player) throws IOException {
    UUID id = player.getUniqueId();
    boolean hidden =
        permitted(player)
            && (state.vanished().contains(id) || player.hasPermission(VanishPlugin.PERM_AUTO));
    if (hidden) {
      VanishBaseline baseline = state.baseline(id);
      if (baseline == null) {
        baseline = capture(player);
      }
      commit(id, true, baseline);
      if (!chatPublic.containsKey(id)) {
        chatPublic.put(id, Boolean.valueOf(plugin.settings().flag("default-chat-public")));
      }
      visionDirty.add(id);
      publish();
      apply(player, baseline);
    } else {
      VanishBaseline baseline = state.baseline(id);
      if (state.vanished().contains(id) || baseline != null) {
        commit(id, false, null);
      }
      restore(player, baseline);
    }
    publish();
    sync();
  }

  /** Called on join after the baseline is applied. */
  void joined(Player player) throws IOException {
    prepare(player);
    if (isVanished(player.getUniqueId())) {
      player.sendMessage(
          plugin.settings().message("joined-vanished", "{accent}You joined vanished."));
      notifyStaff(
          player, "staff-joined", "{muted}{player} {text}joined vanished.");
    }
    scheduleSync();
  }

  /** Persists a toggle before changing packets, flight, metadata or announcements. */
  public void setVanished(Player player, boolean hidden) throws IOException {
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("Vanish changes require the server thread");
    }
    UUID id = player.getUniqueId();
    if (isVanished(id) == hidden) {
      return;
    }
    if (hidden && !permitted(player)) {
      throw new IllegalArgumentException(
          plugin.settings().template(
              "permission",
              "{error}You need {permission}.",
              Collections.singletonMap("permission", VanishPlugin.PERM_USE)));
    }
    if (hidden
        && plugin.settings().flag("prevent-mounting")
        && (player.isInsideVehicle() || player.getVehicle() != null)) {
      throw new IllegalArgumentException(
          plugin.settings().format("{error}Leave your vehicle before vanishing.", null));
    }

    VanishToggleEvent toggle = new VanishToggleEvent(player, hidden);
    Bukkit.getPluginManager().callEvent(toggle);
    if (toggle.isCancelled()) {
      return;
    }

    Set<UUID> previouslyVisible = new HashSet<UUID>();
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.canSee(player)) {
        previouslyVisible.add(viewer.getUniqueId());
      }
    }

    VanishBaseline baseline = state.baseline(id);
    if (hidden && baseline == null) {
      baseline = capture(player);
    }
    commit(id, hidden, baseline);
    plugin
        .getLogger()
        .info(player.getName() + (hidden ? " is now vanished." : " is visible again."));
    if (hidden) {
      chatPublic.put(id, Boolean.valueOf(plugin.settings().flag("default-chat-public")));
      player.closeInventory();
      apply(player, baseline);
      clearMobTargets(player);
    } else {
      chatPublic.remove(id);
      player.closeInventory();
      restore(player, baseline);
      pickup.remove(id);
    }
    publish();
    sync();

    Bukkit.getPluginManager()
        .callEvent(new VanishStateChangedEvent(player, hidden));

    player.sendMessage(
        hidden
            ? plugin.settings().message("enabled", "{accent}You are now vanished.")
            : plugin.settings().message("disabled", "{primary}You are visible again."));
    notifyStaff(
        player,
        hidden ? "staff-vanished" : "staff-visible",
        hidden ? "{muted}{player} {text}vanished." : "{muted}{player} {text}became visible.");
    fakeJoinQuit(player, hidden, previouslyVisible);
  }

  private void commit(UUID id, boolean hidden, VanishBaseline baseline) throws IOException {
    VanishState next = state.update(id, hidden, baseline);
    if (!next.equals(state)) {
      store.save(next);
      state = next;
    }
  }

  private VanishBaseline capture(Player player) {
    return new VanishBaseline(
        player.getAllowFlight(),
        player.isFlying(),
        Compat.isCollidable(player),
        Compat.isSilent(player),
        player.isSleepingIgnored(),
        Compat.getAffectsSpawning(player),
        player.getGameMode().name());
  }

  private void apply(Player player, VanishBaseline baseline) {
    if (baseline == null) {
      return;
    }
    integrations.hidden(player, true);
    player.setMetadata("vanished", new FixedMetadataValue(plugin, Boolean.TRUE));
    if (plugin.settings().flag("prevent-gliding")) {
      Compat.setGliding(player, false);
    }
    Compat.setCollidable(
        player,
        plugin.settings().flag("prevent-collision")
            ? false
            : baseline.collision());
    Compat.setSilent(
        player, plugin.settings().flag("silent-entities") || baseline.silent());
    player.setSleepingIgnored(
        plugin.settings().flag("ignore-sleep") || baseline.sleeping());
    Compat.setAffectsSpawning(
        player,
        plugin.settings().flag("prevent-spawning")
            ? false
            : baseline.spawning());
    if (plugin.settings().flag("grant-flight")) {
      player.setAllowFlight(true);
    } else {
      restoreFlight(player, baseline);
    }
    updateVision(player);
  }

  private void restore(Player player, VanishBaseline baseline) {
    integrations.hidden(player, false);
    player.removeMetadata("vanished", plugin);
    RawMessages.clearActionBar(player);
    UUID id = player.getUniqueId();
    visionDirty.remove(id);
    disableVision(player);
    if (baseline == null) {
      return;
    }
    Compat.setCollidable(player, baseline.collision());
    Compat.setSilent(player, baseline.silent());
    player.setSleepingIgnored(baseline.sleeping());
    Compat.setAffectsSpawning(player, baseline.spawning());
    restoreFlight(player, baseline);
  }

  private static void restoreFlight(Player player, VanishBaseline baseline) {
    if (player.getGameMode() != GameMode.CREATIVE
        && player.getGameMode() != GameMode.SPECTATOR) {
      boolean naturalMode =
          Compat.gameMode(baseline.mode()) != GameMode.CREATIVE
              && Compat.gameMode(baseline.mode()) != GameMode.SPECTATOR;
      boolean allow = naturalMode && baseline.flight();
      player.setFlying(allow && baseline.flying());
      player.setAllowFlight(allow);
      player.setFallDistance(0);
    }
  }

  private void updateVision(Player player) {
    UUID id = player.getUniqueId();
    boolean enabled = plugin.settings().flag("client-night-vision");
    Integer entity = visionEntities.get(id);
    boolean dirty = visionDirty.remove(id);
    User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
    if (enabled && (entity == null || entity.intValue() != player.getEntityId() || dirty)) {
      if (VanishPackets.nightVision(
          user, player.getEntityId(), true, -1, 0, false, false, false)) {
        visionEntities.put(id, Integer.valueOf(player.getEntityId()));
      }
    } else if (!enabled && visionEntities.remove(id) != null) {
      disableVision(player);
    }
  }

  private void disableVision(Player player) {
    if (visionEntities.remove(player.getUniqueId()) == null) {
      return;
    }
    User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
    PotionEffect real = nightVisionEffect(player);
    VanishPackets.nightVision(
        user,
        player.getEntityId(),
        false,
        real == null ? 0 : real.getDuration(),
        real == null ? 0 : real.getAmplifier(),
        real != null && real.isAmbient(),
        real != null && real.hasParticles(),
        false);
  }

  private static PotionEffect nightVisionEffect(Player player) {
    for (PotionEffect effect : player.getActivePotionEffects()) {
      if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
        return effect;
      }
    }
    return null;
  }

  private void clearMobTargets(Player player) {
    if (!plugin.settings().flag("prevent-mob-targeting")) {
      return;
    }
    for (LivingEntity entity : player.getWorld().getLivingEntities()) {
      if (entity instanceof Creature) {
        Creature creature = (Creature) entity;
        if (player.equals(creature.getTarget())) {
          creature.setTarget(null);
        }
      }
    }
  }

  /** Rebuilds the immutable policy snapshot from server-thread state. */
  void publish() {
    Map<UUID, VanishView.Identity> identities =
        new LinkedHashMap<UUID, VanishView.Identity>();
    Set<UUID> staff = new LinkedHashSet<UUID>();
    Set<UUID> publicChat = new LinkedHashSet<UUID>();
    List<VanishView.Identity> online = new ArrayList<VanishView.Identity>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      VanishView.Identity identity =
          new VanishView.Identity(
              player.getUniqueId(), player.getName(), player.getEntityId());
      online.add(identity);
      if (player.hasPermission(VanishPlugin.PERM_SEE)) {
        staff.add(player.getUniqueId());
      }
      if (state.vanished().contains(player.getUniqueId())) {
        identities.put(player.getUniqueId(), identity);
        if (Boolean.TRUE.equals(chatPublic.get(player.getUniqueId()))) {
          publicChat.add(player.getUniqueId());
        }
      }
    }
    view = new VanishView(identities, staff, publicChat, online);
  }

  private void scheduleSync() {
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        sync();
      }
    });
  }

  /** Applies viewer/subject visibility and refreshes cached packet projections. */
  void sync() {
    List<Player> players = new ArrayList<Player>();
    Map<UUID, UUID> worlds = new HashMap<UUID, UUID>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      players.add(player);
      worlds.put(player.getUniqueId(), player.getWorld().getUID());
    }
    actions.worlds(worlds);
    for (Player viewer : players) {
      for (Player subject : players) {
        Pair pair = new Pair(viewer.getUniqueId(), subject.getUniqueId());
        if (!canSee(pair.viewer, pair.subject)) {
          if (viewer.getGameMode() == GameMode.SPECTATOR
              && subject.equals(viewer.getSpectatorTarget())) {
            viewer.setSpectatorTarget(null);
          }
          Compat.hidePlayer(plugin, viewer, subject);
          hiddenPairs.add(pair);
        } else if (hiddenPairs.remove(pair)) {
          Compat.showPlayer(plugin, viewer, subject);
        }
      }
      packets.refresh(
          viewer.getUniqueId(),
          PacketEvents.getAPI().getPlayerManager().getUser(viewer));
    }
  }

  /**
   * Rebuilds the policy, repairs tab/entity hide state that drifted from it and
   * replays cached scoreboard/map projections. Returns one line per finding.
   */
  List<String> doctor() {
    publish();
    Set<Pair> expected = new HashSet<Pair>();
    int repaired = 0;
    int refreshed = 0;
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      for (Player subject : Bukkit.getOnlinePlayers()) {
        if (viewer.equals(subject)) {
          continue;
        }
        Pair pair = new Pair(viewer.getUniqueId(), subject.getUniqueId());
        if (!canSee(pair.viewer, pair.subject)) {
          expected.add(pair);
          if (hiddenPairs.add(pair)) {
            Compat.hidePlayer(plugin, viewer, subject);
            repaired++;
          }
        }
      }
      packets.refresh(
          viewer.getUniqueId(),
          PacketEvents.getAPI().getPlayerManager().getUser(viewer));
      refreshed++;
    }
    for (Iterator<Pair> iterator = hiddenPairs.iterator(); iterator.hasNext(); ) {
      Pair pair = iterator.next();
      if (expected.contains(pair)) {
        continue;
      }
      Player viewer = Bukkit.getPlayer(pair.viewer);
      Player subject = Bukkit.getPlayer(pair.subject);
      if (viewer != null && subject != null) {
        Compat.showPlayer(plugin, viewer, subject);
      }
      iterator.remove();
      repaired++;
    }

    List<String> report = new ArrayList<String>();
    report.add(
        "{muted}Doctor: {accent}"
            + view.hidden().size()
            + " {muted}vanished / {text}"
            + Bukkit.getOnlinePlayers().size()
            + " {muted}online, {text}"
            + expected.size()
            + (expected.size() == 1
                ? " {muted}hidden pair checked."
                : " {muted}hidden pairs checked."));
    report.add(
        repaired == 0
            ? "{success}No tab or entity drift found."
            : "{warning}Repaired " + repaired + " tab/entity visibility pair(s).");
    report.add(
        "{muted}Replayed scoreboard and map projections for {text}"
            + refreshed
            + (refreshed == 1 ? " {muted}viewer." : " {muted}viewers."));
    return report;
  }

  private void tick() {
    boolean changed;
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID id = player.getUniqueId();
      if (isVanished(id)) {
        if (!permitted(player)) {
          try {
            setVanished(player, false);
          } catch (IOException error) {
            // Fail closed: keep the player hidden when the persistent edit cannot commit.
            plugin.getLogger().severe("Cannot clear revoked vanish: " + error.getMessage());
          }
        } else {
          apply(player, state.baseline(id));
          if (plugin.settings().flag("action-bar")) {
            RawMessages.actionBar(
                player,
                LegacyComponentSerializer.legacySection()
                    .deserialize(
                        plugin.settings()
                            .format(
                                "{accent}Vanished {muted}\u2022 {text}/vanish to become visible",
                                null)));
          }
        }
      }
      VanishListener.closeInvalidInspection(this, player);
    }
    VanishView previous = view;
    publish();
    changed = !view.equals(previous);
    if (changed) {
      sync();
    }
  }

  /** Called after the quit message decision, before the connection is gone. */
  void left(Player player) {
    UUID id = player.getUniqueId();
    restore(player, state.baseline(id));
    chatPublic.remove(id);
    pickup.remove(id);
    visionDirty.remove(id);
    packets.forget(id);
    hiddenPairs.removeIf(pair -> pair.viewer.equals(id) || pair.subject.equals(id));
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        publish();
        sync();
      }
    });
  }

  boolean picksUp(Player player) {
    Boolean value = pickup.get(player.getUniqueId());
    return value == null
        ? plugin.settings().flag("default-item-pickup")
        : value.booleanValue();
  }

  void togglePickup(Player player) {
    if (!isVanished(player.getUniqueId())) {
      throw new IllegalArgumentException(
          plugin.settings().format("{error}Enable vanish before toggling item pickup.", null));
    }
    pickup.put(player.getUniqueId(), Boolean.valueOf(!picksUp(player)));
    player.sendMessage(
        plugin.settings().message(
            "pickup",
            "{muted}Item pickup: {state}.",
            Collections.singletonMap(
                "state",
                plugin.settings().format(picksUp(player) ? "{accent}on" : "{muted}off", null))));
  }

  void resetPickupOverrides() {
    pickup.clear();
  }

  boolean publicChat(UUID player) {
    return Boolean.TRUE.equals(chatPublic.get(player));
  }

  void chatMode(Player player, String mode) {
    if (!isVanished(player.getUniqueId())) {
      throw new IllegalArgumentException(
          plugin.settings().format("{error}Enable vanish before selecting its chat channel.", null));
    }
    boolean next;
    if (mode.equals("public")) {
      next = true;
    } else if (mode.equals("private")) {
      next = false;
    } else if (mode.equals("toggle")) {
      next = !publicChat(player.getUniqueId());
    } else if (mode.equals("status")) {
      next = publicChat(player.getUniqueId());
    } else {
      throw new IllegalArgumentException(
          plugin.settings().format(
              "{error}/vanish chat <private|public|toggle|status>", null));
    }
    chatPublic.put(player.getUniqueId(), Boolean.valueOf(next));
    publish();
    sync();
    player.sendMessage(
        plugin.settings().message(
            "chat",
            "{muted}Chat channel: {state}.",
            Collections.singletonMap(
                "state",
                plugin.settings().format(next ? "{accent}public" : "{muted}private", null))));
  }

  void notifyStaff(Player subject, String key, String fallback) {
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (!viewer.equals(subject)
          && viewer.hasPermission(VanishPlugin.PERM_SEE)) {
        viewer.sendMessage(
            plugin.settings().message(
                key, fallback, Collections.singletonMap("player", subject.getName())));
      }
    }
  }

  void silentBlock(Player player, org.bukkit.block.Block block) {
    if (!plugin.settings().flag("silent-block-actions")) {
      return;
    }
    actions.block(
        player.getUniqueId(),
        block.getWorld().getUID(),
        block.getX(),
        block.getY(),
        block.getZ());
  }

  private void fakeJoinQuit(Player player, boolean hidden, Set<UUID> previouslyVisible) {
    if (!plugin.settings().flag("fake-join-quit")) {
      return;
    }
    String key = hidden ? "fake-quit" : "fake-join";
    String fallback = hidden ? "{muted}{player} left the game" : "{muted}{player} joined the game";
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.equals(player)
          || viewer.hasPermission(VanishPlugin.PERM_SEE)) {
        continue;
      }
      if (hidden
          ? previouslyVisible.contains(viewer.getUniqueId())
          : viewer.canSee(player)) {
        RawMessages.send(
            viewer,
            LegacyComponentSerializer.legacySection()
                .deserialize(
                    plugin.settings().template(
                        key, fallback, Collections.singletonMap("player", player.getName()))));
      }
    }
  }

  void reload() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (isVanished(player.getUniqueId())) {
        apply(player, state.baseline(player.getUniqueId()));
        if (!plugin.settings().flag("action-bar")) {
          RawMessages.clearActionBar(player);
        }
      }
    }
    publish();
    sync();
  }

  void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    view = VanishView.EMPTY;
    List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
    for (Player player : players) {
      restore(player, state.baseline(player.getUniqueId()));
    }
    sync();
    if (registered) {
      PacketEvents.getAPI().getEventManager().unregisterListener(packets);
      registered = false;
    }
    hiddenPairs.clear();
    pickup.clear();
    chatPublic.clear();
    visionEntities.clear();
  }
}
