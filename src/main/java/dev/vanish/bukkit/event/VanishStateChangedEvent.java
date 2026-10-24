package dev.vanish.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a player's vanish state has fully changed. Not cancellable. */
public final class VanishStateChangedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final boolean vanished;

  public VanishStateChangedEvent(Player player, boolean vanished) {
    this.player = player;
    this.vanished = vanished;
  }

  public Player getPlayer() {
    return player;
  }

  public boolean isVanished() {
    return vanished;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
