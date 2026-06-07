package com.mlgassist.clutch;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClutchKeybinds {
  private static final KeyBinding.Category CATEGORY =
      KeyBinding.Category.create(Identifier.of("mlgassist", "main"));

  private static KeyBinding toggleClutchKey;
  private static boolean clutchEnabled;

  private ClutchKeybinds() {}

  public static void register() {
    toggleClutchKey =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.mlgassist.toggle_clutch",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY));

    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          while (toggleClutchKey.wasPressed()) {
            clutchEnabled = !clutchEnabled;
            if (client.player != null) {
              client.player.sendMessage(
                  Text.literal("Clutch: " + (clutchEnabled ? "ON" : "OFF")),
                  true);
            }
          }
        });
  }

  public static boolean isEnabled() {
    return clutchEnabled;
  }

  public static String getStatusLabel() {
    return clutchEnabled ? "ON" : "OFF";
  }
}
