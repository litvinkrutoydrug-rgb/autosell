package com.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Clean compact green HUD for AutoSell. */
public final class AutoSellHud {
    private static final int PANEL = 0xE9101712;
    private static final int PANEL2 = 0xD916211A;
    private static final int GREEN = 0xFF55F28A;
    private static final int GREEN_DARK = 0xFF258B50;
    private static final int WHITE = 0xFFF1F7F2;
    private static final int MUTED = 0xFF9AAFA0;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private AutoSellHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        AutoSellConfig cfg = AutoSellConfig.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.hudEnabled || mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        String name = (cfg.hudName == null || cfg.hudName.isBlank()) ? "darknight boss" : cfg.hudName.trim();
        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = Math.max(0, entry.getLatency());
        }

        int x = Math.max(4, cfg.hudX);
        int y = Math.max(4, cfg.hudY);
        int pad = 10;
        int h = 30;
        String[] labels = {"D", name, "FPS", "PING", "TIME"};
        String[] values = {"", "", Integer.toString(mc.getCurrentFps()), ping + "ms", LocalTime.now().format(CLOCK)};
        int width = pad * 2 + 20;
        for (int i = 1; i < labels.length; i++) width += tr.getWidth(labels[i]) + 7 + tr.getWidth(values[i]) + 13;
        width = Math.max(width, 175);

        roundedRect(ctx, x - 1, y - 1, width + 2, h + 2, 8, 0xAA35D976);
        roundedRect(ctx, x, y, width, h, 8, PANEL);
        ctx.fill(x, y + 5, x + 3, y + h - 5, GREEN);

        int tx = x + pad;
        int ty = y + 11;
        draw(ctx, tr, "D", tx, ty, GREEN);
        tx += tr.getWidth("D") + 10;
        draw(ctx, tr, "•", tx, ty, GREEN_DARK);
        tx += tr.getWidth("•") + 8;
        draw(ctx, tr, name, tx, ty, WHITE);
        tx += tr.getWidth(name) + 12;
        tx = section(ctx, tr, tx, ty, "FPS", values[2]);
        tx = section(ctx, tr, tx, ty, "PING", values[3]);
        section(ctx, tr, tx, ty, "TIME", values[4]);

        int rowY = y + h + 6;
        String coords = String.format("X %d   Y %d   Z %d", (int)Math.floor(mc.player.getX()),
                (int)Math.floor(mc.player.getY()), (int)Math.floor(mc.player.getZ()));
        String status = "AUTOSELL " + ("HOTBAR".equalsIgnoreCase(cfg.mode) ? "HOTBAR" : "AUCTION");
        int rowW = pad * 2 + tr.getWidth(coords) + 18 + tr.getWidth(status);
        roundedRect(ctx, x - 1, rowY - 1, rowW + 2, 24, 7, 0xAA35D976);
        roundedRect(ctx, x, rowY, rowW, 22, 7, PANEL2);
        draw(ctx, tr, coords, x + pad, rowY + 7, MUTED);
        int sx = x + pad + tr.getWidth(coords) + 18;
        draw(ctx, tr, "•", sx, rowY + 7, GREEN);
        draw(ctx, tr, status, sx + 9, rowY + 7, GREEN);

        if (!mc.player.getStatusEffects().isEmpty()) {
            String effects = "EFFECTS  " + mc.player.getStatusEffects().size();
            int ey = rowY + 28;
            int ew = tr.getWidth(effects) + pad * 2;
            roundedRect(ctx, x - 1, ey - 1, ew + 2, 22, 7, 0xAA35D976);
            roundedRect(ctx, x, ey, ew, 20, 7, PANEL2);
            draw(ctx, tr, effects, x + pad, ey + 6, GREEN);
        }
    }

    private static int section(DrawContext ctx, TextRenderer tr, int x, int y, String label, String value) {
        draw(ctx, tr, label, x, y, MUTED);
        x += tr.getWidth(label) + 5;
        draw(ctx, tr, value, x, y, WHITE);
        return x + tr.getWidth(value) + 12;
    }

    private static void draw(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
        ctx.drawText(tr, Text.literal(text), x, y, color, false);
    }

    private static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) inset = cornerInset(r, r - row);
            else if (row >= h - r) inset = cornerInset(r, row - (h - r) + 1);
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static int cornerInset(int r, int d) {
        double dy = d - 0.5;
        return (int)Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }
}
