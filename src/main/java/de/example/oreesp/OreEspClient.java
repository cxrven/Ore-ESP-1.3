package de.example.oreesp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class OreEspClient implements ClientModInitializer {
    public static final String MOD_ID = "oreesp";
    static boolean enabled = false;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));

    @Override public void onInitializeClient() {
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oreesp.toggle", InputConstants.KEY_X, CATEGORY));
        KeyMapping menu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oreesp.menu", InputConstants.KEY_G, CATEGORY));
        OreOverlay.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) {
                enabled = !enabled;
                if (client.player != null) client.player.sendSystemMessage(Component.literal("Ore ESP: " + (enabled ? "AN" : "AUS")));
            }
            while (menu.consumeClick()) {
                if (client.level != null) client.gui.setScreen(new OreEspSettingsScreen());
            }
            OreOverlay.tick(client);
        });
    }
}
