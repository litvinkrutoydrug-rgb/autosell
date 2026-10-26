package com.autosell.mixin;

import com.autosell.AuctionHighlight;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow @Final protected MinecraftClient client;
    @Shadow protected Slot focusedSlot;

    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void onHandledScreenTick(CallbackInfo ci) {
        AuctionHighlight.tick((HandledScreen<?>)(Object)this);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void autosell$itemScroller(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (client == null || client.player == null || client.interactionManager == null || focusedSlot == null
                || verticalAmount == 0 || client.currentScreen == null) return;
        long window = client.getWindow().getHandle();
        boolean shift = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (!shift || !focusedSlot.hasStack()) return;

        // Shift + mouse wheel quick-moves the hovered stack between player inventory and container.
        int repeats = Math.max(1, Math.min(8, (int)Math.ceil(Math.abs(verticalAmount))));
        for (int i = 0; i < repeats && focusedSlot.hasStack(); i++) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, focusedSlot.id, 0,
                    SlotActionType.QUICK_MOVE, client.player);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void onDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        AuctionHighlight.renderSlot(context, slot);
    }
}
