package dev.elysium.visuals.client.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ClickFirework extends ClickSwapModule {
    public ClickFirework() {
        super("click_firework", "ClickFirework", "По бинду: свап на фейерверк, использование и возврат (хотбар)");
    }

    @Override
    protected boolean matches(ItemStack stack) {
        return stack.is(Items.FIREWORK_ROCKET);
    }

    @Override
    protected void use(Minecraft mc, LocalPlayer player) {
        if (player.isFallFlying()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }
}