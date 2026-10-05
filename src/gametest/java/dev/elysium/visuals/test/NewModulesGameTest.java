package dev.elysium.visuals.test;

import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.farm.AutoCraft;
import dev.elysium.visuals.client.module.impl.render.CustomCrystal;
import dev.elysium.visuals.client.module.impl.render.CustomPet;
import dev.elysium.visuals.client.module.impl.render.Optimizer;
import dev.elysium.visuals.client.module.impl.utils.ChatHelper;
import dev.elysium.visuals.client.module.impl.utils.DeathCoords;
import dev.elysium.visuals.client.module.impl.utils.LockSlot;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.module.setting.Setting;
import dev.elysium.visuals.client.module.setting.StringSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** The seven new modules in a flat world, with checks and screenshots (build/run/clientGameTest/screenshots/mod-*.png). */
public class NewModulesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doImmediateRespawn true");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 -60 0 0 0");
			context.waitTicks(40);

			pet(context, world);
			crystal(context, world);
			optimizer(context, world);
			chat(context, world);
			lockSlot(context, world);
			autoCraft(context, world);
			deathCoords(context, world);

			context.runOnClient(mc -> {
				for (Class<? extends Module> c : java.util.List.of(CustomPet.class, CustomCrystal.class, Optimizer.class, ChatHelper.class,
						LockSlot.class, AutoCraft.class, DeathCoords.class)) {
					ModuleManager.get().find(c).setEnabled(false);
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	// ---------------------------------------------------------------------

	private static void pet(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> {
			CustomPet pet = ModuleManager.get().find(CustomPet.class);
			mode(pet, "kind").set("dachshund");
			((BooleanSetting) setting(pet, "propeller")).set(true);
			((StringSetting) setting(pet, "name")).set("Бублик");
			pet.setEnabled(true);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.player.setYRot(180);
			mc.player.setXRot(25);
		});
		context.waitTicks(20);
		context.runOnClient(mc -> {
			Vec3 p = pet().petPosition();
			check(p != null && p.distanceTo(mc.player.position()) < 4, "pet spawned next to the player: " + p);
		});
		// Walk away: it follows (runs), then sits and finally lies down.
		world.getServer().runCommand("tp @a 0 -60 8 180 25");
		context.waitTicks(6);
		context.takeScreenshot("mod-pet-running");
		context.waitTicks(40);
		context.runOnClient(mc -> {
			Vec3 p = pet().petPosition();
			check(p.distanceTo(mc.player.position()) < 4, "pet followed: " + p.distanceTo(mc.player.position()));
		});
		context.waitTicks(30);
		context.runOnClient(mc -> check(pet().sitting() > 0.8f, "pet sits when you stand: " + pet().sitting()));
		lookAtPet(context);
		context.takeScreenshot("mod-pet-dachshund-sit");
		// Far away: it teleports.
		world.getServer().runCommand("tp @a 60 -60 60 180 25");
		context.waitTicks(10);
		context.runOnClient(mc -> check(pet().petPosition().distanceTo(mc.player.position()) < 4, "pet teleported"));
		// A one-block step: it hops up after you.
		world.getServer().runCommand("fill 58 -60 63 62 -60 70 stone");
		world.getServer().runCommand("tp @a 60 -59 67 0 25");
		context.waitTicks(60);
		context.runOnClient(mc -> check(pet().petPosition().y > -59.5, "pet jumped onto the step: y=" + pet().petPosition().y));
		lookAtPet(context);
		context.takeScreenshot("mod-pet-step");
		context.waitTicks(300);
		context.runOnClient(mc -> check(pet().lying() > 0.8f, "pet lies down after a while: " + pet().lying()));
		lookAtPet(context);
		context.takeScreenshot("mod-pet-lying");
		for (String kind : new String[]{"cat", "fox"}) {
			context.runOnClient(mc -> mode(pet(), "kind").set(kind));
			world.getServer().runCommand("tp @a 60 -60 50 0 25");
			context.waitTicks(50);
			lookAtPet(context);
			context.takeScreenshot("mod-pet-" + kind);
		}
		context.runOnClient(mc -> {
			pet().setEnabled(false);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	/** First person, looking down at the pet from where you stand. */
	private static void lookAtPet(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			Vec3 p = pet().petPosition().add(0, 0.3, 0);
			Vec3 eye = mc.player.getEyePosition();
			double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
			mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
			mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
			mc.player.yRotO = mc.player.getYRot();
			mc.player.xRotO = mc.player.getXRot();
		});
		context.waitTicks(2);
	}

	private static CustomPet pet() {
		return ModuleManager.get().find(CustomPet.class);
	}

	// ---------------------------------------------------------------------

	private static void crystal(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 0 -60 0 0 10");
		server.runCommand("summon end_crystal 0 -60 4 {ShowBottom:0b}");
		server.runCommand("summon end_crystal 4 -60 18 {ShowBottom:0b}");
		server.runCommand("summon end_crystal -6 -60 9 {ShowBottom:0b}");
		// Behind a wall: no damage through it.
		server.runCommand("fill -12 -60 3 -12 -56 7 obsidian");
		server.runCommand("summon end_crystal -14 -60 5 {ShowBottom:0b}");
		context.runOnClient(mc -> ModuleManager.get().find(CustomCrystal.class).setEnabled(true));
		context.waitTicks(20);
		context.runOnClient(mc -> {
			EndCrystal near = crystalAt(mc, 0, 4), far = crystalAt(mc, 4, 18), walled = crystalAt(mc, -14, 5);
			float dn = CustomCrystal.damage(mc.player, near.position()), df = CustomCrystal.damage(mc.player, far.position());
			float dw = CustomCrystal.damage(mc.player, walled.position());
			System.out.printf("[NewModulesTest] crystal damage near=%.1f far=%.1f walled=%.1f%n", dn, df, dw);
			check(dn > 20, "near crystal hurts a lot");
			check(df == 0, "far crystal out of range");
			check(dw < dn * 0.2f, "wall blocks the blast");
			check(CustomCrystal.tint(near) != 0xFF5BE38A, "near crystal is red");
			check(CustomCrystal.tint(far) == 0xFF5BE38A, "far crystal is green");
			mc.gameMode.attack(mc.player, near);
		});
		context.waitTicks(10);
		context.runOnClient(mc -> check(crystalAt(mc, 0, 4) != null && crystalAt(mc, 0, 4).isAlive(), "attack on the dangerous crystal was cancelled"));
		context.takeScreenshot("mod-crystal");
		context.runOnClient(mc -> ((BooleanSetting) setting(ModuleManager.get().find(CustomCrystal.class), "zone")).set(false));
		context.waitTicks(5);
		context.takeScreenshot("mod-crystal-custom-color");
		context.runOnClient(mc -> ((BooleanSetting) setting(ModuleManager.get().find(CustomCrystal.class), "zone")).set(true));
		server.runCommand("kill @e[type=end_crystal]");
		server.runCommand("fill -12 -60 3 -12 -56 7 air");
		server.runCommand("gamemode creative @a");
	}

	private static EndCrystal crystalAt(net.minecraft.client.Minecraft mc, int x, int z) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof EndCrystal c && Math.abs(c.getX() - (x + 0.5)) < 1 && Math.abs(c.getZ() - (z + 0.5)) < 1) {
				return c;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------

	private static void optimizer(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("tp @a 0 -60 0 0 15");
		server.runCommand("fill -4 -60 3 4 -60 9 short_grass");
		server.runCommand("setblock 0 -60 5 poppy");
		server.runCommand("fill -3 -60 14 3 -55 14 stone");
		server.runCommand("summon cow 0 -60 18 {NoAI:1b}");
		context.waitTicks(20);
		context.takeScreenshot("mod-optimizer-off");
		context.runOnClient(mc -> {
			Optimizer o = ModuleManager.get().find(Optimizer.class);
			o.setEnabled(true);
			mode(o, "level").set("ultra");
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			Optimizer o = ModuleManager.get().find(Optimizer.class);
			MultiSelectSetting opts = (MultiSelectSetting) setting(o, "options");
			check(opts.isSelected("grass") && opts.isSelected("weather"), "ultra preset applied");
			check(Optimizer.active("weather") && Optimizer.active("particles"), "options active");
			check(!mc.options.enableVsync().get(), "vsync off");
			BlockPos grass = new BlockPos(1, -60, 4);
			check(Optimizer.hidesBlock(mc.level.getBlockState(grass)), "grass hidden");
			check(!Optimizer.hidesBlock(mc.level.getBlockState(grass.below())), "grass block kept");
			Entity cow = mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(30), e -> e instanceof net.minecraft.world.entity.animal.cow.Cow).getFirst();
			check(Optimizer.cullEntity(cow), "cow behind the wall is culled");
			// Editing the options by hand makes the level "Свой".
			opts.toggle("grass");
		});
		context.waitTicks(3);
		context.runOnClient(mc -> {
			Optimizer o = ModuleManager.get().find(Optimizer.class);
			check(mode(o, "level").is("custom"), "manual change -> custom level");
			((MultiSelectSetting) setting(o, "options")).toggle("grass");
		});
		server.runCommand("weather rain");
		context.waitTicks(40);
		context.takeScreenshot("mod-optimizer-ultra");
		context.runOnClient(mc -> ModuleManager.get().find(Optimizer.class).setEnabled(false));
		context.waitTicks(30);
		context.runOnClient(mc -> check(mc.options.enableVsync().get(), "vsync restored"));
		context.takeScreenshot("mod-optimizer-restored");
		server.runCommand("weather clear");
		server.runCommand("kill @e[type=cow]");
		server.runCommand("fill -4 -60 3 4 -60 9 air");
		server.runCommand("fill -3 -60 14 3 -55 14 air");
	}

	// ---------------------------------------------------------------------

	private static void chat(ClientGameTestContext context, TestSingleplayerContext world) {
		String[] sent = {null};
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> {
			sent[0] = cmd;
			return true;
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			ModuleManager.get().find(ChatHelper.class).setEnabled(true);
			check(ChatHelper.toQwerty("фр").equals("ah"), "фр -> ah");
			check(ChatHelper.toQwerty("ызфцт").equals("spawn"), "ызфцт -> spawn");
			check(ChatHelper.toQwerty("рщьу").equals("home"), "рщьу -> home");
			check(ChatHelper.fixCommand("/фр sell 100").equals("/ah sell 100"), "only the command word");
			check(ChatHelper.fixCommand("/ешьу ыуе").equals("/time ыуе"), "arguments untouched");
			check(ChatHelper.fixCommand(".фр").equals("/ah"), "Russian slash key");
			check(ChatHelper.fixCommand(".рудз").equals(".help"), "our own command kept");
			check(ChatHelper.fixCommand("привет").equals("привет"), "normal message untouched");
			check(ChatHelper.fixCommand("/tp 1 2 3").equals("/tp 1 2 3"), "latin command untouched");
			String nick = mc.getUser().getName();
			var chat = mc.gui.hud.getChat();
			chat.addServerSystemMessage(Component.literal("<Steve> " + nick + ", пойдём в шахту?"));
			chat.addServerSystemMessage(Component.literal("<" + nick + "> это я пишу"));
			chat.addServerSystemMessage(Component.literal("<Alex> просто сообщение"));
			// Russian layout through the real chat path: /цуферук clear -> /weather clear.
			ChatScreen screen = new ChatScreen("", false);
			mc.gui.setScreen(screen);
			screen.handleChatInput("/цуферук clear", false);
		});
		context.waitTicks(20);
		context.runOnClient(mc -> {
			check("weather clear".equals(sent[0]), "/цуферук clear sent as /weather clear: " + sent[0]);
			mc.gui.setScreen(new ChatScreen("", false));
		});
		context.waitTicks(10);
		context.takeScreenshot("mod-chat");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runCommand("time set 6000");
	}

	// ---------------------------------------------------------------------

	private static void lockSlot(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with diamond_sword");
		server.runCommand("item replace entity @a hotbar.1 with stone 16");
		server.runCommand("item replace entity @a inventory.0 with golden_apple 3");
		context.runOnClient(mc -> {
			LockSlot lock = ModuleManager.get().find(LockSlot.class);
			((MultiSelectSetting) setting(lock, "hotbar")).set(Set.of("1"));
			((BooleanSetting) setting(lock, "more")).set(true);
			((MultiSelectSetting) setting(lock, "inventory")).set(Set.of("9"));
			lock.setEnabled(true);
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(0);
			check(!mc.player.drop(true), "Q on a locked slot does nothing");
			mc.player.getInventory().setSelectedSlot(1);
			check(mc.player.drop(false), "Q on a free slot drops");
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			check(mc.player.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "sword kept");
			check(mc.player.getInventory().getItem(1).getCount() == 15, "one stone dropped");
			mc.gui.setScreen(new InventoryScreen(mc.player));
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			var menu = mc.player.containerMenu;
			int swordSlot = slotOf(menu, 0), appleSlot = slotOf(menu, 9);
			mc.gameMode.handleContainerInput(menu.containerId, swordSlot, 1, ContainerInput.THROW, mc.player);
			// Drag out: pick the apples up, click outside the window.
			mc.gameMode.handleContainerInput(menu.containerId, appleSlot, 0, ContainerInput.PICKUP, mc.player);
			mc.gameMode.handleContainerInput(menu.containerId, -999, 0, ContainerInput.PICKUP, mc.player);
			// Put them back.
			mc.gameMode.handleContainerInput(menu.containerId, appleSlot, 0, ContainerInput.PICKUP, mc.player);
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			check(mc.player.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "Ctrl+Q in the inventory blocked");
			check(mc.player.getInventory().getItem(9).getCount() == 3, "drag out of the window blocked: "
					+ mc.player.getInventory().getItem(9));
		});
		context.takeScreenshot("mod-lockslot-inventory");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("mod-lockslot-hotbar");
		context.runOnClient(mc -> ModuleManager.get().find(LockSlot.class).setEnabled(false));
		server.runCommand("kill @e[type=item]");
	}

	private static int slotOf(net.minecraft.world.inventory.AbstractContainerMenu menu, int inventoryIndex) {
		for (var s : menu.slots) {
			if (s.container instanceof net.minecraft.world.entity.player.Inventory && s.getContainerSlot() == inventoryIndex) {
				return s.index;
			}
		}
		throw new AssertionError("no slot " + inventoryIndex);
	}

	// ---------------------------------------------------------------------

	private static void autoCraft(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("clear @a");
		server.runCommand("tp @a 0 -60 0 0 30");
		server.runCommand("setblock 0 -60 2 crafting_table");
		context.waitTicks(20);
		craft(context, world, "eye_of_ender", new String[]{"ender_pearl 16", "blaze_powder 10"}, Items.ENDER_EYE, 10);
		craft(context, world, "snow_block", new String[]{"snowball 9"}, Items.SNOW_BLOCK, 2);
		craft(context, world, "harming_arrow", new String[]{"arrow 16", "lingering_potion[potion_contents={potion:\"minecraft:harming\"}] 1"},
				Items.TIPPED_ARROW, 8);
		server.runCommand("setblock 0 -60 2 air");
		server.runCommand("gamemode creative @a");
	}

	private static void craft(ClientGameTestContext context, TestSingleplayerContext world, String recipe, String[] gives, Item result, int expected) {
		var server = world.getServer();
		server.runCommand("clear @a");
		for (String g : gives) {
			server.runCommand("give @a " + g);
		}
		context.runOnClient(mc -> {
			mc.player.getInventory().setSelectedSlot(8);
			AutoCraft ac = ModuleManager.get().find(AutoCraft.class);
			mode(ac, "recipe").set(recipe);
			((NumberSetting) setting(ac, "delay")).set(1.0);
			ac.setEnabled(true);
			BlockPos table = new BlockPos(0, -60, 2);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
		});
		context.waitFor(mc -> mc.player.containerMenu instanceof CraftingMenu, 100);
		context.waitTicks(20 * 8);
		context.takeScreenshot("mod-autocraft-" + recipe);
		context.runOnClient(mc -> {
			int n = mc.player.getInventory().countItem(result);
			check(n == expected, recipe + ": crafted " + n + " of " + expected);
			mc.player.closeContainer();
			ModuleManager.get().find(AutoCraft.class).setEnabled(false);
		});
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------------

	private static void deathCoords(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("setworldspawn 0 -60 30");
		server.runCommand("tp @a 0 -60 0 0 0");
		context.runOnClient(mc -> ModuleManager.get().find(DeathCoords.class).setEnabled(true));
		context.waitTicks(5);
		server.runCommand("kill @a");
		context.waitTicks(40);
		context.runOnClient(mc -> {
			if (mc.player.isDeadOrDying()) {
				mc.player.respawn();
			}
			mc.gui.setScreen(null);
		});
		context.waitTicks(40);
		server.runCommand("tp @a 0 -60 30 180 -10");
		context.waitTicks(20);
		context.runOnClient(mc -> mc.gui.setScreen(new ChatScreen("", false)));
		context.waitTicks(10);
		context.takeScreenshot("mod-deathcoords");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		// Walking up to the spot removes the pillar.
		server.runCommand("tp @a 0 -60 0");
		context.waitTicks(10);
		server.runCommand("tp @a 0 -60 30 180 -10");
		context.waitTicks(10);
		context.takeScreenshot("mod-deathcoords-reached");
		server.runCommand("gamemode creative @a");
	}

	// ---------------------------------------------------------------------

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("[NewModulesTest] ok: " + what);
	}

	private static Setting<?> setting(Module m, String id) {
		return m.settings().stream().filter(s -> s.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no setting " + id));
	}

	private static ModeSetting mode(Module m, String id) {
		return (ModeSetting) setting(m, id);
	}
}
