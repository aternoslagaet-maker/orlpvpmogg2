package org.example.ronin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class OrlPvpClient implements ClientModInitializer {

    public static final String MOD_ID = "orlpvpmogg";

    public static KeyBinding openHistoryKey;

    @Override
    public void onInitializeClient() {
        openHistoryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".open_history",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SwapTracker.tick(client);
            while (openHistoryKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SwapHistoryScreen());
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> HudOverlay.render(context));
    }
}
