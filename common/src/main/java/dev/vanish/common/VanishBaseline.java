package dev.vanish.common;

/**
 * The player state that vanish mutates and must restore. Persisted with the
 * vanish list so a server crash during observation cannot strand a player with
 * changed flight, collision or visibility flags.
 */
public final class VanishBaseline {
  private final boolean flight;
  private final boolean flying;
  private final boolean collision;
  private final boolean silent;
  private final boolean sleeping;
  private final boolean spawning;
  private final String mode;

  public VanishBaseline(
      boolean flight,
      boolean flying,
      boolean collision,
      boolean silent,
      boolean sleeping,
      boolean spawning,
      String mode) {
    if (mode == null) {
      throw new IllegalArgumentException("Game mode is required");
    }
    this.flight = flight;
    this.flying = flying;
    this.collision = collision;
    this.silent = silent;
    this.sleeping = sleeping;
    this.spawning = spawning;
    this.mode = mode;
  }

  public boolean flight() {
    return flight;
  }

  public boolean flying() {
    return flying;
  }

  public boolean collision() {
    return collision;
  }

  public boolean silent() {
    return silent;
  }

  public boolean sleeping() {
    return sleeping;
  }

  public boolean spawning() {
    return spawning;
  }

  public String mode() {
    return mode;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof VanishBaseline)) {
      return false;
    }
    VanishBaseline baseline = (VanishBaseline) other;
    return flight == baseline.flight
        && flying == baseline.flying
        && collision == baseline.collision
        && silent == baseline.silent
        && sleeping == baseline.sleeping
        && spawning == baseline.spawning
        && mode.equals(baseline.mode);
  }

  @Override
  public int hashCode() {
    int result = flight ? 1 : 0;
    result = 31 * result + (flying ? 1 : 0);
    result = 31 * result + (collision ? 1 : 0);
    result = 31 * result + (silent ? 1 : 0);
    result = 31 * result + (sleeping ? 1 : 0);
    result = 31 * result + (spawning ? 1 : 0);
    result = 31 * result + mode.hashCode();
    return result;
  }
}
