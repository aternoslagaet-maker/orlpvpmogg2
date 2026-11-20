package org.example.ronin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;

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
            ItemStack from,
            ItemStack to,
            long intervalMs,
            boolean spearInvolved
    ) {
    }

    public static synchronized void record(ItemStack from, ItemStack to) {
        boolean spearInvolved = isSpear(from) || isSpear(to);
        long now = System.currentTimeMillis();

        List<SwapRecord> all = snapshot();
        long intervalMs = all.isEmpty() ? -1L : now - all.get(all.size() - 1).timeMs();

        if (spearInvolved) {
            lastSpearSwapMs = now;
        }

        RECORDS.addLast(new SwapRecord(now, from.copy(), to.copy(), intervalMs, spearInvolved));
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

    public static synchronized long getLastSpearInterval() {
        List<SwapRecord> all = snapshot();
        for (int j = all.size() - 1; j >= 0; j--) {
            if (all.get(j).spearInvolved()) {
                for (int k = j - 1; k >= 0; k--) {
                    if (all.get(k).spearInvolved()) {
                        return all.get(j).timeMs() - all.get(k).timeMs();
                    }
                }
                break;
            }
        }
        return -1L;
    }

    public static boolean isMeaningfulSwap(ItemStack from, ItemStack to) {
        return !from.isEmpty()
                && !to.isEmpty()
                && from.getItem() != to.getItem()
                && isWeapon(from)
                && isWeapon(to);
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
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

    public static List<Text> enchantmentsOf(ItemStack stack) {
        List<Text> result = new ArrayList<>();
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null) {
            return result;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            Identifier id = entry.getKey().map(key -> key.getValue()).orElse(null);
            if (id == null || "lunge".equals(id.getPath())) {
                continue;
            }
            int level = component.getLevel(entry);
            result.add(Text.translatable("enchantment." + id.getNamespace() + "." + id.getPath())
                    .append(" " + toRoman(level)));
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
