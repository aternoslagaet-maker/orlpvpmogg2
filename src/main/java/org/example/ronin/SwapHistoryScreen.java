package org.example.ronin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SwapHistoryScreen extends Screen {

    private static final int ROWS_PER_PAGE = 10;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private int page = 0;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;

    public SwapHistoryScreen() {
        super(Text.literal("История атрибут-свапов"));
    }

    @Override
    protected void init() {
        int y = this.height - 26;

        this.prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (this.page > 0) {
                this.page--;
                this.clearAndInit();
            }
        }).dimensions(this.width / 2 - 200, y, 60, 20).build());

        this.nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            this.page++;
            this.clearAndInit();
        }).dimensions(this.width / 2 - 130, y, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Очистить"), b -> {
            SwapHistory.clear();
            this.page = 0;
            this.clearAndInit();
        }).dimensions(this.width / 2 - 60, y, 120, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
                .dimensions(this.width / 2 + 70, y, 120, 20).build());

        int pages = pageCount();
        if (this.page >= pages) {
            this.page = pages - 1;
        }
        this.prevButton.active = this.page > 0;
        this.nextButton.active = this.page < pages - 1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        List<SwapHistory.SwapRecord> records = SwapHistory.snapshot();
        int total = records.size();
        int pages = pageCount();
        String meta = "Страница " + (this.page + 1) + "/" + pages + " | всего свапов: " + total;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, meta,
                this.width / 2 - this.textRenderer.getWidth(meta) / 2, 24, 0xAAAAAA);

        int y = 40;
        int start = Math.max(0, total - (this.page + 1) * ROWS_PER_PAGE);
        int end = Math.min(total - this.page * ROWS_PER_PAGE, total);

        for (int i = end - 1; i >= start; i--) {
            SwapHistory.SwapRecord r = records.get(i);
            String line = TIME_FORMAT.format(new Date(r.timeMs()))
                    + "  "
                    + r.from() + " -> " + r.to()
                    + "   Δ " + SwapHistory.formatInterval(r.intervalMs());
            int color = r.spearInvolved() ? 0x55FF55 : 0xFFFFFF;
            context.drawTextWithShadow(this.textRenderer, line, 12, y, color);
            y += 11;
        }

        if (records.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Свапов пока не было"), this.width / 2, 60, 0xAAAAAA);
        }
    }

    private int pageCount() {
        long total = SwapHistory.getCount();
        return (int) Math.max(1, (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }
}
