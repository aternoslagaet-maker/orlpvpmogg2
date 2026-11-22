package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HudOverlay {

    public static final int[] LAST_RECT = new int[4];
    public static boolean LAST_RECT_VALID = false;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SPEAR = 0xFF45FF45;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_BACKGROUND = 0x64000000;

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

        int pad = 3;
        int iconSize = 16;
        int textX = pad + iconSize + pad + 6 + iconSize + pad + 2;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add("Δ " + SwapHistory.formatDuration(r.intervalMs()));
        colors.add(COLOR_TEXT);

        if (!SwapTracker.lastIssueText.isEmpty()) {
            lines.add(SwapTracker.lastIssueText);
            colors.add(SwapTracker.lastIssueColor);
        }

        if (!SwapTracker.lastHitText.isEmpty()) {
            lines.add(SwapTracker.lastHitText);
            colors.add(SwapTracker.lastHitColor);
        }

        if (fullProfile) {
            long spearInterval = SwapHistory.getLastSpearInterval();
            Optional<SwapHistory.SwapRecord> lastLunge = SwapHistory.lastLunge();
            if (spearInterval >= 0 && lastLunge.isPresent()) {
                String spearDelta = "Δ рывок " + SwapHistory.formatDuration(spearInterval)
                        + " · серия " + lastLunge.get().lungeSeries();
                lines.add(spearDelta + (lastLunge.get().fromOnCooldown() ? "  КД!" : ""));
                colors.add(COLOR_SPEAR);
            }

            if (lastLunge.isPresent() && lastLunge.get().recordDiffMs() != SwapHistory.NO_PREV_RECORD
                    && lastLunge.get().intervalMs() >= 0) {
                if (lastLunge.get().recordDiffMs() <= 0) {
                    lines.add("Δ: " + SwapHistory.formatDuration(lastLunge.get().intervalMs())
                            + " (РЕКОРД " + lastLunge.get().recordDiffMs() + "мс)");
                    colors.add(0xFFFFD455);
                } else {
                    lines.add("+" + lastLunge.get().recordDiffMs() + "мс к рекорду");
                    colors.add(COLOR_GRAY);
                }
            }

            if (lastLunge.isPresent()) {
                float pitch = client.player.getPitch(0.0f);
                lines.add("рывок: " + String.format("%.1f°", pitch));
                colors.add(pitchColor(pitch));
            }

            SwapConfig.Goal goal = SwapConfig.currentGoal();
            if (SwapConfig.goalCompleted) {
                lines.add("✓ " + goal.name());
                colors.add(COLOR_SPEAR);
            } else if (goal != null) {
                lines.add("Цель: " + SwapConfig.goalProgress + "/" + goal.count());
                colors.add(SwapConfig.goalProgress > 0 ? COLOR_TEXT : COLOR_GRAY);
            }
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, textX + client.textRenderer.getWidth(line) + pad);
        }
        width = Math.max(width, textX + 4);

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        int ox = Math.max(0, Math.min(SwapConfig.hudX, Math.max(0, scaledWidth - width)));
        int oy = Math.max(0, Math.min(SwapConfig.hudY, Math.max(0, scaledHeight - height(lines.size(), pad))));
        int totalHeight = height(lines.size(), pad);
        LAST_RECT[0] = ox;
        LAST_RECT[1] = oy;
        LAST_RECT[2] = width;
        LAST_RECT[3] = totalHeight;
        LAST_RECT_VALID = true;

        context.fill(ox, oy, ox + width, oy + totalHeight, COLOR_BACKGROUND);

        drawStack(context, r.from(), ox + pad, oy + pad);
        context.drawTextWithShadow(client.textRenderer, "→", ox + pad + iconSize + 1, oy + pad + 4, COLOR_TEXT);
        drawStack(context, r.to(), ox + pad + iconSize + pad + 4, oy + pad);

        int y = oy + pad + 1;
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, lines.get(i), ox + textX, y, colors.get(i));
            y += 11;
        }
    }

    private static int height(int lineCount, int pad) {
        return pad + 1 + lineCount * 11 + pad;
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
