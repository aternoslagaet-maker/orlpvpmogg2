package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Optional;

public final class HudOverlay {

    private static final int COLOR_TITLE = 0xFF55FFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SPEAR = 0xFF55FF55;
    private static final int COLOR_BACKGROUND = 0x90000000;

    private HudOverlay() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        String[] lines = buildLines();

        int lineHeight = 10;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, client.textRenderer.getWidth(line));
        }
        context.fill(0, 0, width + 8, lines.length * lineHeight + 6, COLOR_BACKGROUND);

        int y = 4;
        for (int i = 0; i < lines.length; i++) {
            int color = switch (i) {
                case 0 -> COLOR_TITLE;
                case 2 -> COLOR_SPEAR;
                default -> COLOR_TEXT;
            };
            context.drawTextWithShadow(client.textRenderer, lines[i], 4, y, color);
            y += lineHeight;
        }
    }

    private static String[] buildLines() {
        Optional<SwapHistory.SwapRecord> last = SwapHistory.last();
        long count = SwapHistory.getCount();
        long spearCount = SwapHistory.getSpearCount();
        double average = SwapHistory.getAverageInterval();

        String lastSwap = last
                .map(r -> r.from() + " -> " + r.to())
                .orElse("--");
        String lastInterval = last
                .map(r -> SwapHistory.formatInterval(r.intervalMs()))
                .orElse("--");
        String spearInterval = SwapHistory.formatDuration(SwapHistory.getLastSpearInterval());
        String ago = last
                .map(r -> SwapHistory.formatDuration(System.currentTimeMillis() - r.timeMs()) + " назад")
                .orElse("--");

        return new String[]{
                "Атрибут-свапы [O]",
                lastSwap + " (" + ago + ")",
                "Свапов: " + count + " | копьё: " + spearCount,
                "Δ: " + lastInterval + " | копьё: " + spearInterval,
                "Средний Δ: " + (average < 0 ? "--" : String.format("%.2f с", average / 1000.0))
        };
    }
}
