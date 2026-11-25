package org.example.ronin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HudOverlay {

    public static final int[] LAST_RECT = new int[4];
    public static boolean LAST_RECT_VALID = false;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SPEAR = 0xFF45FF45;
    private static final int COLOR_GOLD = 0xFFFFD455;
    private static final int COLOR_BACKGROUND = 0x64000000;

    private static final float PITCH_MAX_GOOD_DEG = 5.0f;
    private static final float PITCH_WARN_DEG = 15.0f;
    private static final float PITCH_BAD_DEG = 30.0f;
    private static final float PITCH_UP_WARN_DEG = -10.0f;

    private static final String[] KEYS = {
            "delta", "lunge", "series", "cd", "record", "pitch", "goal", "issue", "hit", "pearl"
    };

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
        Optional<SwapHistory.SwapRecord> lastLunge = SwapHistory.lastLunge();
        long spearInterval = SwapHistory.getLastSpearInterval();

        Map<String, String> vars = new HashMap<>();
        buildVars(client, r, lastLunge, spearInterval, vars);

        int pad = 3;
        int iconSize = 16;
        int textX = pad + iconSize + pad + 6 + iconSize + pad + 2;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < SwapConfig.hudLineCount(); i++) {
            if (!SwapConfig.hudLineVisible(i)) {
                continue;
            }
            if (!lineAvailable(i, fullProfile, lastLunge, spearInterval)) {
                continue;
            }
            String text = expand(SwapConfig.hudLineFormat(i), vars);
            if (text.isBlank()) {
                continue;
            }
            lines.add(text);
            colors.add(lineColor(i, lastLunge, client.player.getPitch(0.0f)));
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

    private static void buildVars(MinecraftClient client, SwapHistory.SwapRecord r,
                                  Optional<SwapHistory.SwapRecord> lastLunge, long spearInterval,
                                  Map<String, String> vars) {
        vars.put("delta", SwapHistory.formatDuration(r.intervalMs()));
        vars.put("issue", SwapTracker.lastIssueText);
        vars.put("hit", SwapTracker.lastHitText);
        vars.put("pearl", SwapTracker.lastPearlText);
        if (lastLunge.isPresent() && spearInterval >= 0) {
            vars.put("lunge", SwapHistory.formatDuration(spearInterval));
        } else {
            vars.put("lunge", "");
        }
        vars.put("series", lastLunge.isPresent() ? String.valueOf(lastLunge.get().lungeSeries()) : "");
        vars.put("cd", lastLunge.isPresent() && lastLunge.get().fromOnCooldown() ? "КД!" : "");
        if (lastLunge.isPresent()
                && lastLunge.get().recordDiffMs() != SwapHistory.NO_PREV_RECORD
                && lastLunge.get().intervalMs() >= 0) {
            SwapHistory.SwapRecord l = lastLunge.get();
            if (l.recordDiffMs() <= 0) {
                vars.put("record", "Δ: " + SwapHistory.formatDuration(l.intervalMs())
                        + " (РЕКОРД " + l.recordDiffMs() + "мс)");
            } else {
                vars.put("record", "+" + l.recordDiffMs() + "мс к рекорду");
            }
        } else {
            vars.put("record", "");
        }
        vars.put("pitch", String.format("%.1f", client.player.getPitch(0.0f)));
        SwapConfig.Goal goal = SwapConfig.currentGoal();
        if (SwapConfig.goalCompleted && goal != null) {
            vars.put("goal", "✓ " + goal.name());
        } else if (goal != null) {
            vars.put("goal", "Цель: " + SwapConfig.goalProgress + "/" + goal.count());
        } else {
            vars.put("goal", "");
        }
    }

    private static boolean lineAvailable(int i, boolean fullProfile,
                                         Optional<SwapHistory.SwapRecord> lastLunge, long spearInterval) {
        switch (i) {
            case 0:
                return true;
            case 1:
                return !SwapTracker.lastIssueText.isEmpty();
            case 2:
                return !SwapTracker.lastHitText.isEmpty();
            case 3:
                return !SwapTracker.lastPearlText.isEmpty();
            case 4:
                return fullProfile && spearInterval >= 0 && lastLunge.isPresent();
            case 5:
                return fullProfile && lastLunge.isPresent()
                        && lastLunge.get().recordDiffMs() != SwapHistory.NO_PREV_RECORD
                        && lastLunge.get().intervalMs() >= 0;
            case 6:
                return fullProfile && lastLunge.isPresent();
            case 7:
                return fullProfile;
            default:
                return false;
        }
    }

    private static int lineColor(int i, Optional<SwapHistory.SwapRecord> lastLunge, float livePitch) {
        switch (i) {
            case 1:
                return SwapTracker.lastIssueColor;
            case 2:
                return SwapTracker.lastHitColor;
            case 3:
                return SwapTracker.lastPearlColor;
            case 4:
                return COLOR_SPEAR;
            case 5:
                return lastLunge.isPresent() && lastLunge.get().recordDiffMs() <= 0 ? COLOR_GOLD : COLOR_GRAY;
            case 6:
                return pitchColor(livePitch);
            case 7:
                if (SwapConfig.goalCompleted) {
                    return COLOR_SPEAR;
                }
                return SwapConfig.goalProgress > 0 ? COLOR_TEXT : COLOR_GRAY;
            default:
                return COLOR_TEXT;
        }
    }

    private static String expand(String template, Map<String, String> vars) {
        String out = template;
        for (String key : KEYS) {
            String value = vars.get(key);
            if (value != null) {
                out = out.replace("{" + key + "}", value);
            }
        }
        return out;
    }

    public static String previewLine(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SwapConfig.hudLineVisible(index)) {
            return "— строка скрыта —";
        }
        Optional<SwapHistory.SwapRecord> last = SwapHistory.last();
        Optional<SwapHistory.SwapRecord> lastLunge = SwapHistory.lastLunge();
        long spearInterval = SwapHistory.getLastSpearInterval();
        boolean fullProfile = SwapConfig.hudProfile != 0;
        if (!lineAvailable(index, fullProfile, lastLunge, spearInterval)) {
            return "— нет данных для этой строки —";
        }
        Map<String, String> vars = new HashMap<>();
        if (client.player != null && last.isPresent()) {
            buildVars(client, last.get(), lastLunge, spearInterval, vars);
        } else {
            for (String key : KEYS) {
                vars.put(key, key);
            }
        }
        String text = expand(SwapConfig.hudLineFormat(index), vars);
        if (text.isBlank()) {
            return "— строка пустая (удалена) —";
        }
        return text;
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
