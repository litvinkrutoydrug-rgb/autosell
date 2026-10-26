package com.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Auto Resell for SpookyTime.
 * Automatically periodically relists expired items from auction storage.
 */
public class AutoResellManager {
    private static final int OPEN_TIMEOUT_TICKS = 100;
    private static final int TICK_DELAY = 6;

    private static State state = State.IDLE;
    private static int cooldownTicks = 0;
    private static int waitTicks = 0;
    private static int noScreenTicks = 0;

    private enum State {
        IDLE,
        OPEN_AH,
        CLICK_STORAGE,
        CLICK_RELIST,
        CLOSE
    }

    public static boolean isBusy() {
        return state != State.IDLE;
    }

    public static void tick(MinecraftClient mc) {
        if (!AutoSellConfig.INSTANCE.autoResell) {
            if (state != State.IDLE) reset();
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (AuctionHelper.isRunning()) {
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(mc);
            case OPEN_AH -> tickOpenAh(mc);
            case CLICK_STORAGE -> tickClickStorage(mc);
            case CLICK_RELIST -> tickClickRelist(mc);
            case CLOSE -> finishCycle(mc);
        }
    }

    private static void tickIdle(MinecraftClient mc) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        resetCycle(mc);
    }

    public static void resetCycle(MinecraftClient mc) {
        if (mc.player == null) {
            state = State.IDLE;
            return;
        }
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        sendCommand(mc, "ah");
        state = State.OPEN_AH;
        noScreenTicks = 0;
        waitTicks = 20;
    }

    private static void tickOpenAh(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            noScreenTicks = 0;
            state = State.CLICK_STORAGE;
            waitTicks = 2;
            return;
        }

        if (++noScreenTicks >= OPEN_TIMEOUT_TICKS) {
            sendCommand(mc, "ah");
            noScreenTicks = 0;
            waitTicks = 20;
            return;
        }
        waitTicks = TICK_DELAY;
    }

    private static void tickClickStorage(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = State.OPEN_AH;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (findRelistSlot(handler) >= 0) {
            state = State.CLICK_RELIST;
            waitTicks = 2;
            return;
        }

        int storage = findStorageSlot(handler);
        if (storage < 0) {
            waitTicks = TICK_DELAY;
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, storage, 0, SlotActionType.PICKUP, mc.player);
        state = State.CLICK_RELIST;
        waitTicks = 8;
    }

    private static void tickClickRelist(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            state = State.OPEN_AH;
            waitTicks = TICK_DELAY;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int relist = findRelistSlot(handler);
        if (relist < 0) {
            waitTicks = TICK_DELAY;
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, relist, 0, SlotActionType.PICKUP, mc.player);
        state = State.CLOSE;
        waitTicks = 6;
    }

    private static void finishCycle(MinecraftClient mc) {
        if (mc.currentScreen != null && mc.player != null) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
        cooldownTicks = Math.max(20, AutoSellConfig.INSTANCE.autoResellInterval * 20);
        waitTicks = 0;
        noScreenTicks = 0;
    }

    public static void reset() {
        state = State.IDLE;
        cooldownTicks = 0;
        waitTicks = 0;
        noScreenTicks = 0;
    }

    private static int findStorageSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == Items.ENDER_CHEST) {
                return i;
            }
        }
        return findTextSlot(handler, "хранилище");
    }

    private static int findRelistSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            String text = stackText(stack);
            if (stack.getItem() == Items.CLOCK && text.contains("перевыстав")) {
                return i;
            }
        }
        return findTextSlot(handler, "перевыставить предметы");
    }

    private static int findTextSlot(GenericContainerScreenHandler handler, String needle) {
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack() && stackText(slot.getStack()).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static String stackText(ItemStack stack) {
        StringBuilder text = new StringBuilder(clean(stack.getName().getString()));
        var lore = stack.getComponents().get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                text.append(' ').append(clean(line.getString()));
            }
        }
        return text.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim().toLowerCase();
    }

    private static void sendCommand(MinecraftClient mc, String command) {
        if (mc.player != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
}
