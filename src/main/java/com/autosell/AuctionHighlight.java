package com.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

/**
 * Highlights top 3 cheapest items in auction screens.
 * Green = #1 cheapest
 * Yellow = #2 cheapest
 * Orange = #3 cheapest
 */
public class AuctionHighlight {
    private static Slot ahSlot1 = null;
    private static Slot ahSlot2 = null;
    private static Slot ahSlot3 = null;

    public static void tick(HandledScreen<?> screen) {
        if (!AutoSellConfig.INSTANCE.highlightCheapest) {
            clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            clear();
            return;
        }

        String title = mc.currentScreen.getTitle().getString();
        if (!isAuctionScreen(title)) {
            clear();
            return;
        }

        ahSlot1 = null;
        ahSlot2 = null;
        ahSlot3 = null;
        long p1 = Long.MAX_VALUE, p2 = Long.MAX_VALUE, p3 = Long.MAX_VALUE;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.hasStack()) continue;
            long total = AuctionHelper.getPrice(slot);
            if (total <= 0) continue;

            long perOne = total / Math.max(slot.getStack().getCount(), 1);

            if (perOne < p1) {
                p3 = p2; ahSlot3 = ahSlot2;
                p2 = p1; ahSlot2 = ahSlot1;
                p1 = perOne; ahSlot1 = slot;
            } else if (perOne < p2) {
                p3 = p2; ahSlot3 = ahSlot2;
                p2 = perOne; ahSlot2 = slot;
            } else if (perOne < p3) {
                p3 = perOne; ahSlot3 = slot;
            }
        }
    }

    public static void clear() {
        ahSlot1 = null;
        ahSlot2 = null;
        ahSlot3 = null;
    }

    public static void renderSlot(DrawContext context, Slot slot) {
        if (!AutoSellConfig.INSTANCE.highlightCheapest) return;

        int rank = 0;
        if (slot == ahSlot1) rank = 1;
        else if (slot == ahSlot2) rank = 2;
        else if (slot == ahSlot3) rank = 3;

        if (rank == 0) return;

        AuctionHelper.renderSlot(context, slot, rank);
    }

    public static boolean isAuctionScreen(String title) {
        if (title == null) return false;
        return title.contains("Аукцион") || title.contains("Auction") || title.contains("Маркет")
                || title.contains("ꈁꀀꈂꌲꈂꀁ") || title.contains("Аукционы") || title.contains("Поиск:")
                || title.contains("Аукционы ") || title.equals("☃️ Аукцион") || title.startsWith("☃️ П:")
                || title.startsWith("☃️ Поиск:") || title.startsWith("0A2z") || title.contains("Поиск: ");
    }
}
