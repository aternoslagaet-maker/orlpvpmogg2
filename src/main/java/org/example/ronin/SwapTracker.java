package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapTracker {

    public static final Logger LOGGER = LoggerFactory.getLogger("orlpvpmogg");

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
            boolean lungeSwap = isLungeSwap(from, current);
            SwapHistory.record(from, current, fromOnCooldown, lungeSwap);
            LOGGER.info("Свап: {} -> {} (копьё-свап: {}, КД снятого: {})",
                    itemId(from), itemId(current), lungeSwap, fromOnCooldown);
        } else {
            LOGGER.info("Смена предмета: {} -> {} (не записано: пусто или не оружие)",
                    itemId(from), itemId(current));
        }
    }

    public static boolean isLungeSwap(ItemStack from, ItemStack to) {
        if (SwapHistory.isSpear(from) || SwapHistory.isSpear(to)) {
            return true;
        }
        return matchesSlot(SwapConfig.spearSlot, from) || matchesSlot(SwapConfig.spearSlot, to);
    }

    public static boolean matchesSlot(int slot, ItemStack stack) {
        if (slot == SwapConfig.NO_SLOT || slot < 0 || slot > 8 || stack.isEmpty()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }
        ItemStack slotStack = client.player.getInventory().getStack(slot);
        return !slotStack.isEmpty() && slotStack.getItem() == stack.getItem();
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "<пусто>";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "<null>" : id.toString();
    }
}
