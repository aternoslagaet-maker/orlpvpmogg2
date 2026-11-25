package org.example.ronin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HudEditorScreen extends Screen {

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;

    private static final String[] PLACEHOLDER_HELP = {
            "{delta} — время свапа   {lunge} — Δ рывка   {series} — серия",
            "{cd} — метка КД   {record} — рекорд пары   {pitch} — прицел",
            "{goal} — цель   {issue} — диагностика   {hit} — попадание",
            "{pearl} — прогноз Pearl Catch"
    };

    private int selected = 0;
    private TextFieldWidget templateField;
    private ButtonWidget visibilityButton;
    private final ButtonWidget[] lineButtons = new ButtonWidget[SwapConfig.hudLineCount()];

    public HudEditorScreen() {
        super(Text.literal("Строки HUD"));
    }

    @Override
    protected void init() {
        int leftX = this.width / 2 - 195;
        for (int i = 0; i < SwapConfig.hudLineCount(); i++) {
            final int idx = i;
            lineButtons[i] = addDrawableChild(ButtonWidget
                    .builder(Text.literal(""), b -> select(idx))
                    .dimensions(leftX, 30 + i * 24, 150, 20).build());
        }

        templateField = new TextFieldWidget(this.textRenderer, this.width / 2 - 35, 30, 230, 20,
                Text.literal("Текст строки"));
        templateField.setMaxLength(120);
        templateField.setChangedListener(text -> SwapConfig.setHudLineFormat(selected, text));
        addDrawableChild(templateField);

        visibilityButton = addDrawableChild(ButtonWidget
                .builder(Text.literal(""), b -> {
                    SwapConfig.setHudLineVisible(selected, !SwapConfig.hudLineVisible(selected));
                    SwapConfig.save();
                    updateLabels();
                })
                .dimensions(this.width / 2 - 35, 56, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить текст"), b -> {
            SwapConfig.resetHudLineFormat(selected);
            templateField.setText(SwapConfig.hudLineFormat(selected));
            SwapConfig.save();
        }).dimensions(this.width / 2 + 85, 56, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
                .dimensions(this.width / 2 - 170, this.height - 30, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить и выйти").formatted(Formatting.GREEN), b -> {
            SwapConfig.save();
            this.close();
        }).dimensions(this.width / 2 + 30, this.height - 30, 140, 20).build());

        select(0);
    }

    private void select(int idx) {
        this.selected = idx;
        this.templateField.setText(SwapConfig.hudLineFormat(idx));
        updateLabels();
    }

    private void updateLabels() {
        for (int i = 0; i < lineButtons.length; i++) {
            if (lineButtons[i] == null) {
                continue;
            }
            String mark = SwapConfig.hudLineVisible(i) ? "✓ " : "✗ ";
            lineButtons[i].setMessage(Text.literal(mark + SwapConfig.HUD_LINE_NAMES[i]));
            lineButtons[i].active = i != this.selected;
        }
        visibilityButton.setMessage(Text.literal(SwapConfig.hudLineVisible(this.selected)
                ? "Строка: видна" : "Строка: скрыта"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, COLOR_TEXT);

        int rx = this.width / 2 - 35;
        context.drawTextWithShadow(this.textRenderer, "Сейчас в HUD:", rx, 88, COLOR_GRAY);
        context.drawTextWithShadow(this.textRenderer, HudOverlay.previewLine(this.selected), rx, 100, COLOR_TEXT);

        context.drawTextWithShadow(this.textRenderer, "Подстановки:", rx, 120, COLOR_GRAY);
        int y = 132;
        for (String help : PLACEHOLDER_HELP) {
            context.drawTextWithShadow(this.textRenderer, help, rx, y, COLOR_TEXT);
            y += 11;
        }
        context.drawTextWithShadow(this.textRenderer,
                "Очисти поле или поставь «Строка: скрыта» — строка исчезнет из HUD.",
                rx, y + 6, COLOR_GRAY);
        context.drawTextWithShadow(this.textRenderer,
                "«Рывок · серия», «Рекорд», «Прицел» и «Цель» видны только в профиле «Полный анализ».",
                rx, y + 18, COLOR_GRAY);
        context.drawTextWithShadow(this.textRenderer,
                "Выбранная строка — неактивная кнопка слева.",
                rx, y + 30, COLOR_GRAY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        SwapConfig.save();
        super.close();
    }
}
