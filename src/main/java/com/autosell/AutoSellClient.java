package com.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AutoSellClient implements ClientModInitializer {
    public static KeyBinding sellKey;
    public static KeyBinding searchKey;
    public static KeyBinding configKey;
    public static KeyBinding resellKey;
    public static KeyBinding fastExpKey;
    public static KeyBinding espKey;
    private static int fastExpCooldown = 0;

    @Override
    public void onInitializeClient() {
        AutoSellConfig.load();

        sellKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.sell", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.autosell"));
        searchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.search", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.autosell"));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.autosell"));
        resellKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.resell", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.autosell"));
        fastExpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.fastexp", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.autosell"));

        HudRenderCallback.EVENT.register(AutoSellHud::render);
        espKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.esp", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.autosell"));

        // Green player ESP. Render in world space relative to the camera; depth testing is
        // disabled by the through-walls layer so boxes remain visible behind blocks.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!AutoSellConfig.INSTANCE.playerEspEnabled || context.world() == null
                    || context.matrixStack() == null || context.consumers() == null
                    || context.camera() == null) return;
            var matrices = context.matrixStack();
            Vec3d cameraPos = context.camera().getPos();
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            var lines = context.consumers().getBuffer(RenderLayer.getLines());
            for (var player : context.world().getPlayers()) {
                if (player == null || player == net.minecraft.client.MinecraftClient.getInstance().player
                        || !player.isAlive() || player.isSpectator()) continue;
                Box box = player.getBoundingBox().expand(0.04);
                VertexRendering.drawBox(matrices, lines, box, 0.1f, 1.0f, 0.25f, 0.95f);
            }
            matrices.pop();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (espKey.wasPressed()) {
                AutoSellConfig.INSTANCE.playerEspEnabled = !AutoSellConfig.INSTANCE.playerEspEnabled;
                AutoSellConfig.save();
                AuctionHelper.actionbar("§a[ESP] " + (AutoSellConfig.INSTANCE.playerEspEnabled ? "§aВключен" : "§cВыключен"));
            }

            while (configKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AutoSellScreen(null));
                }
            }

            while (searchKey.wasPressed()) {
                AuctionHelper.performSearch();
            }

            while (fastExpKey.wasPressed()) {
                AutoSellConfig.INSTANCE.fastExpEnabled = !AutoSellConfig.INSTANCE.fastExpEnabled;
                AutoSellConfig.save();
                AuctionHelper.actionbar("§a[FastExp] " + (AutoSellConfig.INSTANCE.fastExpEnabled ? "§aВключен" : "§cВыключен"));
            }

            while (resellKey.wasPressed()) {
                AutoSellConfig.INSTANCE.autoResell = !AutoSellConfig.INSTANCE.autoResell;
                AutoSellConfig.save();
                AuctionHelper.actionbar("§e[AutoResell] " + (AutoSellConfig.INSTANCE.autoResell ? "§aВключен" : "§cВыключен"));
            }

            while (sellKey.wasPressed()) {
                if ("HOTBAR".equalsIgnoreCase(AutoSellConfig.INSTANCE.mode)) {
                    SellManager.start(client);
                } else {
                    if (AuctionHelper.isRunning()) {
                        AuctionHelper.stop();
                    } else {
                        AuctionHelper.startSell();
                    }
                }
            }

            if ("HOTBAR".equalsIgnoreCase(AutoSellConfig.INSTANCE.mode)) {
                SellManager.tick(client);
            } else {
                AuctionHelper.tick();
            }

            AutoResellManager.tick(client);

            // FastExp: repeatedly use XP bottles while the toggle is enabled.
            if (fastExpCooldown > 0) fastExpCooldown--;
            if (AutoSellConfig.INSTANCE.fastExpEnabled && client.player != null
                    && client.interactionManager != null && fastExpCooldown == 0
                    && client.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                    && client.currentScreen == null) {
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                client.player.swingHand(Hand.MAIN_HAND);
                fastExpCooldown = 2;
            }
        });
    }
}
