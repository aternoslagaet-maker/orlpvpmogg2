package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapTracker {

    public static final Logger LOGGER = LoggerFactory.getLogger("orlpvpmogg");

    public static final long LUNGE_ATTACK_WINDOW_MS = 400;
    public static final long REACH_ATTACK_WINDOW_MS = 150;

    private static ItemStack lastStack = ItemStack.EMPTY;
    private static boolean lastAttackKeyDown = false;
    private static long lastAttackClickMs = -1L;

    private SwapTracker() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            lastStack = ItemStack.EMPTY;
            lastAttackKeyDown = false;
            return;
        }

        long now = System.currentTimeMillis();
        boolean attackDown = client.options.attackKey.isPressed();
        if (attackDown && !lastAttackKeyDown) {
            lastAttackClickMs = now;
        }
        lastAttackKeyDown = attackDown;

        ItemStack current = player.getMainHandStack();
        if (current.getItem() == lastStack.getItem()) {
            return;
        }

        ItemStack from = lastStack;
        lastStack = current.copy();

        if (SwapHistory.isMeaningfulSwap(from, current)) {
            ItemCooldownManager cooldowns = player.getItemCooldownManager();
            boolean fromOnCooldown = cooldowns.isCoolingDown(from);
            boolean fromSpear = SwapHistory.isSpear(from) || matchesSlot(SwapConfig.spearSlot, from);
            boolean toSpear = SwapHistory.isSpear(current) || matchesSlot(SwapConfig.spearSlot, current);
            long attackGapMs = lastAttackClickMs < 0L ? -1L : now - lastAttackClickMs;
            boolean lungeSwap = toSpear && !fromSpear && attackGapMs >= 0
                    && attackGapMs <= LUNGE_ATTACK_WINDOW_MS;
            boolean reachSwap = fromSpear && !toSpear && attackGapMs >= 0
                    && attackGapMs <= REACH_ATTACK_WINDOW_MS;
            SwapHistory.record(from, current, fromOnCooldown, lungeSwap, reachSwap, attackGapMs);
            LOGGER.info("Свап: {} -> {} (рывок: {}, дальность: {}, КД снятого: {}, удар {} мс назад)",
                    itemId(from), itemId(current), lungeSwap, reachSwap, fromOnCooldown,
                    attackGapMs < 0 ? "нет" : String.valueOf(attackGapMs));
        } else {
            LOGGER.info("Смена предмета: {} -> {} (не записано: пусто или не оружие)",
                    itemId(from), itemId(current));
        }
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
