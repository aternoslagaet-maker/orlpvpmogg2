package org.example.ronin;

public final class SwapConfig {

    public static int spearSlot = -1;
    public static int partnerSlot = -1;
    public static final int NO_SLOT = -1;

    public static long seriesGapMs = 5000;
    public static final long[] SERIES_GAP_CHOICES = {3000L, 5000L, 10000L, 20000L, -1L};

    private SwapConfig() {
    }

    public static int nextSlot(int current) {
        return current >= 8 ? NO_SLOT : current + 1;
    }

    public static long nextSeriesGap(long current) {
        for (int i = 0; i < SERIES_GAP_CHOICES.length; i++) {
            if (SERIES_GAP_CHOICES[i] == current) {
                return SERIES_GAP_CHOICES[(i + 1) % SERIES_GAP_CHOICES.length];
            }
        }
        return SERIES_GAP_CHOICES[0];
    }
}
