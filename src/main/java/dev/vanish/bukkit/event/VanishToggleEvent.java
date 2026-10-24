package dev.vanish.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a player's vanish state changes. Cancellable. */
public final class VanishToggleEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final boolean vanishing;
  private boolean cancelled;

  public VanishToggleEvent(Player player, boolean vanishing) {
    this.player = player;
    this.vanishing = vanishing;
  }

  public Player getPlayer() {
    return player;
  }

  public boolean isVanishing() {
    return vanishing;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
