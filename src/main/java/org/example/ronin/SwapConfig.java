package org.example.ronin;

public final class SwapConfig {

    public static int spearSlot = -1;
    public static int partnerSlot = -1;
    public static final int NO_SLOT = -1;

    private SwapConfig() {
    }

    public static int nextSlot(int current) {
        return current >= 8 ? NO_SLOT : current + 1;
    }
}
