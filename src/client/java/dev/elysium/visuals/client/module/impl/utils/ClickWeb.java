package dev.elysium.visuals.client.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ClickWeb extends ClickSwapModule {
    public ClickWeb() {
        super("click_web", "ClickWeb", "По бинду: свап на паутину, установка на блок под прицелом и возврат (хотбар)");
    }

    @Override
    protected boolean matches(ItemStack stack) {
        return stack.is(Items.COBWEB);
    }

    @Override
    protected void use(Minecraft mc, LocalPlayer player) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }
}