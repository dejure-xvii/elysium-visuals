package dev.elysium.visuals.client.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.RenderUtil.Face;
import dev.elysium.visuals.client.menu.ElysiumTitleScreen;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Руда 2048": the 2048 puzzle with ores on a glass board. Arrows/WASD or a
 * mouse swipe to move, Ctrl+Z / Backspace to undo, P to pause, R to restart.
 * The best score and the unfinished game are saved.
 */
public class Ore2048Screen extends Screen {
	/** Ores by exponent: 1 = 2 (coal) ... 11 = 2048 (Nether Star). */
	private record Ore(String name, Identifier texture, int tint) {
		Ore(String name, String item, int tint) {
			this(name, Identifier.withDefaultNamespace("textures/item/" + item + ".png"), tint);
		}
	}

	private static final Ore[] ORES = {
			null,
			new Ore("Уголь", "coal", 0xFF3A3A40),
			new Ore("Медь", "copper_ingot", 0xFFC8693F),
			new Ore("Железо", "iron_ingot", 0xFFD8D2CC),
			new Ore("Редстоун", "redstone", 0xFFE0352B),
			new Ore("Лазурит", "lapis_lazuli", 0xFF2F57C8),
			new Ore("Золото", "gold_ingot", 0xFFF2C53D),
			new Ore("Изумруд", "emerald", 0xFF2BC46A),
			new Ore("Алмаз", "diamond", 0xFF4FE0DA),
			new Ore("Аметист", "amethyst_shard", 0xFFA66BEA),
			new Ore("Незерит", "netherite_ingot", 0xFF5B4A4F),
			new Ore("Звезда Незера", "nether_star", 0xFFF4F1FF),
			new Ore("Сердце моря", "heart_of_the_sea", 0xFF3FA7D6),
			new Ore("Тотем бессмертия", "totem_of_undying", 0xFFE8C14A),
			new Ore("Эхо-осколок", "echo_shard", 0xFF0F4D5C),
	};

	private static final long SLIDE_MS = 110;
	private static final long POP_MS = 200;

	private final Screen parent;
	private final Ore2048 game = new Ore2048();
	private int best;
	private long moveAt;
	private boolean paused;
	private boolean over;
	private boolean newRecordPlayed;
	private final SmoothValue open = new SmoothValue(0f, 11f);
	private final SmoothValue overlay = new SmoothValue(0f, 10f);
	private final SmoothValue scoreBump = new SmoothValue(0f, 8f);
	private int lastPoints;
	private final List<SmoothValue> buttonHover = new ArrayList<>();
	private double pressX = Double.NaN, pressY;

	/** Sparks flying out of merges. */
	private static final class Spark {
		float x, y, vx, vy, life, max, size;
		int color;
	}

	private final List<Spark> sparks = new ArrayList<>();
	private final Random random = new Random();
	private long lastFrame;

	public Ore2048Screen(Screen parent) {
		super(Component.literal("Руда 2048"));
		this.parent = parent;
		load();
	}

	private static Palette palette() {
		return ThemeManager.get().palette();
	}

	// ---------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------

	private static final int HEADER = 66, PAD = 12;

	private int board() {
		return Math.max(120, Math.min(260, Math.min(width - 40, height - HEADER - PAD * 2 - 16)));
	}

	private int boardX() {
		return (width - board()) / 2;
	}

	/** Top of the window (header + board). */
	private int panelY() {
		return (height - (HEADER + board() + PAD * 2)) / 2;
	}

	private int boardY() {
		return panelY() + PAD + HEADER;
	}

	private float gap() {
		return board() * 0.03f;
	}

	private float cell() {
		return (board() - gap() * 5) / 4f;
	}

	private float cellX(float col) {
		return boardX() + gap() + col * (cell() + gap());
	}

	private float cellY(float row) {
		return boardY() + gap() + row * (cell() + gap());
	}

	// ---------------------------------------------------------------------
	// Game actions
	// ---------------------------------------------------------------------

	private void play(Ore2048.Dir dir) {
		if (paused || over || game.won()) {
			return;
		}
		Ore2048.MoveResult r = game.move(dir);
		if (!r.moved()) {
			return;
		}
		moveAt = Util.getMillis();
		if (r.points() > 0) {
			lastPoints = r.points();
			scoreBump.set(1f);
			int top = 0;
			for (Ore2048.Tile t : r.merges()) {
				top = Math.max(top, t.exp);
				burst(t);
			}
			// Higher ores ring higher.
			float pitch = 0.6f + Math.min(1.4f, top * 0.11f);
			sound(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.9f);
			if (top >= 8) {
				sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + top * 0.05f, 0.6f);
			}
		} else {
			sound(SoundEvents.STONE_HIT, 1.4f, 0.25f);
		}
		if (game.score() > best) {
			best = game.score();
			if (!newRecordPlayed && best > 0 && game.score() > 0 && recordAtStart > 0) {
				newRecordPlayed = true;
				sound(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.5f);
			}
		}
		if (game.won()) {
			sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
		} else if (!game.canMove()) {
			over = true;
			sound(SoundEvents.ANVIL_LAND, 0.8f, 0.35f);
		}
		save();
	}

	private int recordAtStart;

	private void undo() {
		if (paused || !game.undo()) {
			return;
		}
		over = false;
		moveAt = 0;
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.4f);
		save();
	}

	private void restart() {
		game.reset();
		over = false;
		paused = false;
		newRecordPlayed = false;
		recordAtStart = best;
		moveAt = Util.getMillis() - SLIDE_MS;
		sparks.clear();
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.6f);
		save();
	}

	private static void sound(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	private void burst(Ore2048.Tile t) {
		Ore ore = ore(t.exp);
		float cx = cellX(t.col) + cell() / 2f, cy = cellY(t.row) + cell() / 2f;
		int n = 10 + Math.min(14, t.exp * 2);
		for (int i = 0; i < n; i++) {
			Spark s = new Spark();
			double a = random.nextDouble() * Math.PI * 2;
			float speed = cell() * (0.9f + random.nextFloat() * 1.6f);
			s.x = cx;
			s.y = cy;
			s.vx = (float) Math.cos(a) * speed;
			s.vy = (float) Math.sin(a) * speed;
			s.max = s.life = 0.35f + random.nextFloat() * 0.35f;
			s.size = 1.2f + random.nextFloat() * 1.8f;
			s.color = random.nextInt(3) == 0 ? palette().accent2() : ColorUtil.mixRgb(ore.tint(), 0xFFFFFFFF, 0.35f);
			sparks.add(s);
		}
	}

	private static Ore ore(int exp) {
		return ORES[Math.max(1, Math.min(ORES.length - 1, exp))];
	}

	// ---------------------------------------------------------------------
	// Saving (config/elysium-visuals/ore2048.json)
	// ---------------------------------------------------------------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ElysiumVisuals.MOD_ID).resolve("ore2048.json");
	}

	private void load() {
		try {
			Path f = file();
			if (Files.isRegularFile(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				best = o.has("best") ? o.get("best").getAsInt() : 0;
				if (o.has("board")) {
					JsonArray arr = o.getAsJsonArray("board");
					int[] exps = new int[Ore2048.SIZE * Ore2048.SIZE];
					boolean any = false;
					for (int i = 0; i < exps.length && i < arr.size(); i++) {
						exps[i] = Math.max(0, Math.min(ORES.length - 1, arr.get(i).getAsInt()));
						any |= exps[i] > 0;
					}
					if (any) {
						game.restore(exps, o.has("score") ? o.get("score").getAsInt() : 0);
						over = !game.canMove();
					}
				}
			}
		} catch (Exception e) {
			ElysiumVisuals.LOGGER.warn("Can't read the 2048 save: {}", e.toString());
		}
		recordAtStart = best;
		paused = false;
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("best", best);
		o.addProperty("score", game.score());
		JsonArray arr = new JsonArray();
		for (int e : game.exps()) {
			arr.add(e);
		}
		o.add("board", arr);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			ElysiumVisuals.LOGGER.warn("Can't save the 2048 game: {}", e.toString());
		}
	}

	// ---------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (Minecraft.getInstance().level == null) {
			ElysiumTitleScreen.drawBackdrop(g, width, height);
		} else {
			super.extractBackground(g, mouseX, mouseY, partialTick);
		}
	}

	private static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Palette p = palette();
		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float o = open.update(1f);
		boolean showOverlay = paused || over || game.won();
		float ov = overlay.update(showOverlay ? 1f : 0f);
		int mx = showOverlay ? -10000 : mouseX, my = showOverlay ? -10000 : mouseY;
		try {
			RenderUtil.setAlpha(o);
			header(g, p, mx, my);
			boardAndTiles(g, p, now, dt);
			if (ov > 0.004f) {
				RenderUtil.setAlpha(o * ov);
				overlayPanel(g, p, mouseX, mouseY);
			}
		} finally {
			RenderUtil.setAlpha(1f);
		}
	}

	private record Button(String id, String label, float x, float y, float w, float h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Button> buttons = new ArrayList<>();

	private void header(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		buttons.clear();
		int bx = boardX(), bw = board();
		RenderUtil.window(g, bx - PAD, panelY(), bw + PAD * 2, HEADER + bw + PAD * 2, 16, p);
		int top = panelY() + PAD;
		RenderUtil.text(g, "Руда 2048", Face.TITLE, bx, top + 2, p.text());
		Ore next = ore(Math.min(ORES.length - 1, Math.max(game.highest() + 1, 2)));
		String goal = "Следующая руда: " + next.name();
		RenderUtil.text(g, RenderUtil.ellipsize(goal, bw - 140, Face.SMALL), Face.SMALL, bx, top + 20, p.textFaint());

		// Score chips on the right.
		float bump = scoreBump.update(0f);
		int chipW = 58;
		chip(g, p, "Рекорд", String.valueOf(best), bx + bw - chipW, top, chipW, 0f);
		chip(g, p, "Очки", String.valueOf(game.score()), bx + bw - chipW * 2 - 6, top, chipW, bump);
		if (bump > 0.02f && lastPoints > 0) {
			String plus = "+" + lastPoints;
			int px = bx + bw - chipW * 2 - 6 + chipW + 4;
			RenderUtil.withAlpha(bump, () -> RenderUtil.text(g, plus, Face.BOLD, px, Math.round(top + 34 - bump * 4), p.accent2()));
		}

		// Toolbar.
		float y = top + HEADER - 22;
		float x = bx;
		x = toolButton(g, p, "undo", "Отменить", x, y, mx, my, game.canUndo() && !paused) + 6;
		x = toolButton(g, p, "pause", paused ? "Продолжить" : "Пауза", x, y, mx, my, true) + 6;
		toolButton(g, p, "restart", "Заново", x, y, mx, my, true);
		String close = "Закрыть";
		float cw = RenderUtil.width(close, Face.BOLD) + 16;
		toolButton(g, p, "close", close, bx + bw - cw, y, mx, my, true);
	}

	private void chip(GuiGraphicsExtractor g, Palette p, String label, String value, float x, float y, float w, float bump) {
		RenderUtil.well(g, x, y, w, 28, 8, p, 0f);
		if (bump > 0.01f) {
			RenderUtil.roundedRect(g, x, y, w, 28, 8, ColorUtil.mulAlpha(p.accent(), 0.3f * bump));
		}
		RenderUtil.caption(g, label, Math.round(x + (w - RenderUtil.captionWidth(label)) / 2f), Math.round(y + 4), p.textFaint());
		RenderUtil.text(g, value, Face.BOLD, Math.round(x + (w - RenderUtil.width(value, Face.BOLD)) / 2f), Math.round(y + 15), p.text());
	}

	private float toolButton(GuiGraphicsExtractor g, Palette p, String id, String label, float x, float y, int mx, int my, boolean enabled) {
		float w = RenderUtil.width(label, Face.BOLD) + 16, h = 16;
		Button b = new Button(id, label, x, y, w, h);
		int index = buttons.size();
		while (buttonHover.size() <= index) {
			buttonHover.add(new SmoothValue(0f, 14f));
		}
		float hv = buttonHover.get(index).update(enabled && b.contains(mx, my) ? 1f : 0f);
		RenderUtil.withAlpha(enabled ? 1f : 0.45f, () -> {
			RenderUtil.glass(g, x, y, w, h, 6, p, hv, hv * 0.5f);
			RenderUtil.text(g, label, Face.BOLD, Math.round(x + 8), Math.round(y + 4), ColorUtil.mix(p.textDim(), p.text(), hv));
		});
		if (enabled) {
			buttons.add(b);
		} else {
			buttons.add(new Button("", label, 0, 0, 0, 0));
		}
		return x + w;
	}

	private void boardAndTiles(GuiGraphicsExtractor g, Palette p, long now, float dt) {
		int bx = boardX(), by = boardY(), bs = board();
		RenderUtil.roundedRect(g, bx, by, bs, bs, 12, ColorUtil.withAlpha(p.bgBottom(), 0x66));
		float cell = cell(), r = Math.max(4, cell * 0.12f);
		for (int row = 0; row < Ore2048.SIZE; row++) {
			for (int col = 0; col < Ore2048.SIZE; col++) {
				RenderUtil.well(g, cellX(col), cellY(row), cell, cell, r, p, 0f);
			}
		}

		float slide = moveAt == 0 ? 1f : ease((now - moveAt) / (float) SLIDE_MS);
		// Consumed tiles first (they slide under the result), then the rest.
		for (int pass = 0; pass < 2; pass++) {
			for (Ore2048.Tile t : game.tiles()) {
				if (t.consumed != (pass == 0)) {
					continue;
				}
				if (t.consumed && slide >= 1f) {
					continue;
				}
				float row = t.fromRow + (t.row - t.fromRow) * slide;
				float col = t.fromCol + (t.col - t.fromCol) * slide;
				float scale = 1f;
				int exp = t.exp;
				if (t.spawned) {
					float s = (now - moveAt - SLIDE_MS) / 160f;
					if (moveAt == 0) {
						s = 1f;
					}
					if (s <= 0f) {
						continue;
					}
					scale = ease(s);
				} else if (t.merged) {
					float s = (now - moveAt - SLIDE_MS) / (float) POP_MS;
					if (s < 0f) {
						exp = t.exp - 1; // still the two halves sliding together
					} else if (s < 1f) {
						scale = 1f + 0.16f * (float) Math.sin(s * Math.PI);
					}
				}
				tile(g, p, exp, cellX(col), cellY(row), cell, r, scale);
			}
		}

		// Sparks.
		for (int i = sparks.size() - 1; i >= 0; i--) {
			Spark s = sparks.get(i);
			s.life -= dt;
			if (s.life <= 0) {
				sparks.remove(i);
				continue;
			}
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			s.vx *= (float) Math.exp(-4 * dt);
			s.vy = s.vy * (float) Math.exp(-4 * dt) + cell * 1.2f * dt;
			float a = s.life / s.max;
			float size = s.size * (0.5f + 0.5f * a);
			RenderUtil.softGlow(g, s.x - size / 2, s.y - size / 2, size, size, size / 2, ColorUtil.mulAlpha(s.color, 0.5f * a), 3);
			RenderUtil.roundedRect(g, s.x - size / 2, s.y - size / 2, size, size, size / 2, ColorUtil.mulAlpha(s.color, a));
		}
	}

	private void tile(GuiGraphicsExtractor g, Palette p, int exp, float x, float y, float size, float r, float scale) {
		Ore ore = ore(exp);
		float cx = x + size / 2f, cy = y + size / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		float h = size / 2f;
		int tint = ore.tint();
		boolean dark = ColorUtil.luminance(tint) < 0.25f;
		if (exp >= 8) {
			RenderUtil.softGlow(g, -h, -h, size, size, r, ColorUtil.mulAlpha(tint, 0.35f + 0.04f * (exp - 8)), Math.round(size * 0.18f));
		}
		RenderUtil.roundedGradient(g, -h, -h, size, size, r,
				ColorUtil.withAlpha(ColorUtil.mixRgb(tint, 0xFFFFFFFF, dark ? 0.18f : 0.1f), 0xD8),
				ColorUtil.withAlpha(ColorUtil.mixRgb(tint, p.bgBottom(), 0.45f), 0xE6));
		RenderUtil.roundedOutline(g, -h, -h, size, size, r, 0, ColorUtil.withAlpha(0xFFFFFF, 0x38));
		RenderUtil.fadedLine(g, -h + r, -h + RenderUtil.hairline(), size - 2 * r, RenderUtil.hairline(), 0x80FFFFFF);
		// The ore item, as big as the tile allows in whole multiples of 16.
		float itemScale = Math.max(1f, (float) Math.floor(size * 0.55f / 16f * 2f) / 2f);
		int px = Math.round(16 * itemScale);
		g.blit(RenderPipelines.GUI_TEXTURED, ore.texture(), -px / 2, Math.round(-px / 2f - size * 0.06f), 0f, 0f, px, px, 16, 16, 16, 16,
				ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * RenderUtil.alpha())));
		String value = String.valueOf(1 << exp);
		int vw = RenderUtil.width(value, Face.SMALL);
		RenderUtil.text(g, value, Face.SMALL, Math.round(-vw / 2f), Math.round(h - 11), ColorUtil.withAlpha(0xFFFFFF, 0xE6));
		g.pose().popMatrix();
	}

	private void overlayPanel(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		int bx = boardX(), by = boardY(), bs = board();
		RenderUtil.roundedRect(g, bx, by, bs, bs, 12, ColorUtil.withAlpha(p.bgBottom(), 0xC8));
		String title, sub;
		if (game.won()) {
			title = ore(Ore2048.WIN).name() + "!";
			sub = "Вы собрали 2048. Играть дальше?";
		} else if (over) {
			title = "Ходов нет";
			sub = game.score() >= best && game.score() > 0 ? "Новый рекорд: " + game.score() : "Очки: " + game.score();
		} else {
			title = "Пауза";
			sub = "P или Esc — продолжить";
		}
		float scale = 1.6f;
		int tw = Math.round(RenderUtil.width(title, Face.TITLE) * scale);
		g.pose().pushMatrix();
		g.pose().translate(bx + (bs - tw) / 2f, by + bs / 2f - 34);
		g.pose().scale(scale, scale);
		RenderUtil.text(g, title, Face.TITLE, 0, 0, p.text());
		g.pose().popMatrix();
		RenderUtil.text(g, sub, Face.REGULAR, bx + (bs - RenderUtil.width(sub)) / 2, Math.round(by + bs / 2f - 6), p.textDim());

		buttons.removeIf(b -> b.id().equals("o1") || b.id().equals("o2"));
		String a = game.won() ? "Дальше" : over ? "Заново" : "Продолжить";
		String b = game.won() || over ? "В меню" : "Заново";
		float aw = RenderUtil.width(a, Face.BOLD) + 24, bw2 = RenderUtil.width(b, Face.BOLD) + 24;
		float x0 = bx + (bs - aw - bw2 - 8) / 2f, y0 = by + bs / 2f + 14;
		overlayButton(g, p, "o1", a, x0, y0, aw, mx, my, true);
		overlayButton(g, p, "o2", b, x0 + aw + 8, y0, bw2, mx, my, false);
	}

	private void overlayButton(GuiGraphicsExtractor g, Palette p, String id, String label, float x, float y, float w, int mx, int my, boolean primary) {
		Button b = new Button(id, label, x, y, w, 20);
		float hv = b.contains(mx, my) ? 1f : 0f;
		if (primary) {
			RenderUtil.softGlow(g, x, y, w, 20, 7, ColorUtil.mulAlpha(p.accent(), 0.3f), 5);
		}
		RenderUtil.glass(g, x, y, w, 20, 7, p, hv, primary ? 0.8f : 0f);
		RenderUtil.text(g, label, Face.BOLD, Math.round(x + (w - RenderUtil.width(label, Face.BOLD)) / 2f), Math.round(y + 6), p.text());
		buttons.add(b);
	}

	// ---------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------

	private void press(String id) {
		switch (id) {
			case "undo" -> undo();
			case "pause" -> {
				paused = !paused;
				sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
			}
			case "restart" -> restart();
			case "close" -> onClose();
			case "o1" -> {
				if (game.won()) {
					game.keepPlaying();
					save();
				} else if (over) {
					restart();
				} else {
					paused = false;
				}
				sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
			}
			case "o2" -> {
				if (game.won() || over) {
					onClose();
				} else {
					restart();
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return false;
		}
		boolean overlayShown = paused || over || game.won();
		for (Button b : buttons) {
			if (!b.id().isEmpty() && b.contains(event.x(), event.y()) && (!overlayShown || b.id().startsWith("o"))) {
				if (!b.id().startsWith("o") && !b.id().equals("pause")) {
					sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
				}
				press(b.id());
				return true;
			}
		}
		pressX = event.x();
		pressY = event.y();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!Double.isNaN(pressX)) {
			double dx = event.x() - pressX, dy = event.y() - pressY;
			pressX = Double.NaN;
			if (Math.max(Math.abs(dx), Math.abs(dy)) >= 18) {
				play(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? Ore2048.Dir.RIGHT : Ore2048.Dir.LEFT)
						: (dy > 0 ? Ore2048.Dir.DOWN : Ore2048.Dir.UP));
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (event.isEscape()) {
			if (paused) {
				paused = false;
			} else {
				onClose();
			}
			return true;
		}
		if ((key == GLFW.GLFW_KEY_Z && event.hasControlDown()) || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_U) {
			undo();
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> play(Ore2048.Dir.UP);
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> play(Ore2048.Dir.DOWN);
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> play(Ore2048.Dir.LEFT);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> play(Ore2048.Dir.RIGHT);
			case GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_SPACE -> press("pause");
			case GLFW.GLFW_KEY_R -> restart();
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public void onClose() {
		save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
