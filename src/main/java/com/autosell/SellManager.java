package com.autosell;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayDeque;

/**
 * Goes through the hotbar slots that have a price, selects each one and sends the sell command.
 * Press M again while it runs to stop.
 */
public class SellManager {
    private static final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private static boolean running = false;
    private static boolean selected = false; // slot already switched, waiting to send command
    private static int wait = 0;
    private static int restoreSlot = 0;

    public static void start(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (running) {
            finish(client, "msg.autosell.stopped");
            return;
        }

        AutoSellConfig cfg = AutoSellConfig.INSTANCE;
        queue.clear();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && priceOf(stack) != null) {
                queue.add(i);
            }
        }

        if (queue.isEmpty()) {
            player.sendMessage(Text.translatable("msg.autosell.nothing"), true);
            return;
        }

        restoreSlot = player.getInventory().selectedSlot;
        running = true;
        selected = false;
        wait = 0;
        player.sendMessage(Text.translatable("msg.autosell.start"), true);
    }

    public static void tick(MinecraftClient client) {
        if (!running) return;
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            running = false;
            queue.clear();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }

        AutoSellConfig cfg = AutoSellConfig.INSTANCE;

        if (queue.isEmpty()) {
            finish(client, "msg.autosell.done");
            return;
        }

        int slot = queue.peek();

        if (!selected) {
            // Step 1: switch hotbar slot and give the client a couple of ticks to sync it to the server.
            player.getInventory().selectedSlot = slot;
            selected = true;
            wait = 2;
            return;
        }

        // Step 2: send the command for the item in this slot.
        ItemStack stack = player.getInventory().getStack(slot);
        String price = priceOf(stack);
        if (price != null) {
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            String itemName = Registries.ITEM.getId(stack.getItem()).getPath();
            long basePrice;
            try {
                basePrice = Long.parseLong(price.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                basePrice = 0;
            }
            String discountedPrice = basePrice > 0
                    ? String.valueOf(Math.max(1, Math.round(basePrice * 0.90)))
                    : price;
            String cmd = cfg.command
                    .replace("{price}", discountedPrice)
                    .replace("{item}", itemName)
                    .replace("{id}", id)
                    .replace("{count}", String.valueOf(stack.getCount()))
                    .trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isEmpty()) {
                client.getNetworkHandler().sendChatCommand(cmd);
            }
        }
        queue.poll();
        selected = false;
        wait = Math.max(1, cfg.delayTicks);
    }

    private static String priceOf(ItemStack stack) {
        String cleanName = AuctionHelper.cleanItemName(stack.getName().getString()).toLowerCase();
        String p = AutoSellConfig.INSTANCE.prices.get(cleanName);
        if (p == null || p.isBlank()) {
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            p = AutoSellConfig.INSTANCE.prices.get(id);
        }
        if (p == null || p.isBlank()) return null;
        return p.trim();
    }

    private static void finish(MinecraftClient client, String msgKey) {
        running = false;
        selected = false;
        queue.clear();
        if (client.player != null) {
            client.player.getInventory().selectedSlot = restoreSlot;
            client.player.sendMessage(Text.translatable(msgKey), true);
        }
    }
}
