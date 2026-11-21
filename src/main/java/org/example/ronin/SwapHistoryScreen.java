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

    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_HEIGHT = 22;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private int page = 0;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;

    private final List<int[]> rowBounds = new ArrayList<>();
    private final List<SwapHistory.SwapRecord> visibleRows = new ArrayList<>();

    public SwapHistoryScreen() {
        super(Text.literal("История свапов"));
    }

    @Override
    protected void init() {
        int y = this.height - 26;

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

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
                .dimensions(this.width / 2 + 70, y, 120, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

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

        String meta = "Страница " + (this.page + 1) + "/" + pages + " | свапов: " + total;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, meta, this.width / 2, 24, 0xAAAAAA);

        this.rowBounds.clear();
        this.visibleRows.clear();

        if (total == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Действенных свапов (оружие → оружие) пока не было"),
                    this.width / 2, 60, 0xAAAAAA);
            return;
        }

        int start = Math.max(0, total - (this.page + 1) * ROWS_PER_PAGE);
        int end = Math.min(total - this.page * ROWS_PER_PAGE, total);

        int x = this.width / 2 - 100;
        int y = 44;

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

    private void renderRow(DrawContext context, SwapHistory.SwapRecord r, int x, int y) {
        context.fill(x - 6, y, x + 206, y + ROW_HEIGHT, r.lungeSpearInvolved() ? 0x30144014 : 0x30000000);

        drawStack(context, r.from(), x, y + 3);
        context.drawTextWithShadow(this.textRenderer, "→", x + 19, y + 8, 0xFFFFFF);
        drawStack(context, r.to(), x + 34, y + 3);

        String time = TIME_FORMAT.format(new Date(r.timeMs()));
        context.drawTextWithShadow(this.textRenderer, time, x + 56, y + 3, 0xFFAAAAAA);

        String interval = r.intervalMs() > 0 ? "Δ " + SwapHistory.formatDuration(r.intervalMs()) : "Δ --";
        context.drawTextWithShadow(this.textRenderer, interval, x + 56, y + 13, 0xFFFFFF);

        if (r.lungeSpearInvolved()) {
            context.drawTextWithShadow(this.textRenderer, "⚑ копьё", x + 122, y + 3, 0xFF45FF45);
            if (r.fromOnCooldown()) {
                context.drawTextWithShadow(this.textRenderer, "КД", x + 186, y + 3, 0xFFFF5555);
            }
            context.drawTextWithShadow(this.textRenderer, "серия " + r.lungeSeries(), x + 122, y + 13, 0xFFFFFF);
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

        if (r.lungeSpearInvolved()) {
            lines.add(r.fromOnCooldown()
                    ? Text.literal("КД снятого предмета: есть — свап неэффективен").formatted(Formatting.RED)
                    : Text.literal("КД снятого предмета: нет — как у обычной руки").formatted(Formatting.GREEN));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0) {
            this.page++;
        } else if (verticalAmount > 0) {
            this.page--;
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
}
