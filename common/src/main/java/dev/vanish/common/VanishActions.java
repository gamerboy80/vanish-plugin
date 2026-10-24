package dev.vanish.common;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, world-specific block actions shared safely with the packet
 * thread. Server-thread callers record an action; the packet filter consumes it
 * when the matching sound or world event is about to be sent to another viewer.
 */
public final class VanishActions {
  private static final long LIFETIME_NANOS = 400_000_000L;

  private static final class Block {
    private final UUID world;
    private final int x;
    private final int y;
    private final int z;

    private Block(UUID world, int x, int y, int z) {
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Block)) {
        return false;
      }
      Block block = (Block) other;
      return x == block.x
          && y == block.y
          && z == block.z
          && (world == null ? block.world == null : world.equals(block.world));
    }

    @Override
    public int hashCode() {
      int result = world == null ? 0 : world.hashCode();
      result = 31 * result + x;
      result = 31 * result + y;
      result = 31 * result + z;
      return result;
    }
  }

  private static final class Action {
    private final UUID actor;
    private final long expires;

    private Action(UUID actor, long expires) {
      this.actor = actor;
      this.expires = expires;
    }
  }

  private final Map<Block, Action> blocks = new ConcurrentHashMap<Block, Action>();
  private volatile Map<UUID, UUID> worlds = new LinkedHashMap<UUID, UUID>();

  /** Updates the viewer-to-world map used to interpret sound coordinates. */
  public void worlds(Map<UUID, UUID> next) {
    worlds = new LinkedHashMap<UUID, UUID>(next);
  }

  /** Records a block action by a vanished player. */
  public void block(UUID actor, UUID world, int x, int y, int z) {
    long now = System.nanoTime();
    for (Iterator<Map.Entry<Block, Action>> iterator = blocks.entrySet().iterator();
        iterator.hasNext(); ) {
      if (iterator.next().getValue().expires <= now) {
        iterator.remove();
      }
    }
    blocks.put(new Block(world, x, y, z), new Action(actor, now + LIFETIME_NANOS));
  }

  /**
   * The vanished actor whose recent action matches this position, or
   * {@code null}. Callers decide whether that actor is hidden from the viewer,
   * so staff keep hearing and seeing the actions of vanished players.
   */
  public UUID actorAt(UUID viewer, double x, double y, double z) {
    UUID world = worlds.get(viewer);
    if (world == null) {
      return null;
    }
    Action action =
        blocks.get(
            new Block(
                world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    if (action == null || action.actor.equals(viewer) || action.expires <= System.nanoTime()) {
      return null;
    }
    return action.actor;
  }

  /** Whether an action at this position is hidden from the viewer. */
  public boolean silent(UUID viewer, double x, double y, double z) {
    return actorAt(viewer, x, y, z) != null;
  }
}
