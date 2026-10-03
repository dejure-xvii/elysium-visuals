package dev.elysium.visuals.test;

import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.gui.ClickGuiScreen;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.module.impl.render.Ambience;
import dev.elysium.visuals.client.module.impl.render.BlockOverlay;
import dev.elysium.visuals.client.module.impl.render.CustomSky;
import dev.elysium.visuals.client.module.impl.render.JumpCircle;
import dev.elysium.visuals.client.module.impl.render.Predictions;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.mixin.AbstractContainerScreenAccessor;
import dev.elysium.visuals.client.module.impl.player.AutoTool;
import dev.elysium.visuals.client.module.impl.player.NoFriendDamage;
import dev.elysium.visuals.client.module.impl.render.AspectRatio;
import dev.elysium.visuals.client.module.impl.render.FireworkESP;
import dev.elysium.visuals.client.module.impl.render.Hands;
import dev.elysium.visuals.client.module.impl.render.Keystrokes;
import dev.elysium.visuals.client.module.impl.render.KillEffect;
import dev.elysium.visuals.client.module.impl.render.LootBeams;
import dev.elysium.visuals.client.module.impl.render.ShulkerPreview;
import dev.elysium.visuals.client.module.impl.render.TargetESP;
import dev.elysium.visuals.client.module.impl.utils.AutoAccept;
import dev.elysium.visuals.client.module.impl.utils.ClientSounds;
import dev.elysium.visuals.client.module.impl.utils.FakePlayer;
import dev.elysium.visuals.client.module.impl.utils.ItemScroller;
import dev.elysium.visuals.client.module.impl.utils.NameProtect;
import dev.elysium.visuals.client.module.impl.utils.NotificationsModule;
import dev.elysium.visuals.client.module.impl.utils.UseTracker;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.LivingEntity;
import dev.elysium.visuals.client.module.impl.render.Trail;
import dev.elysium.visuals.client.module.impl.render.MotionBlur;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import java.util.List;
import dev.elysium.visuals.client.module.impl.render.NoRender;
import dev.elysium.visuals.client.module.impl.render.Particles;
import dev.elysium.visuals.client.module.impl.render.SwingAnimation;
import dev.elysium.visuals.client.module.impl.render.ViewModel;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import dev.elysium.visuals.client.particle.ParticleEngine;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ParticleStatus;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.Watermark;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import dev.elysium.visuals.client.hud.TargetTracker;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;
import dev.elysium.visuals.client.theme.ColorSlot;
import dev.elysium.visuals.client.theme.Theme;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.theme.Themes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens the ClickGUI in a test world with the real keybind, screenshots every
 * theme, and checks that the game keeps running and the config is written.
 */
public class ClickGuiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			context.waitTicks(40);

			double closedMs = averageFrameMs(context);

			// Right Shift opens the GUI.
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			context.waitForScreen(ClickGuiScreen.class);
			context.waitTicks(10);

			for (Theme preset : Themes.PRESETS) {
				context.runOnClient(mc -> ThemeManager.get().select(preset));
				context.waitTicks(10);
				double openMs = averageFrameMs(context);
				System.out.printf("[ClickGuiGameTest] frame time: GUI closed %.2f ms, GUI open (%s) %.2f ms%n",
						closedMs, preset.id(), openMs);
			}

			boolean paused = context.computeOnClient(mc -> mc.isPaused());
			if (paused) {
				throw new AssertionError("ClickGUI must not pause the game");
			}

			context.runOnClient(mc -> ThemeManager.get().select(Themes.DEFAULT));
			testModules(context);

			// Switch to the "Темы" tab (first icon in the sidebar).
			int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
			click(context, scale, 31, 62);

			for (Theme preset : Themes.PRESETS) {
				context.runOnClient(mc -> ThemeManager.get().select(preset));
				context.waitTicks(10);
				context.takeScreenshot("clickgui-" + preset.id());
			}

			testMouseAndKeyboard(context);

			context.runOnClient(mc -> {
				ThemeManager themes = ThemeManager.get();
				themes.setCustomColor(ColorSlot.BACKGROUND, 0xEE1B1030);
				themes.setCustomColor(ColorSlot.ACCENT, 0xFFFF6FB5);
				themes.setCustomColor(ColorSlot.TEXT, 0xFFFFF0F8);
				themes.setCustomColor(ColorSlot.SELECTED_TAB, 0xFF3A2150);
			});
			context.waitTicks(10);
			context.takeScreenshot("clickgui-custom");

			// Other screen sizes: GUI scale 3 (font crispness) and a small window (layout).
			context.runOnClient(mc -> ThemeManager.get().select(Themes.DEFAULT));
			context.getInput().resizeWindow(1280, 720);
			context.waitTicks(15);
			context.takeScreenshot("clickgui-scale3");
			// 700x720 → GUI scale 2, only 350 GUI units wide: the sidebar collapses to icons.
			context.getInput().resizeWindow(700, 720);
			context.waitTicks(15);
			context.takeScreenshot("clickgui-narrow");
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(10);

			// Right Shift again closes it (after the close animation).
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			context.waitFor(mc -> mc.gui.screen() == null);

			testHud(context, world);
			testParticles(context, world);
			testRenderStage1(context, world);
			testRenderStage2(context, world);
			testRenderStage3(context, world);
			testRenderStage4(context, world);
			testUtilsUpdate(context, world);
			testPlayerUpdate(context, world);
			testRenderUpdate(context, world);

			Path config = FabricLoader.getInstance().getConfigDir().resolve("elysium-visuals.json");
			context.runOnClient(mc -> ConfigManager.save());
			String json;
			try {
				json = Files.readString(config);
			} catch (Exception e) {
				throw new AssertionError("Config was not written", e);
			}
			if (!json.contains("\"theme\": \"elysium\"") || !json.contains("#FF6FB5")) {
				throw new AssertionError("Unexpected config contents:\n" + json);
			}
			if (!json.contains("\"auto_sprint\"") || !json.contains("\"only_forward\"")) {
				throw new AssertionError("Modules missing from config:\n" + json);
			}
			assertTrue(json.replaceAll("\\s", "").contains("\"auto_sprint\":{\"enabled\":true,\"bind\":71"),
					"Auto Sprint state/bind not saved:\n" + json);
			assertTrue(json.contains("\"watermark/watermark\""), "Dragged HUD position not saved:\n" + json);
			assertTrue(json.contains("\"Notch\""), "Friend not saved:\n" + json);

			testCommands(context, config);
		}
	}

	/** Chat commands typed into the real chat screen; .panic last, since it lasts until restart. */
	private static void testCommands(ClientGameTestContext context, Path config) {
		Path configs = FabricLoader.getInstance().getConfigDir().resolve("elysium-visuals").resolve("configs");
		Module watermark = context.computeOnClient(mc -> ModuleManager.get().find(Watermark.class));
		context.runOnClient(mc -> watermark.setEnabled(true));

		chat(context, ".help");
		context.takeScreenshot("cmd-help");

		chat(context, ".CFG save RW");
		assertTrue(Files.exists(configs.resolve("rw.json")), ".cfg save should create rw.json");
		context.runOnClient(mc -> watermark.setEnabled(false));
		chat(context, ".cfg load rw");
		assertTrue(context.computeOnClient(mc -> watermark.isEnabled()), ".cfg load should restore the saved state");
		chat(context, ".cfg load nope");
		chat(context, ".cfg load");
		chat(context, ".blah");
		context.takeScreenshot("cmd-errors");

		// Friends: add/remove go into the Friends module list.
		StringListSetting friends = context.computeOnClient(mc -> FriendsModule.list());
		chat(context, ".friend add Steve");
		assertTrue(context.computeOnClient(mc -> friends.contains("Steve")), ".friend add failed");
		chat(context, ".friend add steve");
		chat(context, ".friend remove STEVE");
		assertTrue(context.computeOnClient(mc -> !friends.contains("Steve")), ".friend remove failed");
		chat(context, ".friend remove Steve");
		chat(context, ".friend list");
		context.takeScreenshot("cmd-friends");

		// Tab completion: command name, sub-command, then the config name.
		context.getInput().pressKey(GLFW.GLFW_KEY_T);
		context.waitForScreen(ChatScreen.class);
		context.waitTicks(2);
		context.getInput().typeChars(".cf");
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.waitTicks(2);
		assertTrue(".cfg ".equals(chatInput(context)), "Tab should complete .cfg, got '" + chatInput(context) + "'");
		context.waitTicks(2);
		context.takeScreenshot("cmd-tab-popup");
		context.getInput().typeChars("lo");
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.waitTicks(2);
		assertTrue(".cfg load rw ".equals(chatInput(context)), "Tab should complete the config name, got '" + chatInput(context) + "'");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);

		chat(context, ".cfg remove rw");
		assertTrue(!Files.exists(configs.resolve("rw.json")), ".cfg remove should delete rw.json");
		chat(context, ".cfg reset");
		assertTrue(context.computeOnClient(mc -> !ModuleManager.get().find(Watermark.class).isEnabled()
				&& ThemeManager.get().active() == Themes.DEFAULT), ".cfg reset should restore defaults");
		assertTrue(context.computeOnClient(mc -> friends.contains("Notch")), ".cfg reset must keep friends");
		context.takeScreenshot("cmd-reset");

		// .panic: everything off, nothing saved, GUI key ignored.
		context.runOnClient(mc -> watermark.setEnabled(true));
		context.runOnClient(mc -> ConfigManager.save());
		String before = readConfig(config);
		chat(context, ".panic");
		assertTrue(context.computeOnClient(mc -> ModuleManager.get().modules().stream().noneMatch(Module::isEnabled)),
				".panic should disable all modules");
		context.runOnClient(mc -> ConfigManager.saveIfDirty());
		assertTrue(before.equals(readConfig(config)), ".panic must not change the saved config");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitTicks(5);
		assertTrue(context.computeOnClient(mc -> mc.gui.screen() == null), "ClickGUI must not open after .panic");
		context.takeScreenshot("cmd-panic");
	}

	private static ModeSetting targetStyle() {
		for (Setting<?> s : ModuleManager.get().find(Watermark.class).settings()) {
			if (s.id().equals("target_style")) {
				return (ModeSetting) s;
			}
		}
		throw new AssertionError("Watermark has no target_style setting");
	}

	private static void chat(ClientGameTestContext context, String message) {
		context.getInput().pressKey(GLFW.GLFW_KEY_T);
		context.waitForScreen(ChatScreen.class);
		context.waitTicks(2);
		context.getInput().typeChars(message);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitFor(mc -> mc.gui.screen() == null);
		context.waitTicks(2);
	}

	private static String chatInput(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			for (var child : mc.gui.screen().children()) {
				if (child instanceof EditBox box) {
					return box.getValue();
				}
			}
			return null;
		});
	}

	private static String readConfig(Path config) {
		try {
			return Files.readString(config);
		} catch (Exception e) {
			throw new AssertionError("Config was not readable", e);
		}
	}

	/** Player tab (opened by default): toggle and expand Auto Sprint, then look at Render settings. */
	private static void testModules(ClientGameTestContext context) {
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ModuleManager modules = ModuleManager.get();
		Module autoSprint = context.computeOnClient(mc -> modules.byCategory(Category.PLAYER).get(0));

		// Switch on the right of the first card.
		click(context, scale, 384, 70);
		assertTrue(context.computeOnClient(mc -> autoSprint.isEnabled()), "Clicking the switch should enable the module");

		// Clicking the card opens its settings; the bind row is at the bottom.
		click(context, scale, 200, 70);
		context.waitTicks(10);
		context.takeScreenshot("clickgui-player");

		// Bind: click the "Клавиша" row and press G.
		click(context, scale, 300, 119);
		context.getInput().pressKey(GLFW.GLFW_KEY_G);
		context.waitTicks(2);
		assertTrue(context.computeOnClient(mc -> autoSprint.bind() == GLFW.GLFW_KEY_G),
				"Bind should be set to G, got " + context.computeOnClient(mc -> autoSprint.bind()));

		// Render tab → expand Watermark and its "Элементы HUD" list.
		click(context, scale, 31, 116);
		context.waitTicks(10);
		click(context, scale, 200, 70);
		context.waitTicks(10);
		click(context, scale, 200, 97);
		context.waitTicks(10);
		context.takeScreenshot("clickgui-render");

		// Back to Player for the rest of the test.
		click(context, scale, 31, 89);
		context.waitTicks(5);
	}

	/**
	 * Drives the GUI with real input. Positions are derived from the layout in
	 * ClickGuiScreen/ThemesTab for the default 854x480 test window (GUI scale 2).
	 */
	private static void testMouseAndKeyboard(ClientGameTestContext context) {
		context.runOnClient(mc -> ThemeManager.get().select(Themes.DARK));
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());

		// Click the "Светлая" card.
		click(context, scale, 231, 82);
		assertTrue(context.computeOnClient(mc -> ThemeManager.get().active() == Themes.LIGHT),
				"Clicking the light card should select the light theme");

		// Pick the "Фон панели" slot, then click the middle of the hue slider.
		int before = context.computeOnClient(mc -> ThemeManager.get().custom().color(ColorSlot.BACKGROUND));
		click(context, scale, 150, 146);
		click(context, scale, 380, 180);
		int after = context.computeOnClient(mc -> ThemeManager.get().custom().color(ColorSlot.BACKGROUND));
		assertTrue(context.computeOnClient(mc -> ThemeManager.get().isCustomActive()),
				"Editing a color should activate the custom theme");
		assertTrue(before != after, "Hue slider should change the background color");

		// Scroll down, click the HEX field (selects its text) and type a new value.
		context.getInput().setCursorPos(300 * scale, 150 * scale);
		context.getInput().scroll(-5);
		context.waitTicks(10);
		click(context, scale, 320, 190);
		context.getInput().typeChars("#E0203040");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(5);
		int typed = context.computeOnClient(mc -> ThemeManager.get().custom().color(ColorSlot.BACKGROUND));
		assertTrue(typed == 0xE0203040, "HEX field should set the color, got " + Integer.toHexString(typed));
		context.takeScreenshot("clickgui-after-input");

		context.getInput().setCursorPos(300 * scale, 150 * scale);
		context.getInput().scroll(5);
		context.waitTicks(10);
	}

	/** All Watermark HUD elements with real data, F1 hiding, and dragging in the ClickGUI editor. */
	private static void testHud(ClientGameTestContext context, TestSingleplayerContext world) {
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.runOnClient(mc -> {
			ModuleManager.get().modules().forEach(m -> m.setEnabled(true));
			Watermark watermark = ModuleManager.get().find(Watermark.class);
			MultiSelectSetting elements = (MultiSelectSetting) watermark.settings().get(0);
			Set<String> all = new HashSet<>();
			elements.options().forEach(o -> all.add(o.id()));
			elements.set(all);
			FriendsModule friends = ModuleManager.get().find(FriendsModule.class);
			((StringListSetting) friends.settings().get(0)).add("Notch");
			// Cooldown on the client is enough for the HUD.
			mc.player.getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 200);
		});
		world.getServer().runCommand("effect give @a minecraft:speed 90 1");
		world.getServer().runCommand("effect give @a minecraft:strength 8 0");
		world.getServer().runCommand("summon minecraft:zombie ~2 ~ ~ {NoAI:1b}");
		context.waitTicks(20);
		context.runOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Zombie) {
					mc.gameMode.attack(mc.player, e);
					break;
				}
			}
		});
		context.waitTicks(20);
		context.takeScreenshot("hud-ingame");
		context.runOnClient(mc -> ThemeManager.get().select(Themes.LIQUID_GLASS));
		context.waitTicks(10);
		context.takeScreenshot("hud-glass");
		context.runOnClient(mc -> ThemeManager.get().select(Themes.DEFAULT));

		// Every target style; the zombie gets some gear and absorption, and is "hit" again so it stays shown.
		world.getServer().runCommand("item replace entity @e[type=minecraft:zombie,limit=1] armor.head with minecraft:diamond_helmet");
		world.getServer().runCommand("item replace entity @e[type=minecraft:zombie,limit=1] armor.chest with minecraft:iron_chestplate");
		world.getServer().runCommand("item replace entity @e[type=minecraft:zombie,limit=1] weapon.mainhand with minecraft:diamond_sword[enchantments={sharpness:3}]");
		world.getServer().runCommand("effect give @e[type=minecraft:zombie] minecraft:absorption 60 1");
		for (String style : List.of("compact", "minimal", "classic", "capsule", "card")) {
			context.runOnClient(mc -> {
				targetStyle().set(style);
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof Zombie z) {
						TargetTracker.onHit(z);
					}
				}
			});
			context.waitTicks(10);
			if (!style.equals("card")) {
				context.takeScreenshot("hud-target-" + style);
			}
		}

		// F1 hides everything.
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(5);
		context.takeScreenshot("hud-f1");
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(5);

		// Editor: open the ClickGUI and drag the watermark plate.
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClickGuiScreen.class);
		context.waitTicks(15);
		// "Редактор HUD" button at the bottom of the sidebar hides the window.
		click(context, scale, 31, 209);
		context.waitTicks(10);
		context.takeScreenshot("hud-editor");
		// Only the Watermark's elements from here on, so the watermark plate is the top-left one.
		context.runOnClient(mc -> ModuleManager.get().modules().forEach(m -> {
			if (!m.hudElements().isEmpty() && !(m instanceof Watermark)) {
				m.setEnabled(false);
			}
		}));
		context.waitTicks(15);
		context.getInput().setCursorPos(12 * scale, 10 * scale);
		context.waitTick();
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 10; i++) {
			context.getInput().setCursorPos((12 + i * 6) * scale, (10 + i * 8) * scale);
			context.waitTick();
		}
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("hud-dragged");
		// First Right Shift leaves edit mode, the second closes the GUI.
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitTicks(10);
		assertTrue(context.computeOnClient(mc -> mc.gui.screen() instanceof ClickGuiScreen), "RShift should only leave edit mode");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitFor(mc -> mc.gui.screen() == null);

		testChatDrag(context, scale);
	}

	/** HUD elements can also be dragged while the chat is open; they snap and stay on screen. */
	private static void testChatDrag(ClientGameTestContext context, int scale) {
		context.getInput().pressKey(GLFW.GLFW_KEY_T);
		context.waitForScreen(ChatScreen.class);
		context.waitTicks(5);
		HudElement mark = context.computeOnClient(mc -> ModuleManager.get().find(Watermark.class).hudElements().get(0));
		int startX = context.computeOnClient(mc -> mark.x());
		int startY = context.computeOnClient(mc -> mark.y());
		int grabX = startX + 10, grabY = startY + 5;
		context.getInput().setCursorPos(grabX * scale, grabY * scale);
		context.waitTick();
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		// Drag far past the right edge: the plate must stop at the screen border.
		for (int i = 1; i <= 12; i++) {
			context.getInput().setCursorPos((grabX + i * 60) * scale, (grabY + i * 3) * scale);
			context.waitTick();
		}
		context.takeScreenshot("hud-chat-drag");
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		int sw = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int endX = context.computeOnClient(mc -> mark.x());
		int endW = context.computeOnClient(mc -> mark.width());
		assertTrue(endX != startX, "Watermark should move when dragged in the chat");
		assertTrue(endX + endW <= sw, "Dragged element must stay on screen, right edge " + (endX + endW) + " > " + sw);
		// Snapped to the right screen margin (4 GUI units).
		assertTrue(sw - (endX + endW) == 4, "Expected the plate to snap to the right margin, gap " + (sw - (endX + endW)));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);

		// A different GUI scale must not push it off screen.
		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(10);
		int sw3 = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int x3 = context.computeOnClient(mc -> mark.x() + mark.width());
		assertTrue(x3 <= sw3, "Element off screen after resize: " + x3 + " > " + sw3);
		context.takeScreenshot("hud-after-resize");
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(10);
	}

	/** Turns off every Render-tab effect module (earlier steps switch all modules on). */
	private static void disableRenderEffects(ClientGameTestContext context) {
		context.runOnClient(mc -> ModuleManager.get().byCategory(Category.RENDER).forEach(m -> {
			if (m.hudElements().isEmpty()) {
				m.setEnabled(false);
			}
		}));
	}

	/** ViewModel, SwingAnimation, NoRender, MotionBlur: mixins apply and frames render. */
	private static void testRenderStage1(ClientGameTestContext context, TestSingleplayerContext world) {
		disableRenderEffects(context);
		world.getServer().runCommand("time set day");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:shield");
		context.waitTicks(10);
		context.takeScreenshot("s1-hands-vanilla");
		context.runOnClient(mc -> {
			Module vm = ModuleManager.get().find(ViewModel.class);
			((NumberSetting) vm.settings().get(0)).set(0.2);   // right X
			((NumberSetting) vm.settings().get(3)).set(1.4);   // right scale
			((NumberSetting) vm.settings().get(4)).set(-0.2);  // left X
			vm.setEnabled(true);
			ModuleManager.get().find(SwingAnimation.class).setEnabled(true);
			ModuleManager.get().find(NoRender.class).setEnabled(true);
		});
		context.waitTicks(5);
		context.takeScreenshot("s1-viewmodel");
		context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(2);
		context.takeScreenshot("s1-swing");

		// High-contrast pillars all around, so the blur is visible while turning.
		for (int a = 0; a < 360; a += 30) {
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * 6), z = (int) Math.round(Math.sin(Math.toRadians(a)) * 6);
			world.getServer().runCommand("fill ~" + x + " ~ ~" + z + " ~" + x + " ~4 ~" + z
					+ (a % 60 == 0 ? " minecraft:black_concrete" : " minecraft:white_concrete"));
		}
		context.runOnClient(mc -> {
			MotionBlur blur = ModuleManager.get().find(MotionBlur.class);
			((NumberSetting) blur.settings().get(0)).set(1.0);
			blur.setEnabled(true);
		});
		context.waitTicks(10);
		context.takeScreenshot("s1-motionblur-still");
		for (int i = 0; i < 12; i++) {
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 9));
			context.waitTick();
		}
		context.takeScreenshot("s1-motionblur");
		context.runOnClient(mc -> {
			ModuleManager.get().find(MotionBlur.class).setEnabled(false);
			ModuleManager.get().find(ViewModel.class).setEnabled(false);
			ModuleManager.get().find(SwingAnimation.class).setEnabled(false);
			ModuleManager.get().find(NoRender.class).setEnabled(false);
		});
		context.waitTicks(5);
		context.takeScreenshot("s1-after-disable");
	}

	/** Ambience, CustomSky, BlockOverlay. */
	private static void testRenderStage2(ClientGameTestContext context, TestSingleplayerContext world) {
		Ambience ambience = context.computeOnClient(mc -> ModuleManager.get().find(Ambience.class));
		CustomSky sky = context.computeOnClient(mc -> ModuleManager.get().find(CustomSky.class));
		disableRenderEffects(context);
		// Look slightly up so sky and terrain are both on screen.
		context.runOnClient(mc -> {
			mc.player.setYRot(-90);
			mc.player.setXRot(-10);
		});
		world.getServer().runCommand("execute at @p run setblock ~3 ~ ~ minecraft:oak_stairs");
		context.waitTicks(5);

		context.runOnClient(mc -> {
			((ModeSetting) ambience.settings().get(0)).set("evening");
			((NumberSetting) ambience.settings().get(2)).set(1.8);       // saturation
			((ModeSetting) ambience.settings().get(3)).set("blur");
			ambience.setEnabled(true);
		});
		context.waitTicks(10);
		context.takeScreenshot("s2-ambience");
		long shown = context.computeOnClient(mc -> mc.level.getOverworldClockTime() % 24000);
		assertTrue(shown == 12500, "Ambience should show evening (12500), got " + shown);
		context.runOnClient(mc -> ambience.setEnabled(false));

		for (String mode : List.of("aurora", "sakura", "plasma2", "northern", "summer", "caustics")) {
			context.runOnClient(mc -> {
				((ModeSetting) sky.settings().get(0)).set(mode);
				sky.setEnabled(true);
			});
			context.waitTicks(4);
			context.takeScreenshot("s2-sky-" + mode);
		}
		context.runOnClient(mc -> sky.setEnabled(false));

		// BlockOverlay on the stairs under the crosshair.
		context.runOnClient(mc -> {
			BlockPos feet = mc.player.blockPosition();
			mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(feet.east(3)));
			ModuleManager.get().find(BlockOverlay.class).setEnabled(true);
		});
		context.waitTicks(15);
		context.takeScreenshot("s2-block-overlay");
		context.runOnClient(mc -> ModuleManager.get().find(BlockOverlay.class).setEnabled(false));
	}

	/** JumpCircle, Trail, Predictions. */
	private static void testRenderStage3(ClientGameTestContext context, TestSingleplayerContext world) {
		disableRenderEffects(context);
		world.getServer().runCommand("execute at @p run fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air replace minecraft:white_concrete");
		world.getServer().runCommand("execute at @p run fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air replace minecraft:black_concrete");
		// Open ground away from the earlier test blocks, walking west.
		world.getServer().runCommand("execute as @p at @s run tp @s ~-20 ~ ~ -90 0");
		context.waitTicks(10);
		context.runOnClient(mc -> {
			ModuleManager.get().find(JumpCircle.class).setEnabled(true);
			ModuleManager.get().find(Trail.class).setEnabled(true);
			mc.player.setYRot(90);
			mc.player.setXRot(60);
		});
		// Walk forward: the trail appears under the feet.
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 25);
		context.takeScreenshot("s3-trail");
		// Jump: a circle appears where we took off.
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 2);
		context.waitTicks(8);
		context.takeScreenshot("s3-jump-circle");
		// Same from behind (third person), walking and jumping.
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.player.setXRot(35);
		});
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 20);
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 2);
		context.waitTicks(6);
		context.takeScreenshot("s3-trail-third-person");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(40);

		Predictions predictions = context.computeOnClient(mc -> ModuleManager.get().find(Predictions.class));
		context.runOnClient(mc -> {
			predictions.setEnabled(true);
			mc.player.setXRot(-15);
		});
		world.getServer().runCommand("execute at @p rotated ~ 0 run summon minecraft:arrow ^ ^3 ^2 {Motion:[0.0d,0.7d,1.2d]}");
		world.getServer().runCommand("execute at @p rotated ~ 0 run summon minecraft:snowball ^1 ^3 ^2 {Motion:[0.3d,0.6d,0.9d]}");
		world.getServer().runCommand("execute at @p rotated ~ 0 run summon minecraft:item ^-1 ^3 ^2 {Item:{id:\"minecraft:diamond\",count:1},Motion:[-0.1d,0.4d,0.4d]}");
		context.waitTicks(3);
		int tracked = context.computeOnClient(mc -> predictions.trackedCount());
		context.takeScreenshot("s3-predictions");
		assertTrue(tracked >= 2, "Predictions should track the flying arrow/snowball/item, got " + tracked);
		disableRenderEffects(context);
	}

	/** Utils update: NameProtect, AutoAccept, Notifications, FakePlayer, UseTracker, ItemScroller. */
	private static void testUtilsUpdate(ClientGameTestContext context, TestSingleplayerContext world) {
		disableRenderEffects(context);
		world.getServer().runCommand("time set day");
		String own = context.computeOnClient(mc -> mc.getUser().getName());

		// NameProtect: our nick is replaced in drawn text (but not in raw-marked text).
		context.runOnClient(mc -> ModuleManager.get().find(NameProtect.class).setEnabled(true));
		String replaced = context.computeOnClient(mc -> NameProtect.protect("Hello " + own + "!", Style.EMPTY));
		assertTrue(replaced.equals("Hello Elysium!"), "NameProtect should replace the nick, got " + replaced);
		String raw = context.computeOnClient(mc -> NameProtect.protect(own, Style.EMPTY.withInsertion(NameProtect.RAW)));
		assertTrue(raw.equals(own), "Raw text must be left alone");
		context.runOnClient(mc -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Привет, " + own + "!")));

		// AutoAccept patterns (RU/EN).
		assertTrue("notch".equals(AutoAccept.requester("Notch просит к вам телепортироваться.")), "RU tpa request");
		assertTrue("steve".equals(AutoAccept.requester("Steve has requested to teleport to you.")), "EN tpa request");
		assertTrue(AutoAccept.requester("Привет всем") == null, "Not a request");
		context.runOnClient(mc -> ModuleManager.get().find(AutoAccept.class).setEnabled(true));
		world.getServer().runCommand("tellraw @a {\"text\":\"Notch просит к вам телепортироваться.\"}");

		// Notifications: toggles, pickups and module messages show as toasts.
		context.runOnClient(mc -> {
			ModuleManager.get().find(NotificationsModule.class).setEnabled(true);
			ModuleManager.get().find(ClientSounds.class).setEnabled(true);
			ModuleManager.get().find(Keystrokes.class).setEnabled(true);
		});
		world.getServer().runCommand("execute at @p run summon minecraft:item ~ ~1 ~ {Item:{id:\"minecraft:emerald\",count:5},PickupDelay:0}");
		context.waitTicks(20);
		context.takeScreenshot("u1-nameprotect-notifications");

		// FakePlayer: spawn via command, hit it, it loses health; kill it and it comes back.
		chat(context, ".fakeplayer spawn");
		context.waitTicks(5);
		FakePlayer fake = context.computeOnClient(mc -> ModuleManager.get().find(FakePlayer.class));
		assertTrue(context.computeOnClient(mc -> fake.isEnabled()), ".fakeplayer spawn should enable FakePlayer");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		context.waitTicks(25);
		float before = context.computeOnClient(mc -> elysium$fake(mc).getHealth());
		context.runOnClient(mc -> {
			Entity f = elysium$fake(mc);
			mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, f.position().add(0, 1, 0));
			mc.gameMode.attack(mc.player, f);
		});
		context.waitTicks(2);
		float after = context.computeOnClient(mc -> elysium$fake(mc).getHealth());
		context.takeScreenshot("u1-fakeplayer-hit");
		assertTrue(after < before, "FakePlayer should lose health: " + before + " -> " + after);
		// Hit until it dies, then it should be back on full health half a second later.
		for (int i = 0; i < 8 && !context.computeOnClient(mc -> elysium$fake(mc).isDeadOrDying()); i++) {
			context.waitTicks(25);
			context.runOnClient(mc -> mc.gameMode.attack(mc.player, elysium$fake(mc)));
		}
		assertTrue(context.computeOnClient(mc -> elysium$fake(mc).isDeadOrDying()), "FakePlayer should die after enough hits");
		context.takeScreenshot("u1-fakeplayer-dead");
		context.waitTicks(15);
		float respawned = context.computeOnClient(mc -> elysium$fake(mc).getHealth());
		assertTrue(respawned == 20f, "FakePlayer should be back at full health, got " + respawned);

		// UseTracker (self): eating a golden apple is reported.
		context.runOnClient(mc -> {
			UseTracker tracker = ModuleManager.get().find(UseTracker.class);
			((BooleanSetting) tracker.settings().get(1)).set(true);
			tracker.setEnabled(true);
		});
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:golden_apple 4");
		context.waitTicks(5);
		context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 45);
		context.waitTicks(5);
		context.takeScreenshot("u1-usetracker");

		// ItemScroller: Shift + drag over the hotbar moves every stack into the main inventory.
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:dirt 16");
		world.getServer().runCommand("item replace entity @p hotbar.1 with minecraft:stone 16");
		world.getServer().runCommand("item replace entity @p hotbar.2 with minecraft:sand 16");
		context.runOnClient(mc -> ModuleManager.get().find(ItemScroller.class).setEnabled(true));
		context.waitTicks(5);
		context.getInput().pressKey(GLFW.GLFW_KEY_E);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		// Hotbar slots 3, 2, 1, 0 on screen: start on the empty slot 3 and sweep left over the three stacks.
		int[][] hotbar = context.computeOnClient(mc -> {
			InventoryScreen inv = (InventoryScreen) mc.gui.screen();
			AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) inv;
			int[][] pos = new int[4][2];
			for (int i = 0; i < 4; i++) {
				var slot = inv.getMenu().slots.get(36 + 3 - i);
				pos[i][0] = acc.elysium$leftPos() + slot.x + 8;
				pos[i][1] = acc.elysium$topPos() + slot.y + 8;
			}
			return pos;
		});
		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		context.getInput().setCursorPos(hotbar[0][0] * scale, hotbar[0][1] * scale);
		context.waitTicks(2);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		for (int i = 1; i < 4; i++) {
			context.getInput().setCursorPos(hotbar[i][0] * scale, hotbar[i][1] * scale);
			context.waitTicks(3);
		}
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		context.waitTicks(5);
		boolean moved = context.computeOnClient(mc -> {
			var inv = mc.player.getInventory();
			int inMain = 0;
			for (int i = 9; i < 36; i++) {
				inMain += inv.getItem(i).getCount();
			}
			return inv.getItem(0).isEmpty() && inv.getItem(1).isEmpty() && inv.getItem(2).isEmpty() && inMain == 48;
		});
		context.takeScreenshot("u1-itemscroller");
		assertTrue(moved, "ItemScroller should move the swept hotbar stacks");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);

		context.runOnClient(mc -> {
			for (Class<? extends Module> c : List.of(NameProtect.class, AutoAccept.class, FakePlayer.class, UseTracker.class,
					ItemScroller.class, Keystrokes.class)) {
				ModuleManager.get().find(c).setEnabled(false);
			}
		});
	}

	/** AutoTool picks the pickaxe for stone and returns; NoFriendDamage cancels hits on friends. */
	private static void testPlayerUpdate(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand("item replace entity @p hotbar.4 with minecraft:diamond_pickaxe");
		world.getServer().runCommand("execute at @p run setblock ~2 ~ ~ minecraft:stone");
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			ModuleManager.get().find(AutoTool.class).setEnabled(true);
			BlockPos feet = mc.player.blockPosition();
			mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(feet.east(2)));
		});
		context.waitTicks(5);
		context.getInput().holdKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
		context.waitTicks(6);
		int during = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		context.getInput().releaseKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
		context.waitTicks(10);
		int after = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		assertTrue(during == 4, "AutoTool should select the pickaxe (slot 4), got " + during);
		assertTrue(after == 0, "AutoTool should return to slot 0, got " + after);
		context.runOnClient(mc -> ModuleManager.get().find(AutoTool.class).setEnabled(false));

		// NoFriendDamage: the fake player carries our own name; make that name a friend.
		String own = context.computeOnClient(mc -> mc.getUser().getName());
		StringListSetting friends = context.computeOnClient(mc -> FriendsModule.list());
		context.runOnClient(mc -> {
			friends.add(own);
			ModuleManager.get().find(NoFriendDamage.class).setEnabled(true);
		});
		chat(context, ".fakeplayer spawn");
		context.waitTicks(25);
		context.runOnClient(mc -> mc.gameMode.attack(mc.player, elysium$fake(mc)));
		context.waitTicks(2);
		float health = context.computeOnClient(mc -> elysium$fake(mc).getHealth());
		assertTrue(health == 20f, "NoFriendDamage should cancel the hit, health " + health);
		context.runOnClient(mc -> {
			friends.remove(own);
			ModuleManager.get().find(NoFriendDamage.class).setEnabled(false);
			ModuleManager.get().find(FakePlayer.class).setEnabled(false);
		});
	}

	/** AspectRatio, ShulkerPreview, FireworkESP, LootBeams, Hands, KillEffect. */
	private static void testRenderUpdate(ClientGameTestContext context, TestSingleplayerContext world) {
		disableRenderEffects(context);
		world.getServer().runCommand("time set day");
		world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(5);

		// AspectRatio: the world projection uses the chosen ratio.
		context.runOnClient(mc -> {
			AspectRatio ar = ModuleManager.get().find(AspectRatio.class);
			((ModeSetting) ar.settings().get(0)).set("4:3");
			ar.setEnabled(true);
		});
		context.waitTicks(3);
		float aspect = context.computeOnClient(mc -> {
			var m = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix;
			return m.m11() / m.m00();
		});
		context.takeScreenshot("r3-aspect-4-3");
		assertTrue(Math.abs(aspect - 4f / 3f) < 0.01f, "AspectRatio 4:3 expected, projection has " + aspect);
		context.runOnClient(mc -> ModuleManager.get().find(AspectRatio.class).setEnabled(false));

		// LootBeams: items of different rarities on the ground.
		context.runOnClient(mc -> ModuleManager.get().find(LootBeams.class).setEnabled(true));
		String[] loot = {"minecraft:dirt", "minecraft:experience_bottle", "minecraft:beacon", "minecraft:enchanted_golden_apple"};
		for (int i = 0; i < loot.length; i++) {
			world.getServer().runCommand("execute at @p run summon minecraft:item ~" + (i - 1.5) + " ~ ~4 {Item:{id:\"" + loot[i] + "\",count:1},PickupDelay:32767}");
		}
		context.waitTicks(30);
		context.takeScreenshot("r3-loot-beams");
		context.runOnClient(mc -> ModuleManager.get().find(LootBeams.class).setEnabled(false));

		// FireworkESP: a rocket going up in front of us.
		context.runOnClient(mc -> {
			ModuleManager.get().find(FireworkESP.class).setEnabled(true);
			mc.player.setXRot(-35);
		});
		world.getServer().runCommand("execute at @p run summon minecraft:firework_rocket ~ ~1 ~6 {LifeTime:60,FireworksItem:{id:\"minecraft:firework_rocket\",count:1,components:{\"minecraft:fireworks\":{flight_duration:2}}}}");
		context.waitTicks(14);
		context.takeScreenshot("r3-firework-esp");
		context.runOnClient(mc -> {
			ModuleManager.get().find(FireworkESP.class).setEnabled(false);
			mc.player.setXRot(0);
		});

		// Hands: every mode with a sword in hand (swinging for the trail).
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		context.waitTicks(20); // let the equip animation finish
		Hands hands = context.computeOnClient(mc -> ModuleManager.get().find(Hands.class));
		for (String mode : List.of("fill", "glass", "plasma", "outline", "halo", "trail", "plasma_trail")) {
			context.runOnClient(mc -> {
				((ModeSetting) hands.settings().get(0)).set(mode);
				hands.setEnabled(true);
				mc.player.swing(InteractionHand.MAIN_HAND);
			});
			context.waitTicks(3);
			context.takeScreenshot("r3-hands-" + mode);
		}
		context.runOnClient(mc -> hands.setEnabled(false));

		// ShulkerPreview: Ctrl over a filled shulker box in the inventory.
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:light_blue_shulker_box[minecraft:container=["
				+ "{slot:0,item:{id:\"minecraft:diamond\",count:12}},{slot:4,item:{id:\"minecraft:golden_apple\",count:3}},"
				+ "{slot:13,item:{id:\"minecraft:ender_pearl\",count:16}},{slot:26,item:{id:\"minecraft:totem_of_undying\",count:1}}]]");
		context.runOnClient(mc -> ModuleManager.get().find(ShulkerPreview.class).setEnabled(true));
		context.waitTicks(5);
		context.getInput().pressKey(GLFW.GLFW_KEY_E);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(5);
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		int[] slot0 = context.computeOnClient(mc -> {
			InventoryScreen inv = (InventoryScreen) mc.gui.screen();
			AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) inv;
			var slot = inv.getMenu().slots.get(36);
			return new int[]{acc.elysium$leftPos() + slot.x + 8, acc.elysium$topPos() + slot.y + 8};
		});
		context.getInput().setCursorPos(slot0[0] * scale, slot0[1] * scale);
		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.waitTicks(4);
		context.takeScreenshot("r3-shulker-preview");
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);
		context.runOnClient(mc -> ModuleManager.get().find(ShulkerPreview.class).setEnabled(false));

		// KillEffect: each mode on a FakePlayer kill.
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:netherite_sword");
		KillEffect kill = context.computeOnClient(mc -> ModuleManager.get().find(KillEffect.class));
		context.runOnClient(mc -> kill.setEnabled(true));
		chat(context, ".fakeplayer spawn");
		for (String mode : List.of("particles", "lightning", "aura", "nova")) {
			context.runOnClient(mc -> ((ModeSetting) kill.settings().get(0)).set(mode));
			context.waitTicks(25);
			// Hit until the kill, then stop right away (it respawns half a second later).
			for (int i = 0; i < 6; i++) {
				context.runOnClient(mc -> {
					Entity f = elysium$fake(mc);
					mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, f.position().add(0, 1, 0));
					mc.gameMode.attack(mc.player, f);
				});
				context.waitTicks(1);
				if (context.computeOnClient(mc -> elysium$fake(mc).isDeadOrDying())) {
					break;
				}
				context.waitTicks(13);
			}
			System.out.println("[ClickGuiGameTest] kill " + mode + " after hits: dead=" + context.computeOnClient(mc -> elysium$fake(mc).isDeadOrDying())
					+ " hitRecorded=" + context.computeOnClient(mc -> TargetTracker.hitWithin(elysium$fake(mc), 2500))
					+ " enabled=" + context.computeOnClient(mc -> kill.isEnabled())
					+ " mode=" + context.computeOnClient(mc -> ((ModeSetting) kill.settings().get(0)).get()));
			context.waitTicks(2);
			System.out.println("[ClickGuiGameTest] kill " + mode + " +2 ticks: particles=" + context.computeOnClient(mc -> kill.particleCount()));
			context.waitTicks(mode.equals("particles") ? 10 : mode.equals("lightning") ? 0 : 4);
			System.out.println("[ClickGuiGameTest] kill " + mode + ": dead=" + context.computeOnClient(mc -> elysium$fake(mc).isDeadOrDying())
					+ " particles=" + context.computeOnClient(mc -> kill.particleCount())
					+ " bolts=" + context.computeOnClient(mc -> {
						int n = 0;
						for (Entity e : mc.level.entitiesForRendering()) {
							if (e instanceof net.minecraft.world.entity.LightningBolt) {
								n++;
							}
						}
						return n;
					}));
			context.takeScreenshot("r3-kill-" + mode);
		}
		context.runOnClient(mc -> {
			kill.setEnabled(false);
			ModuleManager.get().find(FakePlayer.class).setEnabled(false);
		});
	}

	private static LivingEntity elysium$fake(net.minecraft.client.Minecraft mc) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (FakePlayer.isFake(e)) {
				return (LivingEntity) e;
			}
		}
		throw new AssertionError("FakePlayer entity not found");
	}

	/** TargetESP: every mode around a hit zombie, then the fade-out. */
	private static void testRenderStage4(ClientGameTestContext context, TestSingleplayerContext world) {
		disableRenderEffects(context);
		world.getServer().runCommand("time set night");
		world.getServer().runCommand("kill @e[type=minecraft:zombie]");
		world.getServer().runCommand("execute at @p rotated ~ 0 run summon minecraft:zombie ^ ^ ^3 {NoAI:1b,PersistenceRequired:1b,Tags:[\"esp\"]}");
		// The zombie has to survive all the hits below.
		world.getServer().runCommand("effect give @e[tag=esp] minecraft:resistance infinite 255 true");
		context.waitTicks(5);
		TargetESP esp = context.computeOnClient(mc -> ModuleManager.get().find(TargetESP.class));
		context.runOnClient(mc -> {
			esp.setEnabled(true);
			mc.player.setXRot(10);
		});
		for (String mode : List.of("marker", "ghosts", "orbits", "spirals", "crystals", "cubes", "ring", "chain", "magic")) {
			context.runOnClient(mc -> {
				((ModeSetting) esp.settings().get(0)).set(mode);
				// Hit the nearest zombie (the target stays shown for a few seconds after a hit).
				Entity nearest = null;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof Zombie && (nearest == null || e.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) {
						nearest = e;
					}
				}
				if (nearest != null) {
					mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, nearest.position().add(0, 0.6, 0));
					mc.gameMode.attack(mc.player, nearest);
				}
			});
			context.waitTicks(12);
			context.takeScreenshot("s4-esp-" + mode);
		}
		// No more hits: the effect fades out after a few seconds.
		context.waitTicks(100);
		context.takeScreenshot("s4-esp-faded");
		context.runOnClient(mc -> esp.setEnabled(false));
		world.getServer().runCommand("time set day");
	}

	/** Particles module: attack burst, projectile trail, totem shower, idle fireflies, settings card. */
	private static void testParticles(ClientGameTestContext context, TestSingleplayerContext world) {
		int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		Particles particles = context.computeOnClient(mc -> ModuleManager.get().find(Particles.class));
		context.runOnClient(mc -> {
			ModuleManager.get().modules().forEach(m -> {
				if (!m.hudElements().isEmpty()) {
					m.setEnabled(false);
				}
			});
			particles.setEnabled(true);
			((MultiSelectSetting) particles.settings().get(0)).set(Set.of("attack", "throw", "totem", "move", "idle"));
		});
		world.getServer().runCommand("time set night");
		world.getServer().runCommand("summon minecraft:zombie ~3 ~ ~1 {NoAI:1b,PersistenceRequired:1b,Tags:[\"ptest\"]}");
		// Vanilla particles off, so the screenshots show only ours; look at the zombies.
		context.runOnClient(mc -> mc.options.particles().set(ParticleStatus.MINIMAL));
		context.waitTicks(5);
		context.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES,
				mc.player.position().add(4, 0.6, 1)));
		context.waitTicks(40);
		assertTrue(context.computeOnClient(mc -> particles.particleCount()) > 0, "Idle particles should spawn");
		context.takeScreenshot("particles-idle");

		context.runOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Zombie) {
					mc.gameMode.attack(mc.player, e);
					break;
				}
			}
		});
		context.waitTicks(4);
		context.takeScreenshot("particles-attack");

		world.getServer().runCommand("summon minecraft:snowball ~ ~2 ~ {Motion:[0.6d,0.25d,0.2d]}");
		context.waitTicks(8);
		context.takeScreenshot("particles-throw");

		world.getServer().runCommand("item replace entity @e[tag=ptest] weapon.offhand with minecraft:totem_of_undying");
		world.getServer().runCommand("damage @e[tag=ptest,limit=1] 1000 minecraft:generic");
		context.waitTicks(3);
		assertTrue(context.computeOnClient(mc -> particles.activeTotems()) > 0, "Totem pop was not detected");
		context.waitTicks(9);
		context.takeScreenshot("particles-totem");
		// Third person with the Move trail.
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 20);
		context.takeScreenshot("particles-move");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		int afterTotem = context.computeOnClient(mc -> particles.particleCount());
		assertTrue(afterTotem <= ParticleEngine.MAX_PARTICLES, "Particle cap exceeded: " + afterTotem);

		// Settings card: Render tab, scroll to the end and expand Particles (the last card).
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(ClickGuiScreen.class);
		context.waitTicks(10);
		click(context, scale, 31, 116);
		context.waitTicks(5);
		context.getInput().setCursorPos(300 * scale, 150 * scale);
		context.getInput().scroll(-20);
		context.waitTicks(10);
		click(context, scale, 200, 200);
		context.waitTicks(10);
		context.getInput().setCursorPos(300 * scale, 150 * scale);
		context.getInput().scroll(-4);
		context.waitTicks(10);
		context.takeScreenshot("particles-settings");
		context.getInput().scroll(-6);
		context.waitTicks(10);
		context.takeScreenshot("particles-settings-2");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitFor(mc -> mc.gui.screen() == null);

		// Switching the module off clears everything.
		context.runOnClient(mc -> particles.setEnabled(false));
		assertTrue(context.computeOnClient(mc -> particles.particleCount()) == 0, "Disabling should clear particles");
	}

	/** Average duration of the last 60 rendered frames. */
	private static double averageFrameMs(ClientGameTestContext context) {
		long total = 0;
		int frames = 60;
		for (int i = 0; i < frames; i++) {
			context.waitTick();
			total += context.computeOnClient(mc -> mc.getFrameTimeNs());
		}
		return total / (double) frames / 1_000_000.0;
	}

	private static void click(ClientGameTestContext context, int scale, int guiX, int guiY) {
		context.getInput().setCursorPos(guiX * scale + scale / 2.0, guiY * scale + scale / 2.0);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
