package dev.elysium.visuals.test;

import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.player.ArmorHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

/** Armor HUD as a 4-slot hotbar to the right of the vanilla one; screenshots armor-*.png. */
public class ArmorHudGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty peaceful");
			server.runCommand("time set 6000");
			server.runCommand("item replace entity @a hotbar.0 with diamond_sword");
			server.runCommand("item replace entity @a hotbar.1 with golden_apple 12");
			server.runCommand("item replace entity @a armor.head with diamond_helmet[damage=300]");
			server.runCommand("item replace entity @a armor.chest with netherite_chestplate");
			server.runCommand("item replace entity @a armor.legs with iron_leggings[damage=10]");
			server.runCommand("item replace entity @a armor.feet with diamond_boots");
			context.runOnClient(mc -> ModuleManager.get().find(ArmorHud.class).setEnabled(true));
			context.waitTicks(30);
			context.takeScreenshot("armor-full");
			server.runCommand("item replace entity @a armor.chest with air");
			server.runCommand("item replace entity @a armor.feet with air");
			context.waitTicks(5);
			context.takeScreenshot("armor-partial");
			// Off-hand slot on the right (left main hand): the bar moves past it.
			server.runCommand("item replace entity @a weapon.offhand with shield");
			context.runOnClient(mc -> mc.options.mainHand().set(HumanoidArm.LEFT));
			context.waitTicks(5);
			context.takeScreenshot("armor-offhand-right");
			context.runOnClient(mc -> mc.options.mainHand().set(HumanoidArm.RIGHT));
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTicks(3);
			context.takeScreenshot("armor-f1-hidden");
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.runOnClient(mc -> ModuleManager.get().find(ArmorHud.class).setEnabled(false));
		}
	}
}
