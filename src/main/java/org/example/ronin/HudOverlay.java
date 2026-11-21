package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.Optional;

public final class HudOverlay {

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SPEAR = 0xFF45FF45;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_BACKGROUND = 0x64000000;

    public static final int[] LAST_RECT = new int[4];
    public static boolean LAST_RECT_VALID = false;

    private static final float PITCH_MAX_GOOD_DEG = 5.0f;
    private static final float PITCH_WARN_DEG = 15.0f;
    private static final float PITCH_BAD_DEG = 30.0f;
    private static final float PITCH_UP_WARN_DEG = -10.0f;

    private HudOverlay() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        Optional<SwapHistory.SwapRecord> last = SwapHistory.last();
        if (last.isEmpty()) {
            return;
        }

        SwapHistory.SwapRecord r = last.get();
        boolean fullProfile = SwapConfig.hudProfile != 0;

        String delta = "Δ " + SwapHistory.formatDuration(r.intervalMs());

        String spearDelta = "";
        long spearInterval = SwapHistory.getLastSpearInterval();
        Optional<SwapHistory.SwapRecord> lastLunge = SwapHistory.lastLunge();
        if (spearInterval >= 0 && lastLunge.isPresent()) {
            spearDelta = "Δ рывок " + SwapHistory.formatDuration(spearInterval)
                    + " · серия " + lastLunge.get().lungeSeries();
        }
        String cooldown = lastLunge.map(SwapHistory.SwapRecord::fromOnCooldown).orElse(false) ? "КД!" : "";

        String recordText = "";
        int recordColor = COLOR_GRAY;
        if (fullProfile && lastLunge.isPresent() && lastLunge.get().recordDiffMs() != SwapHistory.NO_PREV_RECORD
                && lastLunge.get().intervalMs() >= 0) {
            if (lastLunge.get().recordDiffMs() <= 0) {
                recordText = "Δ: " + SwapHistory.formatDuration(lastLunge.get().intervalMs())
                        + " (РЕКОРД " + lastLunge.get().recordDiffMs() + "мс)";
                recordColor = 0xFFFFD455;
            } else {
                recordText = "+" + lastLunge.get().recordDiffMs() + "мс к рекорду";
                recordColor = COLOR_GRAY;
            }
        }

        String goalText = "";
        int goalColor = COLOR_GRAY;
        if (fullProfile) {
            SwapConfig.Goal goal = SwapConfig.currentGoal();
            if (SwapConfig.goalCompleted) {
                goalText = "✓ " + goal.name();
                goalColor = COLOR_SPEAR;
            } else if (goal != null) {
                goalText = "Цель: " + SwapConfig.goalProgress + "/" + goal.count();
                goalColor = SwapConfig.goalProgress > 0 ? COLOR_TEXT : COLOR_GRAY;
            }
        }

        int pad = 3;
        int iconSize = 16;
        int textX = pad + iconSize + pad + 6 + iconSize + pad + 2;

        String pitchLabel = "рывок: ";
        float pitch = client.player.getPitch(0.0f);
        String pitchValue = String.format("%.1f°", pitch);
        int pitchColor = pitchColor(pitch);
        boolean showPitch = fullProfile && lastLunge.isPresent();

        int width = textX + client.textRenderer.getWidth(delta) + pad;
        int line2Width = 0;
        if (fullProfile && !spearDelta.isEmpty()) {
            line2Width = client.textRenderer.getWidth(spearDelta)
                    + (cooldown.isEmpty() ? 0 : client.textRenderer.getWidth(cooldown) + 3);
            width = Math.max(width, textX + line2Width + pad);
        }
        if (showPitch) {
            int line3 = client.textRenderer.getWidth(pitchLabel)
                    + client.textRenderer.getWidth(pitchValue);
            width = Math.max(width, textX + line3 + pad);
        }
        if (!recordText.isEmpty()) {
            width = Math.max(width, textX + client.textRenderer.getWidth(recordText) + pad);
        }
        if (!goalText.isEmpty()) {
            width = Math.max(width, textX + client.textRenderer.getWidth(goalText) + pad);
        }

        int row1Y = pad + 1;
        int row2Y = pad + 12;
        int row3Y = pad + 23;

        int rows = fullProfile ? countRows(spearDelta, showPitch, recordText, goalText) : 1;
        int height = row1Y + rows * 11 + pad;

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        int ox = Math.max(0, Math.min(SwapConfig.hudX, Math.max(0, scaledWidth - width)));
        int oy = Math.max(0, Math.min(SwapConfig.hudY, Math.max(0, scaledHeight - height)));
        LAST_RECT[0] = ox;
        LAST_RECT[1] = oy;
        LAST_RECT[2] = width;
        LAST_RECT[3] = height;
        LAST_RECT_VALID = true;

        context.fill(ox, oy, ox + width, oy + height, COLOR_BACKGROUND);

        drawStack(context, r.from(), ox + pad, oy + pad);
        context.drawTextWithShadow(client.textRenderer, "→", ox + pad + iconSize + 1, oy + pad + 4, COLOR_TEXT);
        drawStack(context, r.to(), ox + pad + iconSize + pad + 4, oy + pad);

        context.drawTextWithShadow(client.textRenderer, delta, ox + textX, oy + row1Y, COLOR_TEXT);

        if (fullProfile) {
            int line = row2Y;
            if (!spearDelta.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, spearDelta, ox + textX, oy + line, COLOR_SPEAR);
                if (!cooldown.isEmpty()) {
                    context.drawTextWithShadow(client.textRenderer, cooldown,
                            ox + textX + client.textRenderer.getWidth(spearDelta) + 3, oy + line, COLOR_BAD);
                }
                line += 11;
            }
            if (!recordText.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, recordText, ox + textX, oy + line, recordColor);
                line += 11;
            }
            if (showPitch) {
                int valueX = ox + textX + client.textRenderer.getWidth(pitchLabel);
                context.drawTextWithShadow(client.textRenderer, pitchLabel, ox + textX, oy + line, COLOR_GRAY);
                context.drawTextWithShadow(client.textRenderer, pitchValue, valueX, oy + line, pitchColor);
                line += 11;
            }
            if (!goalText.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, goalText, ox + textX, oy + line, goalColor);
            }
        }
    }

    private static int countRows(String spearDelta, boolean showPitch, String recordText, String goalText) {
        int rows = 1;
        if (!spearDelta.isEmpty()) {
            rows++;
        }
        if (!recordText.isEmpty()) {
            rows++;
        }
        if (showPitch) {
            rows++;
        }
        if (!goalText.isEmpty()) {
            rows++;
        }
        return rows;
    }

    private static int pitchColor(float pitch) {
        if (pitch < PITCH_UP_WARN_DEG) {
            return 0xFFFFFF55;
        }
        if (pitch <= PITCH_MAX_GOOD_DEG) {
            return 0xFF45FF45;
        }
        if (pitch <= PITCH_WARN_DEG) {
            return 0xFFFFFF55;
        }
        if (pitch <= PITCH_BAD_DEG) {
            return 0xFFFFA555;
        }
        return 0xFFFF5555;
    }

    private static void drawStack(DrawContext context, ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x, y);
        } else {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "—", x + 5, y + 4, 0xFF888888);
        }
    }
}
