package org.example.ronin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class OrlPvpClient implements ClientModInitializer {

    public static final String MOD_ID = "orlpvpmogg";

    public static KeyBinding openHistoryKey;
    public static KeyBinding toggleProfileKey;

    @Override
    public void onInitializeClient() {
        SwapConfig.load();
        SwapHistory.loadPairBest();

        openHistoryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".open_history",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyBinding.Category.MISC
        ));

        toggleProfileKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".toggle_hud_profile",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SwapTracker.tick(client);
            while (toggleProfileKey.wasPressed()) {
                SwapConfig.nextHudProfile();
                SwapConfig.save();
            }
            while (openHistoryKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SwapHistoryScreen());
                }
            }
        });

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "swap_hud"),
                (context, tickCounter) -> HudOverlay.render(context));
    }
}
