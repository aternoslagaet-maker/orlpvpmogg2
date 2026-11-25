package org.example.ronin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SwapConfig {

    public static final int NO_SLOT = -1;

    public static final long[] SERIES_GAP_CHOICES = {3000, 5000, 10000, 20000, -1};
    public static final int[] VOLUME_CHOICES = {10, 25, 50, 75, 100};
    public static final String[] HUD_PROFILES = {"Минимал для боя", "Полный анализ"};

    public static final String[] SOUND_CHOICES = {
            "обычный клик", "нотный блок", "колокол (золото)", "плинг", "голова зомби",
            "голова скелета", "голова крипера", "голова эндер-дракона", "голова визер-скелета", "голова пиглина"
    };
    private static final String[] SOUND_IDS = {
            null,
            "block.note_block.harp",
            "block.note_block.bell",
            "block.note_block.pling",
            "block.note_block.imitate.zombie",
            "block.note_block.imitate.skeleton",
            "block.note_block.imitate.creeper",
            "block.note_block.imitate.ender_dragon",
            "block.note_block.imitate.wither_skeleton",
            "block.note_block.imitate.piglin"
    };

    public static final String[] HUD_LINE_NAMES = {
            "Δ свапа", "Диагностика", "Попадание", "Pearl Catch",
            "Рывок · серия", "Рекорд", "Прицел", "Цель"
    };

    public static final String[] HUD_LINE_DEFAULTS = {
            "Δ {delta}",
            "{issue}",
            "{hit}",
            "{pearl}",
            "Δ рывок {lunge} · серия {series} {cd}",
            "{record}",
            "рывок: {pitch}°",
            "{goal}"
    };

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
    public static int soundChoiceIndex = 1;
    public static int metricIndex = 0;
    public static int goalIndex = 0;
    public static int goalProgress = 0;
    public static boolean goalCompleted = false;
    public static int hudX = 3;
    public static int hudY = 3;

    private static boolean[] lineVisible = new boolean[HUD_LINE_NAMES.length];
    private static String[] lineFormat = new String[HUD_LINE_NAMES.length];

    static {
        Arrays.fill(lineVisible, true);
        System.arraycopy(HUD_LINE_DEFAULTS, 0, lineFormat, 0, HUD_LINE_NAMES.length);
    }

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

    public static int nextSoundChoiceIndex() {
        soundChoiceIndex = (soundChoiceIndex + 1) % SOUND_CHOICES.length;
        return soundChoiceIndex;
    }

    public static String soundChoiceLabel() {
        return "Звук свапа: " + SOUND_CHOICES[clamp(soundChoiceIndex, 0, SOUND_CHOICES.length - 1)];
    }

    public static SoundEvent resolveSound() {
        int i = clamp(soundChoiceIndex, 0, SOUND_IDS.length - 1);
        String id = SOUND_IDS[i];
        if (id == null) {
            return null;
        }
        return Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
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

    public static boolean hudLineVisible(int index) {
        return lineVisible[clamp(index, 0, HUD_LINE_NAMES.length - 1)];
    }

    public static void setHudLineVisible(int index, boolean value) {
        lineVisible[clamp(index, 0, HUD_LINE_NAMES.length - 1)] = value;
    }

    public static String hudLineFormat(int index) {
        return lineFormat[clamp(index, 0, HUD_LINE_NAMES.length - 1)];
    }

    public static void setHudLineFormat(int index, String value) {
        lineFormat[clamp(index, 0, HUD_LINE_NAMES.length - 1)] = value == null ? "" : value;
    }

    public static void resetHudLineFormat(int index) {
        int i = clamp(index, 0, HUD_LINE_NAMES.length - 1);
        lineFormat[i] = HUD_LINE_DEFAULTS[i];
    }

    public static int hudLineCount() {
        return HUD_LINE_NAMES.length;
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
            soundChoiceIndex = clamp(obj.has("soundChoiceIndex") ? obj.get("soundChoiceIndex").getAsInt() : 1, 0, SOUND_CHOICES.length - 1);
            metricIndex = clamp(obj.has("metricIndex") ? obj.get("metricIndex").getAsInt() : 0, 0, 1);
            goalIndex = clamp(obj.has("goalIndex") ? obj.get("goalIndex").getAsInt() : 0, 0, GOALS.length);
            goalProgress = obj.has("goalProgress") ? Math.max(0, obj.get("goalProgress").getAsInt()) : 0;
            goalCompleted = obj.has("goalCompleted") && obj.get("goalCompleted").getAsBoolean();
            hudX = obj.has("hudX") ? Math.max(0, obj.get("hudX").getAsInt()) : 3;
            hudY = obj.has("hudY") ? Math.max(0, obj.get("hudY").getAsInt()) : 3;
            if (obj.has("hudLineVisible") && obj.get("hudLineVisible").isJsonArray()) {
                JsonArray vis = obj.getAsJsonArray("hudLineVisible");
                for (int i = 0; i < lineVisible.length && i < vis.size(); i++) {
                    lineVisible[i] = vis.get(i).getAsBoolean();
                }
            }
            if (obj.has("hudLineFormat") && obj.get("hudLineFormat").isJsonArray()) {
                JsonArray fmt = obj.getAsJsonArray("hudLineFormat");
                for (int i = 0; i < lineFormat.length && i < fmt.size(); i++) {
                    lineFormat[i] = fmt.get(i).isJsonNull() ? HUD_LINE_DEFAULTS[i] : fmt.get(i).getAsString();
                }
            }
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
            obj.addProperty("soundChoiceIndex", soundChoiceIndex);
            obj.addProperty("metricIndex", metricIndex);
            obj.addProperty("goalIndex", goalIndex);
            obj.addProperty("goalProgress", goalProgress);
            obj.addProperty("goalCompleted", goalCompleted);
            obj.addProperty("hudX", hudX);
            obj.addProperty("hudY", hudY);
            JsonArray vis = new JsonArray();
            for (boolean b : lineVisible) {
                vis.add(b);
            }
            obj.add("hudLineVisible", vis);
            JsonArray fmt = new JsonArray();
            for (String s : lineFormat) {
                fmt.add(s == null ? "" : s);
            }
            obj.add("hudLineFormat", fmt);
            Files.writeString(path, new Gson().toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
