package dev.vanish.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Turns {@code §}-style message text into styled Minecraft components. */
final class ModText {
  private ModText() {}

  static Component parse(String value) {
    MutableComponent result = Component.empty();
    Style style = Style.EMPTY;
    StringBuilder part = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current != '\u00a7' || i + 1 >= value.length()) {
        part.append(current);
        continue;
      }
      if (part.length() > 0) {
        result.append(Component.literal(part.toString()).setStyle(style));
        part.setLength(0);
      }
      ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(value.charAt(++i)));
      if (format == ChatFormatting.RESET) {
        style = Style.EMPTY;
      } else if (format != null) {
        style = style.applyFormat(format);
      }
    }
    if (part.length() > 0) {
      result.append(Component.literal(part.toString()).setStyle(style));
    }
    return result;
  }
}
