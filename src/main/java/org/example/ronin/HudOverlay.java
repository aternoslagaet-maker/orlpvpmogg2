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

        String delta = "Δ " + SwapHistory.formatDuration(r.intervalMs());

        String spearDelta = "";
        long spearInterval = SwapHistory.getLastSpearInterval();
        Optional<SwapHistory.SwapRecord> lastLunge = SwapHistory.lastLunge();
        if (spearInterval >= 0 && lastLunge.isPresent()) {
            spearDelta = "Δ рывок " + SwapHistory.formatDuration(spearInterval)
                    + " · серия " + lastLunge.get().lungeSeries();
        }
        String cooldown = lastLunge.map(SwapHistory.SwapRecord::fromOnCooldown).orElse(false) ? "КД!" : "";

        int pad = 3;
        int iconSize = 16;
        int textX = pad + iconSize + pad + 6 + iconSize + pad + 2;

        String pitchLabel = "рывок: ";
        float pitch = client.player.getPitch(0.0f);
        String pitchValue = String.format("%.1f°", pitch);
        int pitchColor = pitchColor(pitch);
        boolean showPitch = lastLunge.isPresent();

        int width = textX + client.textRenderer.getWidth(delta) + pad;
        if (!spearDelta.isEmpty()) {
            int line2 = client.textRenderer.getWidth(spearDelta)
                    + (cooldown.isEmpty() ? 0 : client.textRenderer.getWidth(cooldown) + 3) + pad;
            width = Math.max(width, textX + line2);
        }
        if (showPitch) {
            int line3 = client.textRenderer.getWidth(pitchLabel)
                    + client.textRenderer.getWidth(pitchValue) + pad;
            width = Math.max(width, textX + line3);
        }

        int row1Y = pad + 1;
        int row2Y = pad + 12;
        int row3Y = pad + 23;
        int height = showPitch ? row3Y + 9 + pad
                : (spearDelta.isEmpty() ? row1Y + 9 + pad : row2Y + 9 + pad);

        context.fill(0, 0, width, height, COLOR_BACKGROUND);

        drawStack(context, r.from(), pad, pad);
        context.drawTextWithShadow(client.textRenderer, "→", pad + iconSize + 1, pad + 4, COLOR_TEXT);
        drawStack(context, r.to(), pad + iconSize + pad + 4, pad);

        context.drawTextWithShadow(client.textRenderer, delta, textX, row1Y, COLOR_TEXT);

        if (!spearDelta.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, spearDelta, textX, row2Y, COLOR_SPEAR);
            if (!cooldown.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, cooldown,
                        textX + client.textRenderer.getWidth(spearDelta) + 3, row2Y, COLOR_BAD);
            }
        }

        if (showPitch) {
            int valueX = textX + client.textRenderer.getWidth(pitchLabel);
            context.drawTextWithShadow(client.textRenderer, pitchLabel, textX, row3Y, COLOR_GRAY);
            context.drawTextWithShadow(client.textRenderer, pitchValue, valueX, row3Y, pitchColor);
        }
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
