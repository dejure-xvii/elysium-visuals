package dev.elysium.visuals.client.mixin;

import dev.elysium.visuals.client.menu.ElysiumTitleScreen;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Opens the Elysium main menu wherever the game would open the vanilla title screen. */
@Mixin(Gui.class)
abstract class GuiTitleScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen elysium$mainMenu(Screen screen) {
		MainMenu menu = ModuleManager.get().find(MainMenu.class);
		if (menu == null || !menu.isEnabled()) {
			return screen;
		}
		// null without a world is turned into a new TitleScreen by setScreen itself.
		boolean title = screen == null ? Minecraft.getInstance().level == null : screen.getClass() == TitleScreen.class;
		return title ? new ElysiumTitleScreen() : screen;
	}
}
