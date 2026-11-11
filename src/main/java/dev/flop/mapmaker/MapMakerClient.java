package dev.flop.mapmaker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MapMakerClient implements ClientModInitializer {

    public static final String MOD_ID = "mapmaker";
    public static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        ImageSources.enableAwtClipboard();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mapmaker.open", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (!(client.screen instanceof MapMakerScreen)) client.setScreen(new MapMakerScreen(client.screen));
            }
        });

        // Small "MapMaker" button in the top-left of the pause menu and title screen too
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("MapMaker"),
                        b -> Minecraft.getInstance().setScreen(new MapMakerScreen(screen))).bounds(4, 4, 64, 20).build());
            }
        });
    }
}
