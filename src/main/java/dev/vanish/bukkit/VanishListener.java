package dev.vanish.bukkit;

import dev.vanish.bukkit.compat.Compat;
import dev.vanish.bukkit.compat.RawMessages;
import dev.vanish.common.VanishView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Observer isolation, lifecycle privacy and silent read-only inspection. */
final class VanishListener implements Listener {
  private final VanishPlugin plugin;
  private final VanishService vanish;

  VanishListener(VanishPlugin plugin, VanishService vanish) {
    this.plugin = plugin;
    this.vanish = vanish;
  }

  /** Optional Paper/1.9+ events are registered by {@link OptionalEvents}. */
  void registerOptionalEvents() {
    OptionalEvents.register(plugin, vanish);
  }

  private boolean hidden(Entity entity) {
    return entity instanceof Player && vanish.isVanished(entity.getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void join(PlayerJoinEvent event) {
    try {
      vanish.joined(event.getPlayer());
    } catch (IOException | IllegalArgumentException error) {
      event.setJoinMessage(null);
      event.getPlayer().kickPlayer(
          plugin.settings().prefix() + "Vanish state unavailable. Contact an admin.");
      plugin.getLogger().severe("Vanish join failed: " + error.getMessage());
      return;
    }
    if (hidden(event.getPlayer())) {
      event.setJoinMessage(null);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void quit(PlayerQuitEvent event) {
    if (hidden(event.getPlayer())) {
      event.setQuitMessage(null);
      vanish.notifyStaff(
          event.getPlayer(),
          "staff-left",
          "{muted}{player} {text}left while vanished.");
    }
    vanish.left(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void world(PlayerChangedWorldEvent event) {
    vanish.sync();
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        reapply(event.getPlayer());
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void respawn(PlayerRespawnEvent event) {
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        reapply(event.getPlayer());
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void mode(PlayerGameModeChangeEvent event) {
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        reapply(event.getPlayer());
      }
    });
  }

  private void reapply(Player player) {
    if (!player.isOnline()) {
      return;
    }
    try {
      vanish.prepare(player);
      vanish.sync();
    } catch (IOException | IllegalArgumentException error) {
      plugin.getLogger().severe("Vanish refresh failed: " + error.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void damageEarly(EntityDamageEvent event) {
    isolateDamage(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void damage(EntityDamageEvent event) {
    isolateDamage(event);
  }

  private void isolateDamage(EntityDamageEvent event) {
    if (hidden(event.getEntity()) && plugin.settings().flag("prevent-incoming-damage")) {
      event.setCancelled(true);
    }
    if (event instanceof EntityDamageByEntityEvent) {
      Player source = attacker(((EntityDamageByEntityEvent) event).getDamager());
      if (source != null
          && hidden(source)
          && plugin.settings().flag("prevent-attacking")) {
        event.setCancelled(true);
      }
    }
  }

  private static Player attacker(Entity damager) {
    if (damager instanceof Player) {
      return (Player) damager;
    }
    if (damager instanceof Projectile) {
      Object shooter = ((Projectile) damager).getShooter();
      return shooter instanceof Player ? (Player) shooter : null;
    }
    if (damager instanceof TNTPrimed) {
      Entity source = ((TNTPrimed) damager).getSource();
      return source instanceof Player ? (Player) source : null;
    }
    return null;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void death(PlayerDeathEvent event) {
    if (hidden(event.getEntity())) {
      if (plugin.settings().flag("retain-death-inventory")) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
      }
      event.setDeathMessage(null);
      vanish.notifyStaff(
          event.getEntity(),
          "staff-died",
          "{muted}{player} {text}died while vanished.");
      return;
    }
    Player killer = event.getEntity().getKiller();
    if (killer != null && hidden(killer) && event.getDeathMessage() != null) {
      event.setDeathMessage(event.getEntity().getName() + " was slain.");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void target(EntityTargetEvent event) {
    if (hidden(event.getTarget()) && plugin.settings().flag("prevent-mob-targeting")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void hunger(FoodLevelChangeEvent event) {
    if (hidden(event.getEntity())
        && plugin.settings().flag("prevent-hunger")
        && event.getFoodLevel() < ((Player) event.getEntity()).getFoodLevel()) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void pickup(PlayerPickupItemEvent event) {
    if (hidden(event.getPlayer()) && !vanish.picksUp(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drop(PlayerDropItemEvent event) {
    if (hidden(event.getPlayer()) && plugin.settings().flag("prevent-item-drop")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void empty(PlayerBucketEmptyEvent event) {
    if (hidden(event.getPlayer()) && plugin.settings().flag("prevent-buckets")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void fill(PlayerBucketFillEvent event) {
    if (hidden(event.getPlayer()) && plugin.settings().flag("prevent-buckets")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void consume(PlayerItemConsumeEvent event) {
    if (hidden(event.getPlayer())
        && plugin.settings().flag("prevent-item-consumption")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void mount(VehicleEnterEvent event) {
    if (hidden(event.getEntered()) && plugin.settings().flag("prevent-mounting")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void launch(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof Player) {
      Player player = (Player) event.getEntity().getShooter();
      if (hidden(player) && plugin.settings().flag("prevent-projectiles")) {
        event.setCancelled(true);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void shoot(EntityShootBowEvent event) {
    if (hidden(event.getEntity()) && plugin.settings().flag("prevent-projectiles")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void bed(PlayerBedEnterEvent event) {
    if (hidden(event.getPlayer()) && plugin.settings().flag("prevent-bed-use")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void armorStand(PlayerArmorStandManipulateEvent event) {
    if (hidden(event.getPlayer())
        && plugin.settings().flag("prevent-entity-interactions")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent event) {
    if (!hidden(event.getPlayer())) {
      return;
    }
    Block clicked = event.getClickedBlock();
    if (clicked != null
        && (event.getAction() == Action.LEFT_CLICK_BLOCK
            || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
      vanish.silentBlock(event.getPlayer(), clicked);
    }
    if (plugin.settings().flag("prevent-world-interactions")
        || (event.getAction() == Action.PHYSICAL
            && plugin.settings().flag("prevent-physical-triggers"))) {
      event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
      event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
      return;
    }
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK
        && clicked != null
        && clicked.getState() instanceof InventoryHolder
        && plugin.settings().flag("silent-container-inspection")) {
      event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
      event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
      if (event.getPlayer().hasPermission(VanishPlugin.PERM_INSPECT)) {
        inspectBlock(plugin, event.getPlayer(), clicked);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void entityInteract(PlayerInteractEntityEvent event) {
    if ((hidden(event.getRightClicked()) || hidden(event.getPlayer()))
        && plugin.settings().flag("prevent-entity-interactions")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void entityInteractAt(PlayerInteractAtEntityEvent event) {
    entityInteract(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void blockBreak(BlockBreakEvent event) {
    if (!hidden(event.getPlayer())) {
      return;
    }
    if (plugin.settings().flag("prevent-block-breaking")) {
      event.setCancelled(true);
    }
    vanish.silentBlock(event.getPlayer(), event.getBlock());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void blockPlace(BlockPlaceEvent event) {
    if (!hidden(event.getPlayer())) {
      return;
    }
    if (plugin.settings().flag("prevent-block-placing")) {
      event.setCancelled(true);
    }
    vanish.silentBlock(event.getPlayer(), event.getBlock());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void chat(AsyncPlayerChatEvent event) {
    if (event.isCancelled() || !hidden(event.getPlayer())) {
      return;
    }
    if (vanish.publicChat(event.getPlayer().getUniqueId())) {
      return;
    }
    event.setCancelled(true);
    String plain = event.getMessage();
    Component rendered =
        Component.text("[Vanish] ", NamedTextColor.DARK_AQUA)
            .append(Component.text(event.getPlayer().getName() + ": ", NamedTextColor.AQUA))
            .append(Component.text(plain, NamedTextColor.WHITE));
    Set<Player> recipients = event.getRecipients();
    for (Player viewer : recipients) {
      if (viewer.equals(event.getPlayer())
          || vanish.isVanished(viewer.getUniqueId())
          || viewer.hasPermission(VanishPlugin.PERM_SEE)) {
        RawMessages.send(viewer, rendered);
      }
    }
    if (!recipients.contains(event.getPlayer())) {
      RawMessages.send(event.getPlayer(), rendered);
    }
    Bukkit.getConsoleSender().sendMessage(
        plugin.settings().color("[Vanish] " + event.getPlayer().getName() + ": " + plain));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void ping(ServerListPingEvent event) {
    if (!plugin.settings().flag("hide-from-server-list")) {
      return;
    }
    VanishView view = vanish.view();
    int hidden = 0;
    for (VanishView.Identity identity : view.hidden().values()) {
      if (view.hides(null, identity.id())) {
        hidden++;
      }
    }
    if (hidden == 0) {
      return;
    }
    OptionalEvents.setPingPlayers(event, Math.max(0, event.getNumPlayers() - hidden));
    try {
      Iterator<Player> iterator = event.iterator();
      while (iterator.hasNext()) {
        Player player = iterator.next();
        if (view.hides(null, player.getUniqueId())) {
          iterator.remove();
        }
      }
    } catch (UnsupportedOperationException ignored) {
      // 1.8 implementations may not allow removing listed players; the count
      // is still adjusted on forks that expose setNumPlayers.
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void open(InventoryOpenEvent event) {
    if (event.getInventory().getHolder() instanceof Inspection) {
      Inspection holder = (Inspection) event.getInventory().getHolder();
      if (!holder.owner.equals(event.getPlayer().getUniqueId())
          || !hidden(event.getPlayer())
          || !event.getPlayer().hasPermission(VanishPlugin.PERM_INSPECT)) {
        event.setCancelled(true);
      }
      return;
    }
    if (hidden(event.getPlayer())
        && plugin.settings().flag("prevent-inventory-access")) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof Inspection) {
      event.setCancelled(true);
      validateInspection(event.getWhoClicked());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof Inspection) {
      event.setCancelled(true);
      validateInspection(event.getWhoClicked());
    }
  }

  private void validateInspection(org.bukkit.entity.HumanEntity entity) {
    if (entity instanceof Player) {
      Player player = (Player) entity;
      Inventory top = player.getOpenInventory().getTopInventory();
      if (!hidden(player)
          || !player.hasPermission(VanishPlugin.PERM_INSPECT)
          || !(top.getHolder() instanceof Inspection)
          || !((Inspection) top.getHolder()).owner.equals(player.getUniqueId())) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
          @Override
          public void run() {
            player.closeInventory();
          }
        });
      }
    }
  }

  static void closeInvalidInspection(VanishService vanish, Player player) {
    Inventory top = player.getOpenInventory().getTopInventory();
    if (top != null && top.getHolder() instanceof Inspection) {
      Inspection holder = (Inspection) top.getHolder();
      if (!holder.owner.equals(player.getUniqueId())
          || !vanish.isVanished(player.getUniqueId())
          || !player.hasPermission(VanishPlugin.PERM_INSPECT)) {
        player.closeInventory();
      }
    }
  }

  static void inspect(VanishPlugin plugin, VanishService vanish, Player player) {
    if (!vanish.isVanished(player.getUniqueId())
        || !player.hasPermission(VanishPlugin.PERM_INSPECT)) {
      throw new IllegalArgumentException("Requires vanish and " + VanishPlugin.PERM_INSPECT);
    }
    Block block = Compat.targetBlock(player, 6);
    if (block == null || !(block.getState() instanceof InventoryHolder)) {
      throw new IllegalArgumentException("Look at a container within six blocks");
    }
    inspectBlock(plugin, player, block);
  }

  private static void inspectBlock(VanishPlugin plugin, Player player, Block block) {
    if (!(block.getState() instanceof InventoryHolder)) {
      return;
    }
    Inventory source;
    if (block.getState() instanceof Chest) {
      source = ((Chest) block.getState()).getInventory();
    } else {
      source = ((InventoryHolder) block.getState()).getInventory();
    }
    int size = Math.min(54, ((source.getSize() + 8) / 9) * 9);
    Inspection holder = new Inspection(player.getUniqueId(), size);
    ItemStack[] contents = source.getContents();
    for (int i = 0; i < Math.min(contents.length, size); i++) {
      ItemStack item = contents[i];
      holder.inventory.setItem(i, item == null ? null : item.clone());
    }
    Bukkit.getScheduler().runTask(plugin, new Runnable() {
      @Override
      public void run() {
        if (player.isOnline()
            && plugin.vanish().isVanished(player.getUniqueId())
            && player.hasPermission(VanishPlugin.PERM_INSPECT)) {
          player.openInventory(holder.inventory);
        }
      }
    });
  }

  private static final class Inspection implements InventoryHolder {
    private final java.util.UUID owner;
    private final Inventory inventory;

    private Inspection(java.util.UUID owner, int size) {
      this.owner = owner;
      inventory =
          Bukkit.createInventory(
              this, size, "\u00a78Silent inspection \u00a77\u2022 snapshot");
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }
}
