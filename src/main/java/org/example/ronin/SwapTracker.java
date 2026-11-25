package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapTracker {

    public static final Logger LOGGER = LoggerFactory.getLogger("orlpvpmogg");

    public static final long LUNGE_ATTACK_WINDOW_MS = 400;
    public static final long REACH_ATTACK_WINDOW_MS = 150;
    public static final long SLOW_TRANSITION_MS = 150;
    public static final long REACH_LATE_SLOT_MS = 60;
    public static final long STALE_ATTACK_MS = 1000;
    public static final long PEARL_CATCH_WINDOW_MS = 500;
    public static final float PEARL_UP_OK_DEG = -55.0f;
    public static final float PEARL_UP_WARN_DEG = -25.0f;
    public static final float AIM_GREEN_MAX_DEG = 5.0f;

    public static String lastIssueText = "";
    public static int lastIssueColor = 0xFFAAAAAA;
    public static String lastHitText = "";
    public static int lastHitColor = 0xFF45FF45;
    public static String lastPearlText = "";
    public static int lastPearlColor = 0xFF45FF45;

    private static ItemStack lastStack = ItemStack.EMPTY;
    private static boolean lastAttackKeyDown = false;
    private static long lastAttackClickMs = -1L;
    private static boolean lastAttackHitEntity = false;
    private static boolean lastUseKeyDown = false;
    private static long lastPearlMs = -1L;

    private static ItemStack pendingFrom = null;
    private static ItemStack pendingTo = null;
    private static boolean pendingOnCooldown = false;
    private static boolean pendingIsLunge = true;
    private static long pendingSwapMs = -1L;

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
            lastAttackHitEntity = client.crosshairTarget != null
                    && client.crosshairTarget.getType() == HitResult.Type.ENTITY;
            completePendingSwap(client, player, now);
        }
        lastAttackKeyDown = attackDown;

        boolean useDown = client.options.useKey.isPressed();
        if (useDown && !lastUseKeyDown) {
            onUsePress(client, player, now);
        }
        lastUseKeyDown = useDown;

        ItemStack current = player.getMainHandStack();
        if (current.getItem() == lastStack.getItem()) {
            return;
        }

        ItemStack from = lastStack;
        lastStack = current.copy();

        boolean fromSpear = SwapHistory.isSpear(from) || matchesSlot(SwapConfig.spearSlot, from);
        boolean toSpear = SwapHistory.isSpear(current) || matchesSlot(SwapConfig.spearSlot, current);
        boolean toIsWeapon = SwapHistory.isWeapon(current);
        boolean fromIsWeapon = SwapHistory.isWeapon(from);
        long attackGapMs = lastAttackClickMs < 0L ? -1L : now - lastAttackClickMs;
        if (attackGapMs > STALE_ATTACK_MS) {
            attackGapMs = -1L;
        }
        boolean lungeSwap = toSpear && !fromSpear && !fromIsWeapon && attackGapMs >= 0
                && attackGapMs <= LUNGE_ATTACK_WINDOW_MS;
        boolean reachSwap = fromSpear && !toSpear && toIsWeapon && attackGapMs >= 0
                && attackGapMs <= REACH_ATTACK_WINDOW_MS;

        ItemCooldownManager cooldowns = player.getItemCooldownManager();
        boolean fromOnCooldown = cooldowns.isCoolingDown(from);
        boolean pendingCase = (toSpear && !fromSpear && !fromIsWeapon && !lungeSwap && attackGapMs < 0)
                || (fromSpear && !toSpear && toIsWeapon && !reachSwap && attackGapMs < 0);
        if (pendingCase) {
            pendingIsLunge = toSpear;
            pendingFrom = from.copy();
            pendingTo = current.copy();
            pendingOnCooldown = fromOnCooldown;
            pendingSwapMs = now;
        } else {
            pendingSwapMs = -1L;
            pendingFrom = null;
            pendingTo = null;
        }
        if (fromSpear && !toSpear && toIsWeapon && attackGapMs >= 0) {
            lastHitText = lastAttackHitEntity ? "Дальность: попал по цели ✓" : "Дальность: не попал по цели";
            lastHitColor = lastAttackHitEntity ? 0xFF45FF45 : 0xFFFFA555;
        } else {
            lastHitText = "";
        }
        diagnose(client, fromSpear, toSpear, lungeSwap, reachSwap, attackGapMs, fromOnCooldown, toIsWeapon, fromIsWeapon);

        boolean meaningful = lungeSwap || reachSwap
                || (SwapHistory.isWeapon(from) && SwapHistory.isWeapon(current) && !toSpear && !fromSpear);

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

    private static void diagnose(MinecraftClient client, boolean fromSpear, boolean toSpear, boolean lungeSwap,
                                 boolean reachSwap, long attackGapMs, boolean fromOnCooldown,
                                 boolean toIsWeapon, boolean fromIsWeapon) {
        if (lungeSwap) {
            if (fromOnCooldown) {
                if (attackGapMs > SLOW_TRANSITION_MS) {
                    lastIssueText = "КД: поздний переход — удар за " + attackGapMs + " мс";
                } else {
                    lastIssueText = "КД: слишком рано — снятый предмет ещё остывает";
                }
                lastIssueColor = 0xFFFF5555;
            } else if (attackGapMs > SLOW_TRANSITION_MS) {
                lastIssueText = "Свап с опозданием — удар за " + attackGapMs + " мс (оптимум ~40)";
                lastIssueColor = 0xFFFFFF55;
            } else {
                lastIssueText = "";
            }
            return;
        }
        if (toSpear && !fromSpear) {
            if (fromIsWeapon) {
                return;
            }
            if (attackGapMs >= 0) {
                lastIssueText = "Поздно свапнул на копьё (удар за " + attackGapMs + " мс)";
                lastIssueColor = 0xFFFFFF55;
                playSwapSound(client, false);
            }
            return;
        }
        if (fromSpear && !toSpear && !reachSwap) {
            if (!toIsWeapon) {
                return;
            }
            if (attackGapMs >= 0) {
                lastIssueText = "Поздно ударил (" + attackGapMs + " мс) — дальность не засчитана";
                lastIssueColor = 0xFFFFFF55;
                playSwapSound(client, false);
            }
            return;
        }
        if (reachSwap) {
            if (attackGapMs > REACH_LATE_SLOT_MS) {
                lastIssueText = "Дальность: слот поздновато (удар за " + attackGapMs + " мс)";
                lastIssueColor = 0xFFFFFF55;
                playSwapSound(client, false);
            } else {
                lastIssueText = "";
                playSwapSound(client, true);
            }
        }
    }

    private static void completePendingSwap(MinecraftClient client, ClientPlayerEntity player, long now) {
        if (pendingSwapMs < 0L || pendingFrom == null || pendingTo == null || player == null) {
            return;
        }
        ItemStack held = player.getMainHandStack();
        boolean stillSpear = SwapHistory.isSpear(held) || matchesSlot(SwapConfig.spearSlot, held);
        boolean stillWeapon = SwapHistory.isWeapon(held);
        long delayMs = now - pendingSwapMs;
        long window = pendingIsLunge ? LUNGE_ATTACK_WINDOW_MS : REACH_ATTACK_WINDOW_MS;
        boolean stillTarget = pendingIsLunge ? stillSpear : stillWeapon;
        if (!stillTarget || delayMs > window) {
            pendingSwapMs = -1L;
            pendingFrom = null;
            pendingTo = null;
            return;
        }
        ItemStack from = pendingFrom;
        ItemStack to = pendingTo;
        boolean fromOnCooldown = pendingOnCooldown;
        boolean isLunge = pendingIsLunge;
        pendingSwapMs = -1L;
        pendingFrom = null;
        pendingTo = null;

        float pitchDeg = player.getPitch(1.0f);
        if (isLunge) {
            SwapHistory.record(from, to, fromOnCooldown, true, false, delayMs, pitchDeg);
            if (fromOnCooldown) {
                if (delayMs > SLOW_TRANSITION_MS) {
                    lastIssueText = "КД: поздний переход — удар за " + delayMs + " мс";
                } else {
                    lastIssueText = "КД: слишком рано — снятый предмет ещё остывает";
                }
                lastIssueColor = 0xFFFF5555;
            } else if (delayMs > SLOW_TRANSITION_MS) {
                lastIssueText = "Свап с опозданием приёма — удар через " + delayMs + " мс после слота";
                lastIssueColor = 0xFFFFFF55;
            } else {
                lastIssueText = "";
            }
            LOGGER.info("Парный свап рывок (слот→ЛКМ): {} -> {} (удар через {} мс, КД снятого: {})",
                    itemId(from), itemId(to), delayMs, fromOnCooldown);
            playLungeSound(client, !fromOnCooldown, pitchDeg);
            updateGoal(client, player, !fromOnCooldown, pitchDeg);
            SwapConfig.save();
        } else {
            SwapHistory.record(from, to, fromOnCooldown, false, true, delayMs, pitchDeg);
            if (delayMs > REACH_LATE_SLOT_MS) {
                lastIssueText = "Свап с опозданием приёма — удар через " + delayMs + " мс после слота";
                lastIssueColor = 0xFFFFFF55;
            } else {
                lastIssueText = "";
            }
            lastHitText = lastAttackHitEntity ? "Дальность: попал по цели ✓" : "Дальность: не попал по цели";
            lastHitColor = lastAttackHitEntity ? 0xFF45FF45 : 0xFFFFA555;
            LOGGER.info("Парный свап дальность (слот→ЛКМ): {} -> {} (удар через {} мс)",
                    itemId(from), itemId(to), delayMs);
            playSwapSound(client, delayMs <= REACH_LATE_SLOT_MS);
            SwapConfig.save();
        }
    }

    private static void onUsePress(MinecraftClient client, ClientPlayerEntity player, long now) {
        String id = itemId(player.getMainHandStack());
        if (id.endsWith("ender_pearl")) {
            lastPearlMs = now;
            float pitch = player.getPitch(1.0f);
            if (pitch > PEARL_UP_WARN_DEG) {
                lastPearlText = "Перлинг: прицел низковат (" + String.format("%.0f°", pitch) + ") — надо вверх";
                lastPearlColor = 0xFFFFA555;
            } else {
                lastPearlText = "Перлинг: жемчуг брошен — сразу же заряд!";
                lastPearlColor = 0xFFAAAAAA;
            }
        } else if (id.endsWith("wind_charge")) {
            if (lastPearlMs < 0L) {
                lastPearlText = "Перлинг: сначала кинь жемчуг";
                lastPearlColor = 0xFFFFFF55;
                return;
            }
            long delayMs = now - lastPearlMs;
            lastPearlMs = -1L;
            if (delayMs > PEARL_CATCH_WINDOW_MS) {
                lastPearlText = "Перлинг: поздно, заряд за " + delayMs + " мс — не догонит";
                lastPearlColor = 0xFFFFFF55;
                return;
            }
            float pitch = player.getPitch(1.0f);
            if (pitch > PEARL_UP_OK_DEG) {
                lastPearlText = "Перлинг: прицел низкий (" + String.format("%.0f°", pitch) + ") — почти вертикально";
                lastPearlColor = 0xFFFFA555;
                return;
            }
            boolean boosted = player.getVelocity().y > 0.1;
            lastPearlText = boosted ? "Перлинг: поймаю ✓ (~30 блоков, усиленный)" : "Перлинг: поймаю ✓ (~20 блоков)";
            lastPearlColor = 0xFF45FF45;
        }
    }

    private static void playSwapSound(MinecraftClient client, boolean ok) {
        if (!SwapConfig.soundEnabled || client == null) {
            return;
        }
        float volume = SwapConfig.soundVolume();
        float pitch = ok ? 1.3f : 0.4f;
        float playVolume = ok ? volume : Math.max(0.02f, volume * 0.25f);
        SoundEvent ev = SwapConfig.resolveSound();
        if (ev == null) {
            ev = SoundEvents.UI_BUTTON_CLICK.value();
        }
        client.getSoundManager().play(PositionedSoundInstance.ui(ev, pitch, playVolume));
    }

    public static void previewSound(MinecraftClient client) {
        if (!SwapConfig.soundEnabled || client == null) {
            return;
        }
        SoundEvent ev = SwapConfig.resolveSound();
        if (ev == null) {
            ev = SoundEvents.UI_BUTTON_CLICK.value();
        }
        client.getSoundManager().play(PositionedSoundInstance.ui(ev, 1.0f, SwapConfig.soundVolume()));
    }

    private static void playLungeSound(MinecraftClient client, boolean effective, float pitchDeg) {
        if (!SwapConfig.soundEnabled) {
            return;
        }
        float volume = SwapConfig.soundVolume();
        boolean greenAim = Math.abs(pitchDeg) <= AIM_GREEN_MAX_DEG;
        float pitch = effective ? (greenAim ? 1.5f : 1.2f) : 0.4f;
        float playVolume = effective ? volume : Math.max(0.02f, volume * 0.25f);
        SoundEvent ev = SwapConfig.resolveSound();
        if (ev == null) {
            ev = SoundEvents.UI_BUTTON_CLICK.value();
        }
        client.getSoundManager().play(PositionedSoundInstance.ui(ev, pitch, playVolume));
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
