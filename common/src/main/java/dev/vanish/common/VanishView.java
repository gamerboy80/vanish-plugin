package dev.vanish.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Immutable packet-thread policy. This object never reads Bukkit players,
 * permissions or config, so it is safe to consult from a Netty thread.
 */
public final class VanishView {
  private static final Pattern COLORS = Pattern.compile("(?i)\u00a7[0-9a-fk-orx]");

  public static final VanishView EMPTY =
      new VanishView(
          Collections.<UUID, Identity>emptyMap(),
          Collections.<UUID>emptySet(),
          Collections.<UUID>emptySet(),
          Collections.<Identity>emptyList());

  /** Copy of one online player's identity, captured on the server thread. */
  public static final class Identity {
    private final UUID id;
    private final String name;
    private final int entityId;

    public Identity(UUID id, String name, int entityId) {
      this.id = id;
      this.name = name;
      this.entityId = entityId;
    }

    public UUID id() {
      return id;
    }

    public String name() {
      return name;
    }

    public int entityId() {
      return entityId;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Identity)) {
        return false;
      }
      Identity identity = (Identity) other;
      return entityId == identity.entityId && id.equals(identity.id) && name.equals(identity.name);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * id.hashCode() + name.hashCode()) + entityId;
    }
  }

  private final Map<UUID, Identity> hidden;
  private final Set<UUID> staff;
  private final Set<UUID> chatPublic;
  private final List<Identity> online;

  public VanishView(
      Map<UUID, Identity> hidden,
      Set<UUID> staff,
      Set<UUID> chatPublic,
      List<Identity> online) {
    this.hidden = Collections.unmodifiableMap(new LinkedHashMap<UUID, Identity>(hidden));
    this.staff = Collections.unmodifiableSet(new LinkedHashSet<UUID>(staff));
    this.chatPublic = Collections.unmodifiableSet(new LinkedHashSet<UUID>(chatPublic));
    this.online = Collections.unmodifiableList(new ArrayList<Identity>(online));
  }

  public Map<UUID, Identity> hidden() {
    return hidden;
  }

  public Set<UUID> staff() {
    return staff;
  }

  public Set<UUID> chatPublic() {
    return chatPublic;
  }

  public List<Identity> online() {
    return online;
  }

  /** Whether a subject must not be visible to a viewer. */
  public boolean hides(UUID viewer, UUID subject) {
    return hidden.containsKey(subject)
        && !subject.equals(viewer)
        && (viewer == null || !staff.contains(viewer));
  }

  /** Whether an entity id belongs to a player hidden from this viewer. */
  public boolean hidesEntity(UUID viewer, int entityId) {
    for (Identity subject : hidden.values()) {
      if (subject.entityId() == entityId && hides(viewer, subject.id())) {
        return true;
      }
    }
    return false;
  }

  /** Strict name/UUID mention check used for scoreboards, tab completion and maps. */
  public boolean mentions(UUID viewer, Component component) {
    return mentions(viewer, component, false);
  }

  /** Strict name/UUID mention check used for scoreboards, tab completion and maps. */
  public boolean mentions(UUID viewer, String text) {
    return mentions(viewer, text, false);
  }

  /**
   * Chat-specific mention check. A vanished player who opted into public chat
   * may be named in chat packets, while every other packet remains filtered.
   */
  public boolean mentionsChat(UUID viewer, Component component) {
    return mentions(viewer, component, true);
  }

  /** Chat-specific mention check. */
  public boolean mentionsChat(UUID viewer, String text) {
    return mentions(viewer, text, true);
  }

  private boolean mentions(UUID viewer, Component component, boolean allowPublicChat) {
    if (component == null) {
      return false;
    }
    return mentions(
            viewer, PlainTextComponentSerializer.plainText().serialize(component), allowPublicChat)
        || mentions(viewer, GsonComponentSerializer.gson().serialize(component), allowPublicChat);
  }

  private boolean mentions(UUID viewer, String text, boolean allowPublicChat) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    String plain = COLORS.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    for (Identity identity : hidden.values()) {
      if (!hides(viewer, identity.id())) {
        continue;
      }
      if (plain.contains(identity.id().toString())) {
        return true;
      }
      if (allowPublicChat && chatPublic.contains(identity.id())) {
        continue;
      }
      String name = identity.name().toLowerCase(Locale.ROOT);
      int offset = plain.indexOf(name);
      while (offset >= 0) {
        int end = offset + name.length();
        if ((offset == 0 || !nameChar(plain.charAt(offset - 1)))
            && (end == plain.length() || !nameChar(plain.charAt(end)))) {
          return true;
        }
        offset = plain.indexOf(name, end);
      }
    }
    return false;
  }

  private static boolean nameChar(char value) {
    return value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '_';
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof VanishView)) {
      return false;
    }
    VanishView policy = (VanishView) other;
    return hidden.equals(policy.hidden)
        && staff.equals(policy.staff)
        && chatPublic.equals(policy.chatPublic)
        && online.equals(policy.online);
  }

  @Override
  public int hashCode() {
    int result = hidden.hashCode();
    result = 31 * result + staff.hashCode();
    result = 31 * result + chatPublic.hashCode();
    result = 31 * result + online.hashCode();
    return result;
  }
}
