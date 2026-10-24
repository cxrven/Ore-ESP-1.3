package de.example.oreesp;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class OreEspSettingsScreen extends Screen {
    public OreEspSettingsScreen() { super(Component.literal("Ore ESP Einstellungen")); }

    @Override
    protected void init() {
        int left = this.width / 2 - 168;
        int top = Math.max(28, this.height / 2 - 130);
        int[] ranges = {16, 24, 32, 48, 64};
        for (int i = 0; i < ranges.length; i++) {
            final int range = ranges[i];
            addRenderableWidget(Button.builder(Component.literal((OreOverlay.radius() == range ? "[" : "") + range + " Bl." + (OreOverlay.radius() == range ? "]" : "")),
                btn -> { OreOverlay.setRadius(range); this.minecraft.gui.setScreen(new OreEspSettingsScreen()); })
                .bounds(left + i * 68, top, 65, 20).build());
        }
        for (int i = 0; i < OreOverlay.optionsCount(); i++) {
            final int index = i;
            int x = left + (i % 2) * 172;
            int y = top + 29 + (i / 2) * 25;
            addRenderableWidget(Button.builder(label(index), btn -> {
                OreOverlay.toggleType(index);
                btn.setMessage(label(index));
            }).bounds(x, y, 168, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Diagnose im Chat anzeigen"), btn -> {
            if (this.minecraft.player != null)
                this.minecraft.player.sendSystemMessage(Component.literal("Ore ESP Diagnose: " + OreOverlay.diagnosis()));
        }).bounds(this.width / 2 - 115, top + 29 + 6 * 25 + 2, 230, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), btn -> this.onClose())
            .bounds(this.width / 2 - 75, top + 29 + 6 * 25 + 27, 150, 20).build());
    }

    private static Component label(int index) {
        return Component.literal((OreOverlay.isSelected(index) ? "[AN] " : "[AUS] ") + OreOverlay.optionName(index));
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(null); }
}
