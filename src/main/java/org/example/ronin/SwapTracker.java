package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class SwapTracker {

    private static Item lastMainHandItem;
    private static boolean initialized;

    private SwapTracker() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            initialized = false;
            lastMainHandItem = null;
            return;
        }

        ItemStack stack = player.getMainHandStack();
        Item item = stack.isEmpty() ? null : stack.getItem();

        if (!initialized) {
            initialized = true;
            lastMainHandItem = item;
            return;
        }

        if (!Objects.equals(item, lastMainHandItem)) {
            Item from = lastMainHandItem;
            lastMainHandItem = item;
            SwapHistory.record(from, item);
        }
    }
}
