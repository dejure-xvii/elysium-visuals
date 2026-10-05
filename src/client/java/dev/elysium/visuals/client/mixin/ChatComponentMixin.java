package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.module.impl.utils.ChatHelper;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ChatHelper: messages that mention you are re-added highlighted, with a colored marker. */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@Unique
	private boolean elysium$readding;

	@Shadow
	private void addMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void elysium$mention(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (elysium$readding || source == GuiMessageSource.SYSTEM_CLIENT) {
			return;
		}
		Component highlighted = ChatHelper.highlight(message);
		if (highlighted == null) {
			return;
		}
		ci.cancel();
		GuiMessageTag marker = new GuiMessageTag(ChatHelper.mentionColor(), null, null, "Mention");
		elysium$readding = true;
		try {
			addMessage(highlighted, signature, source, marker);
		} finally {
			elysium$readding = false;
		}
	}
}
