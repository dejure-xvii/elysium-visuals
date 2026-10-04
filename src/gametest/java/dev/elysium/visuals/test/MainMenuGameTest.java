package dev.elysium.visuals.test;

import dev.elysium.visuals.client.alt.AccountStore;
import dev.elysium.visuals.client.alt.AltAccount;
import dev.elysium.visuals.client.alt.AltManager;
import dev.elysium.visuals.client.alt.AltManagerScreen;
import dev.elysium.visuals.client.game.Ore2048;
import dev.elysium.visuals.client.game.Ore2048Screen;
import dev.elysium.visuals.client.menu.ElysiumTitleScreen;
import dev.elysium.visuals.client.menu.MenuBackgrounds;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.MainMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Screenshots of the Elysium main menu: every background, the picker, several GUI scales, the vanilla fallback. */
public class MainMenuGameTest implements FabricClientGameTest {
	private static void accounts(ClientGameTestContext context) {
		Path file = MenuBackgrounds.folder().getParent().resolve("test-accounts.json");
		System.setProperty("elysium.accountsFile", file.toString());
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		context.runOnClient(mc -> {
			ModuleManager.get().find(MainMenu.class).setEnabled(true);
			ModuleManager.get().find(MainMenu.class).setBackground("mountains");
			mc.gui.setScreen(new ElysiumTitleScreen());
		});
		context.waitTicks(60);
		context.runOnClient(mc -> {
			AltManager.reload();
			check(AltManager.addOffline("Steve_Test") == null, "add offline");
			check(AltManager.addOffline("ab") != null, "short nick rejected");
			check(AltManager.addOffline("Bad Nick!") != null, "bad nick rejected");
			check(AltManager.addOffline("Alex2077") == null, "add second");
			String nick = AltManager.randomNick();
			check(AltAccount.validName(nick), "random nick valid: " + nick);
			check(AltManager.addOffline(nick) == null, "add random");
			check(AccountStore.load().size() == 3, "saved 3 accounts");
			// The launcher's UUID scheme.
			check(AltAccount.offline("Notch").uuid().equals("b50ad385829d3141a2167e7d7539ba7f"), "offline uuid");
			AltAccount steve = AccountStore.load().getFirst();
			AltManager.login(steve).join();
			check(mc.getUser().getName().equals("Steve_Test"), "switched to Steve_Test, got " + mc.getUser().getName());
			check(mc.getUser().getProfileId().equals(steve.profileId()), "uuid switched");
			check(steve.id().equals(AltManager.currentId()), "current marker");
			check(AltManager.rename(steve, "Steve_Renamed") == null, "rename");
			check(mc.getUser().getName().equals("Steve_Renamed"), "rename updates the session");
			check(mc.getGameProfile().name().equals("Steve_Renamed"), "game profile follows");
			mc.gui.setScreen(new AltManagerScreen(mc.gui.screen()));
		});
		context.waitTicks(30);
		context.takeScreenshot("alt-manager");
		context.runOnClient(mc -> {
			mc.options.guiScale().set(4);
			mc.resizeGui();
		});
		context.waitTicks(20);
		context.takeScreenshot("alt-manager-scale4");
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
			mc.gui.setScreen(new ElysiumTitleScreen());
		});
		context.waitTicks(20);
		context.takeScreenshot("menu-with-account");
	}

	private static void ore2048(ClientGameTestContext context) {
		// Rules.
		Ore2048 game = new Ore2048();
		game.restore(new int[]{1, 1, 2, 2, 3, 0, 3, 3, 1, 2, 1, 2, 0, 0, 0, 5}, 0);
		Ore2048.MoveResult r = game.move(Ore2048.Dir.LEFT);
		int[] e = game.exps();
		check(r.moved(), "moved");
		check(e[0] == 2 && e[1] == 3 && e[2] == 0 || e[2] != 0 && e[0] == 2 && e[1] == 3, "row 0 merges to 2,3");
		check(e[4] == 4 && e[5] == 3, "row 1: 3,_,3,3 -> 4,3");
		check(e[8] == 1 && e[9] == 2 && e[10] == 1 && e[11] == 2, "row 2 unchanged");
		check(e[12] == 5, "row 3 slides");
		check(r.points() == 4 + 8 + 16, "points " + r.points());
		check(game.undo() && game.exps()[0] == 1 && game.score() == 0, "undo");

		Path save = MenuBackgrounds.folder().getParent().resolve("ore2048.json");
		try {
			Files.writeString(save, "{\"best\":4096,\"score\":1532,\"board\":[1,2,3,4,8,7,6,5,9,10,11,1,0,2,0,1]}");
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		context.runOnClient(mc -> mc.gui.setScreen(new Ore2048Screen(mc.gui.screen())));
		context.waitTicks(30);
		context.takeScreenshot("ore2048");
		context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
		context.waitTicks(2);
		context.takeScreenshot("ore2048-merge");
		context.waitTicks(20);
		context.takeScreenshot("ore2048-after");
		context.getInput().pressKey(GLFW.GLFW_KEY_P);
		context.waitTicks(15);
		context.takeScreenshot("ore2048-paused");
		context.getInput().pressKey(GLFW.GLFW_KEY_P);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(4);
			mc.resizeGui();
		});
		context.waitTicks(15);
		context.takeScreenshot("ore2048-scale4");
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		try {
			String saved = Files.readString(save);
			check(saved.contains("\"best\":4096"), "best kept: " + saved);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[MainMenuTest] ok: " + what);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		Path custom = MenuBackgrounds.folder().resolve("test-image.png");
		try {
			Files.createDirectories(custom.getParent());
			BufferedImage img = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < 900; y++) {
				for (int x = 0; x < 1600; x++) {
					int r = 40 + x * 180 / 1600, g = 30 + y * 120 / 900, b = 160 - y * 100 / 900;
					img.setRGB(x, y, (r << 16) | (g << 8) | b);
				}
			}
			ImageIO.write(img, "png", custom.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		context.runOnClient(mc -> mc.options.guiScale().set(2));
		context.waitTicks(40);
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ElysiumTitleScreen)) {
				throw new AssertionError("Expected the Elysium menu, got " + mc.gui.screen());
			}
		});

		for (String id : new String[]{"forest", "river", "mountains", "night", "sunset", "gradient", "file:test-image.png"}) {
			context.runOnClient(mc -> {
				ModuleManager.get().find(MainMenu.class).setBackground(id);
				mc.gui.setScreen(new ElysiumTitleScreen());
			});
			context.waitTicks(80);
			context.takeScreenshot("menu-" + id.replace("file:", "custom-").replace(".png", ""));
		}

		// Background picker (button in the top-right corner, GUI scale 2).
		context.getInput().setCursorPos(1920 - 21 * 2, 21 * 2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(40);
		context.takeScreenshot("menu-picker");
		// Pick the river card (second card of the first row).
		context.runOnClient(mc -> {
			int w = mc.gui.screen().width;
			int panelW = Math.min(250, w - 40);
			int cardW = (panelW - 20 - 8) / 2;
			System.out.println("[MainMenuTest] picker panel " + panelW + " card " + cardW);
		});
		context.getInput().setCursorPos((1920 / 2 - 10 - 10 - 56) * 2, (38 + 28 + 20) * 2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(6);
		context.takeScreenshot("menu-transition");
		context.waitTicks(60);
		context.takeScreenshot("menu-after-pick");
		context.runOnClient(mc -> System.out.println("[MainMenuTest] selected " + ModuleManager.get().find(MainMenu.class).background()));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(20);

		// GUI scales.
		context.runOnClient(mc -> ModuleManager.get().find(MainMenu.class).setBackground("sunset"));
		for (int scale : new int[]{1, 3, 4}) {
			context.runOnClient(mc -> {
				mc.options.guiScale().set(scale);
				mc.resizeGui();
				mc.gui.setScreen(new ElysiumTitleScreen());
			});
			context.waitTicks(40);
			context.takeScreenshot("menu-scale" + scale);
		}
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.resizeGui();
			mc.gui.setScreen(new ElysiumTitleScreen());
		});
		context.waitTicks(40);
		context.takeScreenshot("menu-small-window");
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});

		// Vanilla screens behind the menu show the same panorama.
		context.runOnClient(mc -> mc.gui.setScreen(new OptionsScreen(mc.gui.screen(), mc.options, false)));
		context.waitTicks(20);
		context.takeScreenshot("menu-options");

		// MainMenu off: the vanilla title screen comes back.
		context.runOnClient(mc -> {
			ModuleManager.get().find(MainMenu.class).setEnabled(false);
			mc.gui.setScreen(new TitleScreen());
		});
		context.waitTicks(30);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof ElysiumTitleScreen) {
				throw new AssertionError("MainMenu is off but the Elysium menu is shown");
			}
		});
		context.takeScreenshot("menu-vanilla");

		accounts(context);
		ore2048(context);

		context.runOnClient(mc -> {
			MainMenu menu = ModuleManager.get().find(MainMenu.class);
			menu.setEnabled(true);
			menu.setBackground("forest");
			mc.gui.setScreen(new TitleScreen());
		});
		context.waitTicks(20);
		try {
			Files.deleteIfExists(custom);
		} catch (IOException ignored) {
		}
	}
}
