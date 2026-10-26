package com.autosell;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AutoSellScreen extends Screen {
    private final Screen parent;
    private int page = 0;
    private final List<String> keys = new ArrayList<>();
    private int pages = 1;

    public AutoSellScreen(Screen parent) {
        super(Text.translatable("screen.autosell.title"));
        this.parent = parent;
    }

    private int left() {
        return width / 2 - 150;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 180 - 40) / 24);
    }

    @Override
    protected void init() {
        AutoSellConfig cfg = AutoSellConfig.INSTANCE;
        keys.clear();
        keys.addAll(cfg.prices.keySet());

        int l = left();

        // Row 1: Mode
        addDrawableChild(ButtonWidget.builder(modeLabel(cfg), b -> {
            cfg.mode = "MARKET".equalsIgnoreCase(cfg.mode) ? "HOTBAR" : "MARKET";
            b.setMessage(modeLabel(cfg));
            clearAndInit();
        }).dimensions(l, 30, 300, 20).build());

        // Row 2: AutoNextPage & Highlight
        addDrawableChild(ButtonWidget.builder(toggleLabel("screen.autosell.autonext", cfg.autoNextPage), b -> {
            cfg.autoNextPage = !cfg.autoNextPage;
            b.setMessage(toggleLabel("screen.autosell.autonext", cfg.autoNextPage));
        }).dimensions(l, 54, 146, 20).build());

        addDrawableChild(ButtonWidget.builder(toggleLabel("screen.autosell.highlight", cfg.highlightCheapest), b -> {
            cfg.highlightCheapest = !cfg.highlightCheapest;
            b.setMessage(toggleLabel("screen.autosell.highlight", cfg.highlightCheapest));
        }).dimensions(l + 154, 54, 146, 20).build());

        // Row 3: AutoResell & SmartRounding
        addDrawableChild(ButtonWidget.builder(toggleLabel("screen.autosell.autoresell", cfg.autoResell), b -> {
            cfg.autoResell = !cfg.autoResell;
            b.setMessage(toggleLabel("screen.autosell.autoresell", cfg.autoResell));
        }).dimensions(l, 78, 146, 20).build());

        addDrawableChild(ButtonWidget.builder(toggleLabel("screen.autosell.smartrounding", cfg.smartRounding), b -> {
            cfg.smartRounding = !cfg.smartRounding;
            b.setMessage(toggleLabel("screen.autosell.smartrounding", cfg.smartRounding));
        }).dimensions(l + 154, 78, 146, 20).build());

        // Row 4: HUD & Undercut
        addDrawableChild(ButtonWidget.builder(toggleLabel("screen.autosell.hud", cfg.hudEnabled), b -> {
            cfg.hudEnabled = !cfg.hudEnabled;
            b.setMessage(toggleLabel("screen.autosell.hud", cfg.hudEnabled));
        }).dimensions(l, 102, 146, 20).build());

        TextFieldWidget undercutField = new TextFieldWidget(textRenderer, l + 154, 102, 146, 20, Text.literal("undercut"));
        undercutField.setMaxLength(10);
        undercutField.setText(String.valueOf(cfg.undercut));
        undercutField.setChangedListener(s -> {
            try {
                cfg.undercut = Long.parseLong(s.replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                cfg.undercut = 0;
            }
        });
        addDrawableChild(undercutField);

        // Row 5: Command
        TextFieldWidget commandField = new TextFieldWidget(textRenderer, l, 136, 300, 20, Text.literal("command"));
        commandField.setMaxLength(128);
        commandField.setText(cfg.command);
        commandField.setChangedListener(s -> cfg.command = s);
        addDrawableChild(commandField);

        // Hotbar mode items list
        if ("HOTBAR".equalsIgnoreCase(cfg.mode)) {
            int perPage = rowsPerPage();
            pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
            page = Math.max(0, Math.min(page, pages - 1));

            int start = page * perPage;
            for (int i = 0; i < perPage && start + i < keys.size(); i++) {
                String id = keys.get(start + i);
                int y = 175 + i * 24;

                TextFieldWidget priceField = new TextFieldWidget(textRenderer, l + 190, y, 70, 20, Text.literal("price"));
                priceField.setMaxLength(16);
                priceField.setText(cfg.prices.get(id));
                priceField.setChangedListener(s -> cfg.prices.put(id, s));
                addDrawableChild(priceField);

                addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                    cfg.prices.remove(id);
                    clearAndInit();
                }).dimensions(l + 264, y, 36, 20).build());
            }

            int by = height - 28;
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.autosell.add"), b -> addHeld())
                    .dimensions(l, by, 110, 20).build());

            ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
                page--;
                clearAndInit();
            }).dimensions(l + 114, by, 36, 20).build();
            prev.active = page > 0;
            addDrawableChild(prev);

            ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
                page++;
                clearAndInit();
            }).dimensions(l + 154, by, 36, 20).build();
            next.active = page < pages - 1;
            addDrawableChild(next);

            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.autosell.done"), b -> close())
                    .dimensions(l + 194, by, 106, 20).build());
        } else {
            // Market mode: single Done button
            int by = height - 28;
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.autosell.done"), b -> close())
                    .dimensions(l, by, 300, 20).build());
        }
    }

    private static Text modeLabel(AutoSellConfig cfg) {
        boolean isMarket = "MARKET".equalsIgnoreCase(cfg.mode);
        return Text.literal("Режим продажи: " + (isMarket ? "§aРыночный AH (SpookyTime)" : "§eХотбар (список цен)"));
    }

    private static Text toggleLabel(String key, boolean val) {
        return Text.translatable(key, val ? "§aВКЛ" : "§cВЫКЛ");
    }

    private void addHeld() {
        if (client == null || client.player == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        AutoSellConfig.INSTANCE.prices.putIfAbsent(id, "1000");
        page = Integer.MAX_VALUE;
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("screen.autosell.command_label"), left(), 125, 0xA0A0A0);

        AutoSellConfig cfg = AutoSellConfig.INSTANCE;
        if ("MARKET".equalsIgnoreCase(cfg.mode)) {
            int y = 175;
            ctx.drawTextWithShadow(textRenderer, Text.literal("§6★ Особенности системы AutoSell:"), left(), y, 0xFFFFAA);
            ctx.drawTextWithShadow(textRenderer, Text.literal("• §f[M] §7- Авто-анализ цен AH и продажа предмета из руки"), left(), y + 16, 0xE0E0E0);
            ctx.drawTextWithShadow(textRenderer, Text.literal("• §f[H] §7- Быстрый поиск предмета из руки на AH"), left(), y + 30, 0xE0E0E0);
            ctx.drawTextWithShadow(textRenderer, Text.literal("• §7Авто-проверка: чары (ваниль + кастомные), прочность, починка"), left(), y + 44, 0xA0A0A0);
            ctx.drawTextWithShadow(textRenderer, Text.literal("• §7Подсветка в AH: §a#1 Самый дешёвый §7/ §e#2 §7/ §6#3"), left(), y + 58, 0xA0A0A0);
            ctx.drawTextWithShadow(textRenderer, Text.literal("• §7AutoResell: автоматическое перевыставление лотов из хранилища"), left(), y + 72, 0xA0A0A0);
        } else {
            int perPage = rowsPerPage();
            int start = page * perPage;
            for (int i = 0; i < perPage && start + i < keys.size(); i++) {
                String id = keys.get(start + i);
                int y = 175 + i * 24;
                Identifier ident = Identifier.tryParse(id);
                String name = id;
                if (ident != null) {
                    Item item = Registries.ITEM.get(ident);
                    ctx.drawItem(new ItemStack(item), left(), y + 2);
                    name = item.getName().getString();
                }
                ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, 150), left() + 22, y + 6, 0xFFFFFF);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages),
                    width / 2, height - 42, 0xA0A0A0);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        AutoSellConfig.save();
    }
}
