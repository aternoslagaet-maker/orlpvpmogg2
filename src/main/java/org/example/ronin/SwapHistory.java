package org.example.ronin;

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SwapHistory {

    public static final int MAX_RECORDS = 500;
    public static final long SERIES_GAP_MS = 3000L;

    private static final ArrayDeque<SwapRecord> RECORDS = new ArrayDeque<>();

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
            int lungeSeries
    ) {
    }

    public static synchronized void record(ItemStack from, ItemStack to, boolean fromOnCooldown,
                                           boolean lungeSwap, boolean reachSwap) {
        boolean spearInvolved = lungeSwap || reachSwap || isSpear(from) || isSpear(to);
        long now = System.currentTimeMillis();

        List<SwapRecord> all = snapshot();
        long intervalMs = all.isEmpty() ? -1L : now - all.get(all.size() - 1).timeMs();

        int series = 0;
        if (lungeSwap) {
            SwapRecord prev = all.isEmpty() ? null : all.get(all.size() - 1);
            if (prev != null && prev.lungeSwap() && intervalMs >= 0 && intervalMs <= SERIES_GAP_MS) {
                series = prev.lungeSeries() + 1;
            } else {
                series = 1;
            }
        }

        RECORDS.addLast(new SwapRecord(now, from.copy(), to.copy(), intervalMs,
                spearInvolved, lungeSwap, reachSwap, fromOnCooldown, series));
        while (RECORDS.size() > MAX_RECORDS) {
            RECORDS.removeFirst();
        }
    }

    public static synchronized List<SwapRecord> snapshot() {
        return new ArrayList<>(RECORDS);
    }

    public static synchronized void clear() {
        RECORDS.clear();
    }

    public static synchronized long getCount() {
        return RECORDS.size();
    }

    public static synchronized Optional<SwapRecord> last() {
        return RECORDS.isEmpty() ? Optional.empty() : Optional.of(RECORDS.getLast());
    }

    public static synchronized long getLastSpearInterval() {
        List<SwapRecord> all = snapshot();
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

    public static boolean isMeaningfulSwap(ItemStack from, ItemStack to) {
        if (from.isEmpty() || to.isEmpty() || from.getItem() == to.getItem()) {
            return false;
        }
        return (isWeapon(from) && isWeapon(to)) || isSpear(from) || isSpear(to);
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
