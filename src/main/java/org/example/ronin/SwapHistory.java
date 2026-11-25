package org.example.ronin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SwapHistory {

    public static final int MAX_RECORDS = 500;

    public static final int AIM_GREEN = 0;
    public static final int AIM_YELLOW = 1;
    public static final int AIM_ORANGE = 2;
    public static final int AIM_RED = 3;

    public static final long NO_PREV_RECORD = -1L;

    private static final ArrayDeque<SwapRecord> RECORDS = new ArrayDeque<>();
    private static final Map<String, Long> PAIR_BEST = new HashMap<>();

    private SwapHistory() {
    }

    public record SwapRecord(
            long timeMs,
            ItemStack from,
            ItemStack to,
            long intervalMs,
            boolean spearInvolved,
            boolean lungeSwap,
            boolean reachSwap,
            boolean fromOnCooldown,
            int lungeSeries,
            long attackGapMs,
            float pitchDeg,
            long recordDiffMs
    ) {
    }

    public static synchronized void record(ItemStack from, ItemStack to, boolean fromOnCooldown,
                                           boolean lungeSwap, boolean reachSwap, long attackGapMs,
                                           float pitchDeg) {
        boolean spearInvolved = lungeSwap || reachSwap || isSpear(from) || isSpear(to);
        long now = System.currentTimeMillis();

        List<SwapRecord> all = snapshotLocked();
        long intervalMs = all.isEmpty() ? -1L : now - all.get(all.size() - 1).timeMs();

        int series = 0;
        long recordDiffMs = NO_PREV_RECORD;
        if (lungeSwap) {
            if (fromOnCooldown) {
                series = 0;
            } else {
                SwapRecord prevLunge = null;
                for (int i = all.size() - 1; i >= 0; i--) {
                    if (all.get(i).lungeSwap() && !all.get(i).fromOnCooldown()) {
                        prevLunge = all.get(i);
                        break;
                    }
                }
                if (prevLunge != null && (SwapConfig.seriesGapMs < 0
                        || now - prevLunge.timeMs() <= SwapConfig.seriesGapMs)) {
                    series = prevLunge.lungeSeries() + 1;
                } else {
                    series = 1;
                }
            }

            String key = pairKey(from, to);
            long prevBest = PAIR_BEST.containsKey(key) ? PAIR_BEST.get(key) : NO_PREV_RECORD;
            if (prevBest == NO_PREV_RECORD) {
                recordDiffMs = NO_PREV_RECORD;
            } else {
                recordDiffMs = prevBest - intervalMs;
            }
            if (intervalMs >= 0 && (prevBest == NO_PREV_RECORD || intervalMs < prevBest)) {
                PAIR_BEST.put(key, intervalMs);
                savePairBest();
            }
        }

        RECORDS.addLast(new SwapRecord(now, from.copy(), to.copy(), intervalMs,
                spearInvolved, lungeSwap, reachSwap, fromOnCooldown, series, attackGapMs,
                pitchDeg, recordDiffMs));
        while (RECORDS.size() > MAX_RECORDS) {
            RECORDS.removeFirst();
        }
    }

    public static synchronized List<SwapRecord> snapshot() {
        return snapshotLocked();
    }

    private static List<SwapRecord> snapshotLocked() {
        return new ArrayList<>(RECORDS);
    }

    public static synchronized void clear() {
        RECORDS.clear();
    }

    public static String formatPercent(long part, long total) {
        if (total <= 0) {
            return "0%";
        }
        return (part * 100 + total / 2) / total + "%";
    }

    public static synchronized long getCount() {
        return RECORDS.size();
    }

    public static synchronized Optional<SwapRecord> last() {
        return RECORDS.isEmpty() ? Optional.empty() : Optional.of(RECORDS.getLast());
    }

    public static synchronized Optional<SwapRecord> lastLunge() {
        List<SwapRecord> all = snapshotLocked();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).lungeSwap()) {
                return Optional.of(all.get(i));
            }
        }
        return Optional.empty();
    }

    public static synchronized long getLastSpearInterval() {
        List<SwapRecord> all = snapshotLocked();
        for (int j = all.size() - 1; j >= 0; j--) {
            if (all.get(j).lungeSwap()) {
                for (int k = j - 1; k >= 0; k--) {
                    if (all.get(k).lungeSwap()) {
                        return all.get(j).timeMs() - all.get(k).timeMs();
                    }
                }
                break;
            }
        }
        return -1L;
    }

    public static String pairKey(ItemStack from, ItemStack to) {
        return itemPath(from) + "|" + itemPath(to);
    }

    private static String itemPath(ItemStack stack) {
        if (stack.isEmpty()) {
            return "<empty>";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "<null>" : id.getPath();
    }

    public static synchronized long getBestForPair(ItemStack from, ItemStack to) {
        Long best = PAIR_BEST.get(pairKey(from, to));
        return best == null ? NO_PREV_RECORD : best;
    }

    public static synchronized long getBestOverallLungeDelta() {
        long best = NO_PREV_RECORD;
        for (long value : PAIR_BEST.values()) {
            if (value >= 0 && (best == NO_PREV_RECORD || value < best)) {
                best = value;
            }
        }
        return best;
    }

    public static int aimClass(float pitchDeg) {
        if (pitchDeg < -10.0f) {
            return AIM_YELLOW;
        }
        if (pitchDeg <= 5.0f) {
            return AIM_GREEN;
        }
        if (pitchDeg <= 15.0f) {
            return AIM_YELLOW;
        }
        if (pitchDeg <= 30.0f) {
            return AIM_ORANGE;
        }
        return AIM_RED;
    }

    public static synchronized long getLungeCount() {
        long count = 0;
        for (SwapRecord r : RECORDS) {
            if (r.lungeSwap()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized long getReachCount() {
        long count = 0;
        for (SwapRecord r : RECORDS) {
            if (r.reachSwap()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized long getEffectiveLungeCount() {
        long count = 0;
        for (SwapRecord r : RECORDS) {
            if (r.lungeSwap() && !r.fromOnCooldown()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized int getBestSeries() {
        int best = 0;
        for (SwapRecord r : RECORDS) {
            if (r.lungeSwap() && r.lungeSeries() > best) {
                best = r.lungeSeries();
            }
        }
        return best;
    }

    public static synchronized long getAvgLungeInterval() {
        List<SwapRecord> all = snapshotLocked();
        long sum = 0;
        long count = 0;
        for (SwapRecord r : all) {
            if (r.lungeSwap() && r.intervalMs() >= 0) {
                sum += r.intervalMs();
                count++;
            }
        }
        return count == 0 ? NO_PREV_RECORD : sum / count;
    }

    public static synchronized long getMedianLungeInterval() {
        List<Long> values = new ArrayList<>();
        for (SwapRecord r : snapshotLocked()) {
            if (r.lungeSwap() && r.intervalMs() >= 0) {
                values.add(r.intervalMs());
            }
        }
        if (values.isEmpty()) {
            return NO_PREV_RECORD;
        }
        values.sort(Long::compare);
        int mid = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(mid)
                : (values.get(mid - 1) + values.get(mid)) / 2;
    }

    public static synchronized long getBestSessionLungeDelta() {
        long best = NO_PREV_RECORD;
        for (SwapRecord r : snapshotLocked()) {
            if (r.lungeSwap() && r.intervalMs() >= 0
                    && (best == NO_PREV_RECORD || r.intervalMs() < best)) {
                best = r.intervalMs();
            }
        }
        return best;
    }

    public static synchronized long[] getAimCounts() {
        long[] counts = new long[4];
        for (SwapRecord r : snapshotLocked()) {
            if (r.lungeSwap()) {
                counts[aimClass(r.pitchDeg())]++;
            }
        }
        return counts;
    }

    private static Path pairBestPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("orlpvpmogg_records.json");
    }

    public static synchronized void loadPairBest() {
        Path path = pairBestPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject obj = new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (obj == null || !obj.has("pairs")) {
                return;
            }
            PAIR_BEST.clear();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.getAsJsonObject("pairs").entrySet()) {
                PAIR_BEST.put(entry.getKey(), entry.getValue().getAsLong());
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void savePairBest() {
        try {
            Path path = pairBestPath();
            Files.createDirectories(path.getParent());
            JsonObject pairs = new JsonObject();
            for (Map.Entry<String, Long> entry : PAIR_BEST.entrySet()) {
                pairs.addProperty(entry.getKey(), entry.getValue());
            }
            JsonObject obj = new JsonObject();
            obj.add("pairs", pairs);
            Files.writeString(path, new Gson().toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isWeapon(ItemStack stack) {
        if (isSpear(stack)) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.get(DataComponentTypes.WEAPON) != null
                || stack.get(DataComponentTypes.KINETIC_WEAPON) != null
                || stack.get(DataComponentTypes.PIERCING_WEAPON) != null) {
            return true;
        }
        AttributeModifiersComponent component = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (component == null) {
            return false;
        }
        for (AttributeModifiersComponent.Entry entry : component.modifiers()) {
            if (entry.attribute() == EntityAttributes.ATTACK_DAMAGE) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSpear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null && (id.getPath().contains("spear") || id.getPath().contains("trident"));
    }

    private static Set<String> enchantKeys(ItemStack stack) {
        Set<String> keys = new HashSet<>();
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null) {
            return keys;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            Identifier id = entry.getKey().map(RegistryKey::getValue).orElse(null);
            if (id == null || "lunge".equals(id.getPath()) || "mending".equals(id.getPath())) {
                continue;
            }
            keys.add(id + "#" + component.getLevel(entry));
        }
        return keys;
    }

    public static List<Text> addedEnchantments(ItemStack from, ItemStack to) {
        return diffEnchantments(to, from);
    }

    public static List<Text> removedEnchantments(ItemStack from, ItemStack to) {
        return diffEnchantments(from, to);
    }

    private static List<Text> diffEnchantments(ItemStack stack, ItemStack other) {
        Set<String> otherKeys = enchantKeys(other);
        List<Text> result = new ArrayList<>();
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null) {
            return result;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            Identifier id = entry.getKey().map(RegistryKey::getValue).orElse(null);
            if (id == null || "lunge".equals(id.getPath()) || "mending".equals(id.getPath())) {
                continue;
            }
            int level = component.getLevel(entry);
            if (!otherKeys.contains(id + "#" + level)) {
                result.add(Text.translatable("enchantment." + id.getNamespace() + "." + id.getPath())
                        .append(" " + toRoman(level)));
            }
        }
        return result;
    }

    private static String toRoman(int number) {
        if (number <= 0) {
            return String.valueOf(number);
        }
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return thousands[number / 1000]
                + hundreds[(number % 1000) / 100]
                + tens[(number % 100) / 10]
                + ones[number % 10];
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
        return String.format("%dч %02dм %02dс", ms / 3_600_000L, (ms % 3_600_000L) / 60_000L, (ms % 3_600_000L) / 1000L);
    }
}
