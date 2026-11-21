package org.example.ronin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SwapConfig {

    public static final int NO_SLOT = -1;

    public static final long[] SERIES_GAP_CHOICES = {3000, 5000, 10000, 20000, -1};
    public static final int[] VOLUME_CHOICES = {10, 25, 50, 75, 100};
    public static final String[] HUD_PROFILES = {"Минимал для боя", "Полный анализ"};

    public static final long BENCHMARK_ONE_TICK_MS = 50;
    public static final long BENCHMARK_STABLE_PVP_MS = 250;

    public record Goal(String name, int count, long maxDeltaMs, boolean needGreenAim) {
    }

    public static final Goal[] GOALS = {
            new Goal("Разминка: 10 рывков, Δ ≤ 300 мс", 10, 300, false),
            new Goal("Стабильность: 20 рывков, Δ ≤ 200 мс + зелёный аим", 20, 200, true),
            new Goal("Идеал: 20 рывков, Δ ≤ 150 мс + зелёный аим", 20, 150, true),
            new Goal("Мастер: 30 рывков, Δ ≤ 100 мс + зелёный аим", 30, 100, true)
    };

    public static int spearSlot = NO_SLOT;
    public static int partnerSlot = NO_SLOT;
    public static long seriesGapMs = 5000;
    public static int hudProfile = 1;
    public static boolean soundEnabled = true;
    public static int volumeIndex = 2;
    public static int metricIndex = 0;
    public static int goalIndex = 0;
    public static int goalProgress = 0;
    public static boolean goalCompleted = false;
    public static int hudX = 3;
    public static int hudY = 3;

    private SwapConfig() {
    }

    public static float soundVolume() {
        return VOLUME_CHOICES[Math.max(0, Math.min(volumeIndex, VOLUME_CHOICES.length - 1))] / 100.0f;
    }

    public static String hudProfileLabel() {
        return "HUD: " + HUD_PROFILES[Math.max(0, Math.min(hudProfile, HUD_PROFILES.length - 1))];
    }

    public static int nextSlot(int slot) {
        return slot >= 8 ? NO_SLOT : slot + 1;
    }

    public static long nextSeriesGap(long current) {
        for (int i = 0; i < SERIES_GAP_CHOICES.length; i++) {
            if (SERIES_GAP_CHOICES[i] == current) {
                return SERIES_GAP_CHOICES[(i + 1) % SERIES_GAP_CHOICES.length];
            }
        }
        return SERIES_GAP_CHOICES[0];
    }

    public static int nextVolumeIndex() {
        volumeIndex = (volumeIndex + 1) % VOLUME_CHOICES.length;
        return volumeIndex;
    }

    public static int nextHudProfile() {
        hudProfile = (hudProfile + 1) % HUD_PROFILES.length;
        return hudProfile;
    }

    public static int nextMetricIndex() {
        metricIndex = (metricIndex + 1) % 2;
        return metricIndex;
    }

    public static int nextGoalIndex() {
        goalIndex = (goalIndex + 1) % (GOALS.length + 1);
        goalProgress = 0;
        goalCompleted = false;
        return goalIndex;
    }

    public static boolean isGoalEnabled() {
        return goalIndex >= 0 && goalIndex < GOALS.length;
    }

    public static Goal currentGoal() {
        return isGoalEnabled() ? GOALS[goalIndex] : null;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("orlpvpmogg.json");
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj == null) {
                return;
            }
            spearSlot = obj.has("spearSlot") ? obj.get("spearSlot").getAsInt() : NO_SLOT;
            partnerSlot = obj.has("partnerSlot") ? obj.get("partnerSlot").getAsInt() : NO_SLOT;
            seriesGapMs = obj.has("seriesGapMs") ? obj.get("seriesGapMs").getAsLong() : 5000;
            hudProfile = clamp(obj.has("hudProfile") ? obj.get("hudProfile").getAsInt() : 1, 0, HUD_PROFILES.length - 1);
            soundEnabled = !obj.has("soundEnabled") || obj.get("soundEnabled").getAsBoolean();
            volumeIndex = clamp(obj.has("volumeIndex") ? obj.get("volumeIndex").getAsInt() : 2, 0, VOLUME_CHOICES.length - 1);
            metricIndex = clamp(obj.has("metricIndex") ? obj.get("metricIndex").getAsInt() : 0, 0, 1);
            goalIndex = clamp(obj.has("goalIndex") ? obj.get("goalIndex").getAsInt() : 0, 0, GOALS.length);
            goalProgress = obj.has("goalProgress") ? Math.max(0, obj.get("goalProgress").getAsInt()) : 0;
            goalCompleted = obj.has("goalCompleted") && obj.get("goalCompleted").getAsBoolean();
            hudX = obj.has("hudX") ? Math.max(0, obj.get("hudX").getAsInt()) : 3;
            hudY = obj.has("hudY") ? Math.max(0, obj.get("hudY").getAsInt()) : 3;
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("spearSlot", spearSlot);
            obj.addProperty("partnerSlot", partnerSlot);
            obj.addProperty("seriesGapMs", seriesGapMs);
            obj.addProperty("hudProfile", hudProfile);
            obj.addProperty("soundEnabled", soundEnabled);
            obj.addProperty("volumeIndex", volumeIndex);
            obj.addProperty("metricIndex", metricIndex);
            obj.addProperty("goalIndex", goalIndex);
            obj.addProperty("goalProgress", goalProgress);
            obj.addProperty("goalCompleted", goalCompleted);
            obj.addProperty("hudX", hudX);
            obj.addProperty("hudY", hudY);
            Files.writeString(path, new Gson().toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
