package com.mlgassist;

import com.mlgassist.clutch.ClutchHud;
import com.mlgassist.clutch.ClutchKeybinds;
import net.fabricmc.api.ClientModInitializer;

public class ClientInit implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClutchKeybinds.register();
    ClutchHud.register();
    AutoClutch.register();
  }
}
