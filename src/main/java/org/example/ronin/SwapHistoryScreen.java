package org.example.ronin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SwapHistoryScreen extends Screen {

    private static final int TAB_HISTORY = 0;
    private static final int TAB_STATS = 1;
    private static final int TAB_SETTINGS = 2;

    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_HEIGHT = 22;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static final int[] AIM_COLORS = {0xFF45FF45, 0xFFFFFF55, 0xFFFFA555, 0xFFFF5555};
    private static final String[] AIM_NAMES = {"зелёный", "жёлтый", "оранжевый", "красный"};

    private int tab = TAB_HISTORY;
    private int page = 0;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private boolean exported = false;
    private boolean draggingHud = false;

    private final List<int[]> rowBounds = new ArrayList<>();
    private final List<SwapHistory.SwapRecord> visibleRows = new ArrayList<>();

    public SwapHistoryScreen() {
        super(Text.literal("История свапов"));
    }

    public SwapHistoryScreen(Screen parent) {
        super(Text.literal("История свапов"));
    }

    @Override
    protected void init() {
        int tabX = this.width / 2 - 117;
        addDrawableChild(ButtonWidget.builder(Text.literal("История"), b -> switchTab(TAB_HISTORY))
                .dimensions(tabX, 6, 78, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Статистика"), b -> switchTab(TAB_STATS))
                .dimensions(tabX + 78, 6, 78, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Настройки"), b -> switchTab(TAB_SETTINGS))
                .dimensions(tabX + 156, 6, 78, 18).build());

        int y = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
                .dimensions(this.width / 2 + 70, y, 120, 20).build());

        if (this.tab == TAB_HISTORY) {
            this.prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                if (this.page > 0) {
                    this.page--;
                }
            }).dimensions(this.width / 2 - 200, y, 60, 20).build());

            this.nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                this.page++;
            }).dimensions(this.width / 2 - 130, y, 60, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Очистить"), b -> {
                SwapHistory.clear();
                this.page = 0;
            }).dimensions(this.width / 2 - 60, y, 120, 20).build());
        } else if (this.tab == TAB_STATS) {
            addDrawableChild(ButtonWidget.builder(Text.literal(metricLabel()), b -> {
                SwapConfig.nextMetricIndex();
                SwapConfig.save();
                b.setMessage(Text.literal(metricLabel()));
            }).dimensions(this.width / 2 - 200, y, 190, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Экспорт статистики"), b -> {
                StatsExporter.exportSession();
                StatsExporter.mergeAllTime();
                this.exported = true;
                b.setMessage(Text.literal("Экспортировано ✓"));
            }).dimensions(this.width / 2 + 10, y, 190, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal(spearSlotLabel()), b -> {
                SwapConfig.spearSlot = SwapConfig.nextSlot(SwapConfig.spearSlot);
                SwapConfig.save();
                b.setMessage(Text.literal(spearSlotLabel()));
            }).dimensions(this.width / 2 - 150, this.height - 52, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(partnerSlotLabel()), b -> {
                SwapConfig.partnerSlot = SwapConfig.nextSlot(SwapConfig.partnerSlot);
                SwapConfig.save();
                b.setMessage(Text.literal(partnerSlotLabel()));
            }).dimensions(this.width / 2 + 5, this.height - 52, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(seriesGapLabel()), b -> {
                SwapConfig.seriesGapMs = SwapConfig.nextSeriesGap(SwapConfig.seriesGapMs);
                SwapConfig.save();
                b.setMessage(Text.literal(seriesGapLabel()));
            }).dimensions(this.width / 2 - 150, this.height - 78, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(SwapConfig.hudProfileLabel()), b -> {
                SwapConfig.nextHudProfile();
                SwapConfig.save();
                b.setMessage(Text.literal(SwapConfig.hudProfileLabel()));
            }).dimensions(this.width / 2 + 5, this.height - 78, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(soundLabel()), b -> {
                SwapConfig.soundEnabled = !SwapConfig.soundEnabled;
                SwapConfig.save();
                b.setMessage(Text.literal(soundLabel()));
            }).dimensions(this.width / 2 - 150, this.height - 104, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(volumeLabel()), b -> {
                SwapConfig.nextVolumeIndex();
                SwapConfig.save();
                b.setMessage(Text.literal(volumeLabel()));
            }).dimensions(this.width / 2 + 5, this.height - 104, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(goalLabel()), b -> {
                SwapConfig.nextGoalIndex();
                SwapConfig.save();
                b.setMessage(Text.literal(goalLabel()));
            }).dimensions(this.width / 2 - 150, this.height - 130, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Сброс цели"), b -> {
                SwapConfig.goalProgress = 0;
                SwapConfig.goalCompleted = false;
                SwapConfig.save();
            }).dimensions(this.width / 2 + 5, this.height - 130, 145, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Вернуть HUD в угол"), b -> {
                SwapConfig.hudX = 3;
                SwapConfig.hudY = 3;
                SwapConfig.save();
            }).dimensions(this.width / 2 - 150, this.height - 156, 145, 20).build());
        }
    }

    private void switchTab(int newTab) {
        this.tab = newTab;
        this.clearChildren();
        this.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.tab == TAB_HISTORY) {
            renderHistoryTab(context, mouseX, mouseY);
        } else if (this.tab == TAB_STATS) {
            renderStatsTab(context);
        } else {
            renderSettingsTab(context);
        }
    }

    private void renderHistoryTab(DrawContext context, int mouseX, int mouseY) {
        List<SwapHistory.SwapRecord> records = SwapHistory.snapshot();
        int total = records.size();
        int pages = pageCount(total);
        if (this.page >= pages) {
            this.page = pages - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }

        this.prevButton.active = this.page > 0;
        this.nextButton.active = this.page < pages - 1;

        String meta = "Страница " + (this.page + 1) + "/" + pages
                + " | свапов: " + total
                + " | рывков: " + SwapHistory.getLungeCount()
                + " | дальность: " + SwapHistory.getReachCount()
                + " | серия: " + SwapHistory.getBestSeries();
        context.drawCenteredTextWithShadow(this.textRenderer, meta, this.width / 2, 28, 0xAAAAAA);

        this.rowBounds.clear();
        this.visibleRows.clear();

        if (total == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Рабочих свапов (рывок/дальность) пока не было — остальное не пишется"),
                    this.width / 2, 60, 0xAAAAAA);
            return;
        }

        int start = Math.max(0, total - (this.page + 1) * ROWS_PER_PAGE);
        int end = Math.min(total - this.page * ROWS_PER_PAGE, total);

        int x = this.width / 2 - 100;
        int y = 42;

        for (int i = end - 1; i >= start; i--) {
            SwapHistory.SwapRecord r = records.get(i);
            this.rowBounds.add(new int[]{x - 6, y, x + 206, y + ROW_HEIGHT});
            this.visibleRows.add(r);
            renderRow(context, r, x, y);
            y += ROW_HEIGHT;
        }

        SwapHistory.SwapRecord hovered = hoveredRecord(mouseX, mouseY);
        if (hovered != null) {
            context.drawTooltip(this.textRenderer, buildTooltip(hovered), mouseX, mouseY);
        }
    }

    private void renderStatsTab(DrawContext context) {
        int x = this.width / 2 - 130;
        int y = 40;

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Статистика"), this.width / 2, 28, 0xFFFFFF);

        long lunges = SwapHistory.getLungeCount();
        long effective = SwapHistory.getEffectiveLungeCount();
        long reach = SwapHistory.getReachCount();

        context.drawTextWithShadow(this.textRenderer,
                "Рывков: " + lunges, x, y, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                "Свапов ради дальности: " + reach, x + 140, y, 0xFF7FDF7F);
        y += 12;
        context.drawTextWithShadow(this.textRenderer,
                "Эффективных КД-скипов: " + SwapHistory.formatPercent(effective, lunges),
                x, y, effective == lunges && lunges > 0 ? 0xFF45FF45 : 0xFFFF5555);
        y += 12;
        context.drawTextWithShadow(this.textRenderer,
                "Лучшая серия: " + SwapHistory.getBestSeries(), x, y, 0xFFFFFF);
        y += 16;

        String metricName = SwapConfig.metricIndex == 0 ? "Avg Δ" : "Median Δ";
        long metricValue = SwapConfig.metricIndex == 0
                ? SwapHistory.getAvgLungeInterval() : SwapHistory.getMedianLungeInterval();
        context.drawTextWithShadow(this.textRenderer,
                metricName + " (сессия): " + SwapHistory.formatDuration(metricValue),
                x, y, 0xFFFFFF);
        y += 12;
        context.drawTextWithShadow(this.textRenderer,
                "Лучший Δ (сессия): " + SwapHistory.formatDuration(SwapHistory.getBestSessionLungeDelta()),
                x, y, 0xFFFFD455);
        y += 12;

        java.util.Map<String, Long> allTime = StatsExporter.readAllTime();
        long allLunges = allTime.containsKey("lunges") ? allTime.get("lunges") : 0L;
        long allSum = allTime.containsKey("avgSumMs") ? allTime.get("avgSumMs") : 0L;
        long allCount = allTime.containsKey("avgCount") ? allTime.get("avgCount") : 0L;
        long allBest = allTime.containsKey("bestDeltaMs") ? allTime.get("bestDeltaMs") : -1L;
        context.drawTextWithShadow(this.textRenderer,
                "Всё время: рывков " + allLunges + ", avg Δ "
                        + (allCount > 0 ? (allSum / allCount) + " мс" : "--")
                        + ", лучший " + (allBest >= 0 ? allBest + " мс" : "--"),
                x, y, 0xAAAAAA);
        y += 16;

        context.drawTextWithShadow(this.textRenderer, "Эталоны:", x, y, 0xFFFFFF);
        y += 12;
        context.drawTextWithShadow(this.textRenderer,
                "· Идеальный 1-тик: " + SwapConfig.BENCHMARK_ONE_TICK_MS + " мс",
                x, y, 0xFF45FF45);
        y += 11;
        context.drawTextWithShadow(this.textRenderer,
                "· Стабильный PvP: " + SwapConfig.BENCHMARK_STABLE_PVP_MS + " мс",
                x, y, 0xFFFFFF55);
        y += 17;

        context.drawTextWithShadow(this.textRenderer, "Аим (рывки):", x, y, 0xFFFFFF);
        y += 12;

        long[] aims = SwapHistory.getAimCounts();
        long aimTotal = aims[0] + aims[1] + aims[2] + aims[3];
        int barWidth = 260;
        int barHeight = 10;
        int barX = x;
        context.fill(barX - 1, y - 1, barX + barWidth + 1, y + barHeight + 1, 0xFFFFFFFF);
        if (aimTotal == 0) {
            context.fill(barX, y, barX + barWidth, y + barHeight, 0xFF303030);
        } else {
            int offset = 0;
            for (int i = 0; i < aims.length; i++) {
                int segment = (int) Math.round(barWidth * aims[i] / (double) aimTotal);
                if (i == aims.length - 1) {
                    segment = barWidth - offset;
                }
                if (segment > 0) {
                    context.fill(barX + offset, y, barX + offset + segment, y + barHeight, AIM_COLORS[i]);
                }
                offset += segment;
            }
        }
        y += barHeight + 4;
        context.drawTextWithShadow(this.textRenderer, legend(aims, aimTotal), x, y, 0xAAAAAA);
        y += 16;

        if (this.exported) {
            context.drawTextWithShadow(this.textRenderer,
                    "Сохранено: config/orlpvpmogg/stats.txt и alltime.txt",
                    x, y, 0xFF45FF45);
        }

        SwapConfig.Goal goal = SwapConfig.currentGoal();
        if (goal != null) {
            String goalLine = SwapConfig.goalCompleted
                    ? "Цель выполнена: " + goal.name()
                    : "Цель: " + SwapConfig.goalProgress + "/" + goal.count() + " — " + goal.name();
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(goalLine).formatted(Formatting.GRAY),
                    this.width / 2, this.height - 40, 0xFFFFFFFF);
        }
    }

    private void renderSettingsTab(DrawContext context) {
        context.drawCenteredTextWithShadow(this.textRenderer,
                "Все настройки сохраняются в config/orlpvpmogg.json",
                this.width / 2, 26, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "Зажми ЛКМ на HUD и тяни — позиция сохранится автоматически",
                this.width / 2, 38, 0xFFFFD455);
    }

    private void renderRow(DrawContext context, SwapHistory.SwapRecord r, int x, int y) {
        context.fill(x - 6, y, x + 206, y + ROW_HEIGHT,
                r.lungeSwap() ? 0x30144014 : r.reachSwap() ? 0x30284830 : 0x30000000);

        drawStack(context, r.from(), x, y + 3);
        context.drawTextWithShadow(this.textRenderer, "→", x + 19, y + 8, 0xFFFFFF);
        drawStack(context, r.to(), x + 34, y + 3);

        String time = TIME_FORMAT.format(new Date(r.timeMs()));
        context.drawTextWithShadow(this.textRenderer, time, x + 56, y + 3, 0xFFAAAAAA);

        String interval = r.intervalMs() > 0 ? "Δ " + SwapHistory.formatDuration(r.intervalMs()) : "Δ --";
        context.drawTextWithShadow(this.textRenderer, interval, x + 56, y + 13, 0xFFFFFF);

        if (r.lungeSwap()) {
            context.drawTextWithShadow(this.textRenderer, "⚑ рывок", x + 122, y + 3, 0xFF45FF45);
            if (r.fromOnCooldown()) {
                context.drawTextWithShadow(this.textRenderer, "КД", x + 186, y + 3, 0xFFFF5555);
            }
            context.drawTextWithShadow(this.textRenderer, "серия " + r.lungeSeries(), x + 122, y + 13, 0xFFFFFF);
        } else if (r.reachSwap()) {
            context.drawTextWithShadow(this.textRenderer, "дальность", x + 122, y + 3, 0xFF7FDF7F);
        }
    }

    private void drawStack(DrawContext context, ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x, y);
        } else {
            context.drawTextWithShadow(this.textRenderer, "—", x + 5, y + 4, 0xFF888888);
        }
    }

    private SwapHistory.SwapRecord hoveredRecord(double mouseX, double mouseY) {
        for (int i = 0; i < this.rowBounds.size(); i++) {
            int[] b = this.rowBounds.get(i);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                return this.visibleRows.get(i);
            }
        }
        return null;
    }

    private List<Text> buildTooltip(SwapHistory.SwapRecord r) {
        List<Text> lines = new ArrayList<>();
        lines.add(r.from().getName().copy().append(Text.literal(" → ")).append(r.to().getName()));

        MutableText gained = Text.literal("Появились: ");
        gained.append(joinEnchantments(SwapHistory.addedEnchantments(r.from(), r.to())));
        lines.add(gained.formatted(Formatting.GRAY));

        MutableText lost = Text.literal("Пропали: ");
        lost.append(joinEnchantments(SwapHistory.removedEnchantments(r.from(), r.to())));
        lines.add(lost.formatted(Formatting.GRAY));

        if (r.lungeSwap()) {
            if (r.fromOnCooldown()) {
                lines.add(Text.literal("Рывок: КД снятого предмета есть — неэффективно").formatted(Formatting.RED));
                if (r.attackGapMs() >= 0 && r.attackGapMs() > 150) {
                    lines.add(Text.literal("Причина: слишком поздний переход — удар был за "
                            + r.attackGapMs() + " мс до свапа").formatted(Formatting.GRAY));
                } else {
                    lines.add(Text.literal("Причина: слишком рано — снятый предмет ещё на КД").formatted(Formatting.GRAY));
                }
            } else {
                lines.add(Text.literal("Рывок: эффективный КД-скип").formatted(Formatting.GREEN));
            }
            long best = SwapHistory.getBestForPair(r.from(), r.to());
            if (best >= 0 && r.intervalMs() >= 0) {
                lines.add(Text.literal("Рекорд пары: " + best + " мс")
                        .formatted(Formatting.DARK_GRAY));
            }
            lines.add(Text.literal("Удар ЛКМ был за " + SwapHistory.formatDuration(r.attackGapMs())
                    + " до свапа, аим " + String.format("%.1f", r.pitchDeg()) + "°").formatted(Formatting.GRAY));
        } else if (r.reachSwap()) {
            lines.add(Text.literal("Свап с копья — ради дальности, удар одновременно (не рывок)").formatted(Formatting.GRAY));
            lines.add(Text.literal("Удар ЛКМ был за " + SwapHistory.formatDuration(r.attackGapMs())
                    + " до свапа").formatted(Formatting.GRAY));
        } else {
            lines.add(Text.literal("Простая смена слота без удара — не рывок и не дальность").formatted(Formatting.DARK_GRAY));
        }

        return lines;
    }

    private Text joinEnchantments(List<Text> enchantments) {
        if (enchantments.isEmpty()) {
            return Text.literal("нет зачарований").formatted(Formatting.DARK_GRAY);
        }
        MutableText result = Text.empty();
        for (int i = 0; i < enchantments.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(enchantments.get(i));
        }
        return result;
    }

    private String legend(long[] aims, long total) {
        if (total == 0) {
            return "нет рывков";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < aims.length; i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(AIM_NAMES[i]).append(" ").append(SwapHistory.formatPercent(aims[i], total));
        }
        return sb.toString();
    }

    private String metricLabel() {
        return SwapConfig.metricIndex == 0 ? "Метрика: Avg Δ" : "Метрика: Median Δ";
    }

    private String soundLabel() {
        return "Звук: " + (SwapConfig.soundEnabled ? "вкл" : "выкл");
    }

    private String volumeLabel() {
        return "Громкость: " + SwapConfig.VOLUME_CHOICES[
                Math.max(0, Math.min(SwapConfig.volumeIndex, SwapConfig.VOLUME_CHOICES.length - 1))] + "%";
    }

    private String goalLabel() {
        return SwapConfig.goalIndex >= 0 && SwapConfig.goalIndex < SwapConfig.GOALS.length
                ? SwapConfig.GOALS[SwapConfig.goalIndex].name()
                : "Цель: ВЫКЛ";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tab == TAB_SETTINGS && button == 0 && HudOverlay.LAST_RECT_VALID) {
            int[] rect = HudOverlay.LAST_RECT;
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                this.draggingHud = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingHud) {
            SwapConfig.hudX = Math.max(0, SwapConfig.hudX + (int) Math.round(deltaX));
            SwapConfig.hudY = Math.max(0, SwapConfig.hudY + (int) Math.round(deltaY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingHud) {
            this.draggingHud = false;
            SwapConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.tab == TAB_HISTORY) {
            if (verticalAmount < 0) {
                this.page++;
            } else if (verticalAmount > 0) {
                this.page--;
            }
        }
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int pageCount(int total) {
        return (int) Math.max(1, (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private String spearSlotLabel() {
        return "Копьё: " + slotName(SwapConfig.spearSlot);
    }

    private String partnerSlotLabel() {
        return "Свап-предмет: " + slotName(SwapConfig.partnerSlot);
    }

    private String slotName(int slot) {
        return slot == SwapConfig.NO_SLOT ? "любой" : "слот " + (slot + 1);
    }

    private String seriesGapLabel() {
        long gap = SwapConfig.seriesGapMs;
        return "Пауза серии: " + (gap < 0 ? "выкл" : (gap / 1000) + "с");
    }
}
