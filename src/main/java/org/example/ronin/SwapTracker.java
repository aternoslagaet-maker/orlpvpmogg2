package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;

public final class SwapTracker {

    private static ItemStack lastStack = ItemStack.EMPTY;

    private SwapTracker() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            lastStack = ItemStack.EMPTY;
            return;
        }

        ItemStack current = player.getMainHandStack();
        if (current.getItem() == lastStack.getItem()) {
            return;
        }

        ItemStack from = lastStack;
        lastStack = current.copy();

        if (SwapHistory.isMeaningfulSwap(from, current)) {
            ItemCooldownManager cooldowns = player.getItemCooldownManager();
            boolean fromOnCooldown = cooldowns.isCoolingDown(from);
            SwapHistory.record(from, current, fromOnCooldown);
        }
    }
}
