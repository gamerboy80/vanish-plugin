package dev.vanish.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class ModTextTest {
  @Test
  void parsesLegacyColorsWithoutLeakingCodes() {
    Component component = ModText.parse("\u00a78[\u00a7dVanish\u00a78] \u00a7aHello");

    assertEquals("[Vanish] Hello", component.getString());
    assertFalse(component.getString().contains("\u00a7"));
    assertEquals(
        TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY),
        component.getSiblings().get(0).getStyle().getColor());
    assertEquals(
        TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE),
        component.getSiblings().get(1).getStyle().getColor());
  }
}
