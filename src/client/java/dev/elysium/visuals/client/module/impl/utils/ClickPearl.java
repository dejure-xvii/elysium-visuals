package dev.elysium.visuals.client.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClickPearl extends ClickSwapModule {
    public ClickPearl() {
        super("click_pearl", "ClickPearl", "По бинду: свап на жемчуг, бросок и возврат на прошлый слот (хотбар)");
    }

    @Override
    protected boolean matches(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL);
    }

    @Override
    protected void use(Minecraft mc, LocalPlayer player) {
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }
}