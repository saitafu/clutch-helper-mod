package com.mlgassist.clutch;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClutchHud {
  private static final Identifier HUD_ID = Identifier.of("mlgassist", "status");

  private ClutchHud() {}

  public static void register() {
    HudElementRegistry.attachElementBefore(
        VanillaHudElements.CHAT,
        HUD_ID,
        ClutchHud::render);
  }

  private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null || client.options.hudHidden) {
      return;
    }

    String line = "Clutch: " + ClutchKeybinds.getStatusLabel();

    int color = ClutchKeybinds.isEnabled() ? 0x55FF55 : 0xFF5555;
    context.drawTextWithShadow(client.textRenderer, Text.literal(line), 6, 6, color);
  }
}
