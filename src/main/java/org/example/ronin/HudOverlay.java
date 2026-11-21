package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.Optional;

public final class HudOverlay {

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SPEAR = 0xFF45FF45;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_BACKGROUND = 0x64000000;

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
        if (r.lungeSpearInvolved() && SwapHistory.getLastSpearInterval() >= 0) {
            spearDelta = "Δ копьё " + SwapHistory.formatDuration(SwapHistory.getLastSpearInterval())
                    + " · серия " + r.lungeSeries();
        }
        String cooldown = r.lungeSpearInvolved() && r.fromOnCooldown() ? "КД!" : "";

        int iconSize = 16;
        int pad = 3;
        int textX = pad + iconSize + pad + 6 + iconSize + pad + 2;
        int spearWidth = spearDelta.isEmpty() ? 0 : client.textRenderer.getWidth(spearDelta);
        int width = textX + Math.max(client.textRenderer.getWidth(delta), spearWidth)
                + (cooldown.isEmpty() ? 0 : client.textRenderer.getWidth(cooldown) + 3) + pad;
        int height = pad + iconSize + pad + 11 + pad;

        context.fill(0, 0, width, height, COLOR_BACKGROUND);

        drawStack(context, r.from(), pad, pad);
        context.drawTextWithShadow(client.textRenderer, "→", pad + iconSize + 1, pad + 4, COLOR_TEXT);
        drawStack(context, r.to(), pad + iconSize + pad + 4, pad);

        context.drawTextWithShadow(client.textRenderer, delta, textX, pad + 1, COLOR_TEXT);
        int secondX = textX;
        if (!spearDelta.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, spearDelta, textX, pad + iconSize - 1, COLOR_SPEAR);
            secondX = textX + spearWidth + 3;
        }
        if (!cooldown.isEmpty()) {
            context.drawTextWithShadow(client.textRenderer, cooldown, secondX, pad + iconSize - 1, COLOR_BAD);
        }
    }

    private static void drawStack(DrawContext context, ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x, y);
        } else {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "—", x + 5, y + 4, 0xFF888888);
        }
    }
}
