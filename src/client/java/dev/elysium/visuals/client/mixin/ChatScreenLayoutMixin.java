package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.utils.ChatHelper;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** ChatHelper: "/фр" typed in the Russian layout is sent as "/ah". */
@Mixin(ChatScreen.class)
abstract class ChatScreenLayoutMixin {
	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
	private String elysium$fixLayout(String message) {
		return ChatHelper.fixCommand(message);
	}
}
