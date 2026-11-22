package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapTracker {

    public static final Logger LOGGER = LoggerFactory.getLogger("orlpvpmogg");

    public static final long LUNGE_ATTACK_WINDOW_MS = 400;
    public static final long REACH_ATTACK_WINDOW_MS = 150;
    public static final long SLOW_TRANSITION_MS = 150;
    public static final long REACH_LATE_SLOT_MS = 60;
    public static final float AIM_GREEN_MAX_DEG = 5.0f;

    public static String lastIssueText = "";
    public static int lastIssueColor = 0xFFAAAAAA;

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

        boolean fromSpear = SwapHistory.isSpear(from) || matchesSlot(SwapConfig.spearSlot, from);
        boolean toSpear = SwapHistory.isSpear(current) || matchesSlot(SwapConfig.spearSlot, current);
        long attackGapMs = lastAttackClickMs < 0L ? -1L : now - lastAttackClickMs;
        boolean lungeSwap = toSpear && !fromSpear && attackGapMs >= 0
                && attackGapMs <= LUNGE_ATTACK_WINDOW_MS;
        boolean reachSwap = fromSpear && !toSpear && attackGapMs >= 0
                && attackGapMs <= REACH_ATTACK_WINDOW_MS;

        ItemCooldownManager cooldowns = player.getItemCooldownManager();
        boolean fromOnCooldown = cooldowns.isCoolingDown(from);
        diagnose(fromSpear, toSpear, lungeSwap, reachSwap, attackGapMs, fromOnCooldown);

        boolean meaningful = (SwapHistory.isWeapon(from) && SwapHistory.isWeapon(current))
                || lungeSwap || reachSwap;

        if (!meaningful) {
            LOGGER.info("Смена предмета: {} -> {} (не записано: обычный свап без атаки)",
                    itemId(from), itemId(current));
            return;
        }

        float pitchDeg = player.getPitch(1.0f);
        SwapHistory.record(from, current, fromOnCooldown, lungeSwap, reachSwap, attackGapMs, pitchDeg);

        LOGGER.info("Свап: {} -> {} (рывок: {}, дальность: {}, КД снятого: {}, удар {} мс назад, аим {}°)",
                itemId(from), itemId(current), lungeSwap, reachSwap, fromOnCooldown,
                attackGapMs < 0 ? "нет" : String.valueOf(attackGapMs));

        if (lungeSwap) {
            playLungeSound(client, !fromOnCooldown, pitchDeg);
            updateGoal(client, player, !fromOnCooldown, pitchDeg);
        }
    }

    private static void diagnose(boolean fromSpear, boolean toSpear, boolean lungeSwap, boolean reachSwap,
                                 long attackGapMs, boolean fromOnCooldown) {
        lastIssueText = "";
        if (lungeSwap) {
            if (fromOnCooldown) {
                if (attackGapMs > SLOW_TRANSITION_MS) {
                    lastIssueText = "КД: поздний переход — удар за " + attackGapMs + " мс";
                } else {
                    lastIssueText = "КД: слишком рано — снятый предмет ещё остывает";
                }
                lastIssueColor = 0xFFFF5555;
            }
            return;
        }
        if (toSpear && !fromSpear) {
            if (attackGapMs < 0) {
                lastIssueText = "Нет удара ЛКМ перед свапом — рывок не засчитан";
                lastIssueColor = 0xFFFF5555;
            } else if (attackGapMs > LUNGE_ATTACK_WINDOW_MS) {
                lastIssueText = "Поздно свапнул на копьё (удар за " + attackGapMs + " мс)";
                lastIssueColor = 0xFFFFFF55;
            }
            return;
        }
        if (fromSpear && !toSpear && !reachSwap) {
            if (attackGapMs < 0) {
                lastIssueText = "Нет удара при свапе с копья — дальность не засчитана";
                lastIssueColor = 0xFFFF5555;
            } else if (attackGapMs > REACH_ATTACK_WINDOW_MS) {
                lastIssueText = "Поздно ударил (" + attackGapMs + " мс) — дальность не засчитана";
                lastIssueColor = 0xFFFFFF55;
            }
            return;
        }
        if (reachSwap && attackGapMs > REACH_LATE_SLOT_MS) {
            lastIssueText = "Дальность: слот поздновато (удар за " + attackGapMs + " мс)";
            lastIssueColor = 0xFFFFFF55;
        }
    }

    private static void playLungeSound(MinecraftClient client, boolean effective, float pitchDeg) {
        if (!SwapConfig.soundEnabled) {
            return;
        }
        float volume = SwapConfig.soundVolume();
        boolean greenAim = Math.abs(pitchDeg) <= AIM_GREEN_MAX_DEG;
        float pitch = effective ? (greenAim ? 1.5f : 1.2f) : 0.4f;
        float playVolume = effective ? volume : Math.max(0.02f, volume * 0.25f);
        client.getSoundManager().play(PositionedSoundInstance.ui(
                SoundEvents.UI_BUTTON_CLICK.value(), pitch, playVolume));
    }

    private static void updateGoal(MinecraftClient client, ClientPlayerEntity player,
                                   boolean effective, float pitchDeg) {
        SwapConfig.Goal goal = SwapConfig.currentGoal();
        if (SwapConfig.goalCompleted || goal == null) {
            return;
        }
        boolean greenAim = Math.abs(pitchDeg) <= AIM_GREEN_MAX_DEG;
        SwapHistory.SwapRecord lunge = SwapHistory.lastLunge().orElse(null);
        if (lunge == null || lunge.intervalMs() < 0 || lunge.intervalMs() > goal.maxDeltaMs()
                || !effective || (goal.needGreenAim() && !greenAim)) {
            SwapConfig.goalProgress = 0;
            SwapConfig.save();
            return;
        }
        SwapConfig.goalProgress++;
        if (SwapConfig.goalProgress >= goal.count()) {
            SwapConfig.goalCompleted = true;
            player.sendMessage(Text.literal("✅ Цель выполнена: " + goal.name() + "!").formatted(Formatting.GOLD), true);
            client.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, SwapConfig.soundVolume()));
        }
        SwapConfig.save();
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
