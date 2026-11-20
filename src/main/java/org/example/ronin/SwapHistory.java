package org.example.ronin;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SwapHistory {

    public static final int MAX_RECORDS = 500;

    private static final ArrayDeque<SwapRecord> RECORDS = new ArrayDeque<>();
    private static long lastSpearSwapMs = -1L;

    private SwapHistory() {
    }

    public record SwapRecord(
            long timeMs,
            String from,
            String to,
            long intervalMs,
            boolean spearInvolved,
            long spearIntervalMs
    ) {
    }

    public static synchronized void record(Item from, Item to) {
        boolean spearInvolved = isSpear(from) || isSpear(to);
        long now = System.currentTimeMillis();

        List<SwapRecord> all = snapshot();
        long intervalMs = all.isEmpty() ? -1L : now - all.get(all.size() - 1).timeMs();

        long spearIntervalMs = -1L;
        if (spearInvolved && lastSpearSwapMs > 0L) {
            spearIntervalMs = now - lastSpearSwapMs;
        }
        if (spearInvolved) {
            lastSpearSwapMs = now;
        }

        RECORDS.addLast(new SwapRecord(now, itemName(from), itemName(to), intervalMs, spearInvolved, spearIntervalMs));
        while (RECORDS.size() > MAX_RECORDS) {
            RECORDS.removeFirst();
        }
    }

    public static synchronized List<SwapRecord> snapshot() {
        return new ArrayList<>(RECORDS);
    }

    public static synchronized void clear() {
        RECORDS.clear();
        lastSpearSwapMs = -1L;
    }

    public static synchronized long getCount() {
        return RECORDS.size();
    }

    public static synchronized long getSpearCount() {
        return RECORDS.stream().filter(SwapRecord::spearInvolved).count();
    }

    public static synchronized Optional<SwapRecord> last() {
        return RECORDS.isEmpty() ? Optional.empty() : Optional.of(RECORDS.getLast());
    }

    public static synchronized long getLastInterval() {
        List<SwapRecord> all = snapshot();
        return all.isEmpty() ? -1L : all.get(all.size() - 1).intervalMs();
    }

    public static synchronized long getLastSpearInterval() {
        List<SwapRecord> all = snapshot();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).spearInvolved() && all.get(i).spearIntervalMs() >= 0L) {
                return all.get(i).spearIntervalMs();
            }
        }
        return -1L;
    }

    public static synchronized double getAverageInterval() {
        List<SwapRecord> all = snapshot();
        long sum = 0L;
        long n = 0L;
        for (SwapRecord r : all) {
            if (r.intervalMs() > 0L) {
                sum += r.intervalMs();
                n++;
            }
        }
        return n == 0L ? -1.0 : (double) sum / (double) n;
    }

    public static boolean isSpear(Item item) {
        if (item == null) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) {
            return false;
        }
        return id.getPath().contains("spear") || id.getPath().contains("trident");
    }

    public static String itemName(Item item) {
        if (item == null) {
            return "рука";
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) {
            return "рука";
        }
        return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())
                ? id.getPath()
                : id.getNamespace() + ":" + id.getPath();
    }

    public static String formatDuration(long ms) {
        if (ms < 0L) {
            return "--";
        }
        if (ms < 1000L) {
            return ms + " мс";
        }
        if (ms < 60_000L) {
            return String.format("%.2f с", ms / 1000.0);
        }
        if (ms < 3_600_000L) {
            return String.format("%dм %02dс", ms / 60_000L, (ms % 60_000L) / 1000L);
        }
        return String.format("%dч %02dм %02dс", ms / 3_600_000L, (ms % 3_600_000L) / 60_000L, (ms % 60_000L) / 1000L);
    }

    public static String formatInterval(long ms) {
        return ms < 0L ? "первый" : "+" + formatDuration(ms);
    }
}
