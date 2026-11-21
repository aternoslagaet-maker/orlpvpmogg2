package org.example.ronin;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StatsExporter {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private StatsExporter() {
    }

    public static String exportSession() {
        List<SwapHistory.SwapRecord> all = SwapHistory.snapshot();
        long lunges = SwapHistory.getLungeCount();
        long effective = SwapHistory.getEffectiveLungeCount();
        long reach = SwapHistory.getReachCount();
        long avg = SwapHistory.getAvgLungeInterval();
        long median = SwapHistory.getMedianLungeInterval();
        long best = SwapHistory.getBestSessionLungeDelta();
        int bestSeries = SwapHistory.getBestSeries();
        long[] aims = SwapHistory.getAimCounts();
        long aimTotal = aims[0] + aims[1] + aims[2] + aims[3];

        StringBuilder sb = new StringBuilder();
        sb.append("=== ORL PvP: статистика сессии ===\n");
        sb.append("Экспортировано: ").append(TIME_FORMAT.format(new Date())).append("\n\n");
        sb.append("Записей свапов: ").append(all.size()).append("\n");
        sb.append("Рывков (свап на копьё): ").append(lunges).append("\n");
        sb.append("Свапов ради дальности: ").append(reach).append("\n");
        sb.append("Эффективных КД-скипов: ").append(pct(effective, lunges)).append("\n");
        sb.append("Лучшая серия рывков: ").append(bestSeries).append("\n\n");
        sb.append("Avg Δ (сессия): ").append(SwapHistory.formatDuration(avg)).append("\n");
        sb.append("Median Δ (сессия): ").append(SwapHistory.formatDuration(median)).append("\n");
        sb.append("Лучший Δ (сессия): ").append(SwapHistory.formatDuration(best)).append("\n");
        sb.append("Лучший Δ за всё время: ").append(SwapHistory.formatDuration(SwapHistory.getBestOverallLungeDelta())).append("\n\n");
        sb.append("Эталоны: Идеальный 1-тик = ").append(SwapConfig.BENCHMARK_ONE_TICK_MS).append(" мс; Стабильный PvP = ")
                .append(SwapConfig.BENCHMARK_STABLE_PVP_MS).append(" мс\n\n");
        sb.append("Аим (рывки): зелёный ").append(pct(aims[0], aimTotal))
                .append(", жёлтый ").append(pct(aims[1], aimTotal))
                .append(", оранжевый ").append(pct(aims[2], aimTotal))
                .append(", красный ").append(pct(aims[3], aimTotal)).append("\n");

        Path path = sessionPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    public static void mergeAllTime() {
        Map<String, Long> counters = readAllTime();
        List<SwapHistory.SwapRecord> all = SwapHistory.snapshot();
        long lunges = SwapHistory.getLungeCount();
        long effective = SwapHistory.getEffectiveLungeCount();
        add(counters, "lunges", lunges);
        add(counters, "effectiveLunges", effective);
        add(counters, "reachSwaps", SwapHistory.getReachCount());
        add(counters, "swaps", all.size());
        long[] aims = SwapHistory.getAimCounts();
        for (int i = 0; i < aims.length; i++) {
            add(counters, new String[]{"aimGreen", "aimYellow", "aimOrange", "aimRed"}[i], aims[i]);
        }
        long avg = SwapHistory.getAvgLungeInterval();
        if (avg >= 0) {
            long intervalCount = 0;
            for (SwapHistory.SwapRecord r : all) {
                if (r.lungeSwap() && r.intervalMs() >= 0) {
                    intervalCount++;
                }
            }
            add(counters, "avgSumMs", avg * intervalCount);
            add(counters, "avgCount", intervalCount);
        }
        long best = SwapHistory.getBestSessionLungeDelta();
        if (best >= 0 && (get(counters, "bestDeltaMs") < 0 || best < get(counters, "bestDeltaMs"))) {
            counters.put("bestDeltaMs", best);
        }
        int bestSeries = SwapHistory.getBestSeries();
        if (bestSeries > get(counters, "bestSeries")) {
            counters.put("bestSeries", (long) bestSeries);
        }
        try {
            Path path = allTimePath();
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            List<String> keys = new java.util.ArrayList<>(counters.keySet());
            java.util.Collections.sort(keys);
            for (String key : keys) {
                sb.append(key).append("=").append(counters.get(key)).append("\n");
            }
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static Map<String, Long> readAllTime() {
        Map<String, Long> result = new HashMap<>();
        Path path = allTimePath();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                int index = line.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                try {
                    result.put(line.substring(0, index).trim(), Long.parseLong(line.substring(index + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private static void add(Map<String, Long> counters, String key, long value) {
        counters.put(key, get(counters, key) + value);
    }

    private static long get(Map<String, Long> counters, String key) {
        Long value = counters.get(key);
        return value == null ? 0L : value;
    }

    private static String pct(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return (part * 100 + total / 2) / total + "%";
    }

    private static Path sessionPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("orlpvpmogg").resolve("stats.txt");
    }

    private static Path allTimePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("orlpvpmogg").resolve("alltime.txt");
    }
}
