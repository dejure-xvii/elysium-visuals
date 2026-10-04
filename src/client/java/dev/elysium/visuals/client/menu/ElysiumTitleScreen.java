package dev.elysium.visuals.client.menu;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.RenderUtil.Face;
import dev.elysium.visuals.client.gui.render.TabIcon;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.MainMenu;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.state.gui.PanoramaRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Elysium main menu, in the launcher's style: logo and brand, a column of
 * glass cards, the versions at the bottom and a background picker in the
 * top-right corner. Extends {@link TitleScreen} so code that looks for the
 * title screen still finds it.
 */
public class ElysiumTitleScreen extends TitleScreen {
	private static final String MODS_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";

	/** Shared so the panorama keeps turning across menu screens. */
	private static float spin;
	private static boolean firstOpen = true;

	private record Item(String label, MenuIcon icon, Runnable action, String hint) {
	}

	private final List<Item> items = new ArrayList<>();
	private final List<SmoothValue> hover = new ArrayList<>();
	private long openedAt;
	/** A photo is behind the menu: cards get a dark base so they stay readable on bright images. */
	private boolean photo;

	// Background transitions: the shown background fades into the theme color, swaps, fades back in.
	private String shownId;
	private String targetId;
	private final SmoothValue veil = new SmoothValue(1f, 9f);

	// Background picker
	private boolean pickerOpen;
	private final SmoothValue picker = new SmoothValue(0f, 13f);
	private final SmoothValue pickerButtonHover = new SmoothValue(0f, 14f);
	private final SmoothValue folderHover = new SmoothValue(0f, 14f);
	private final List<SmoothValue> cardHover = new ArrayList<>();
	private float scroll;
	private final SmoothValue scrollSmooth = new SmoothValue(0f, 16f);

	public ElysiumTitleScreen() {
		super(false);
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		items.clear();
		items.add(new Item("Одиночная игра", MenuIcon.SINGLEPLAYER, () -> mc.gui.setScreen(new SelectWorldScreen(this)), null));
		items.add(new Item("Сетевая игра", MenuIcon.MULTIPLAYER, () -> mc.gui.setScreen(new JoinMultiplayerScreen(this)), null));
		items.add(new Item("Аккаунты", MenuIcon.ACCOUNTS, () -> mc.gui.setScreen(new dev.elysium.visuals.client.alt.AltManagerScreen(this)), mc.getUser().getName()));
		items.add(new Item("Мини-игра", MenuIcon.GAME, () -> mc.gui.setScreen(new dev.elysium.visuals.client.game.Ore2048Screen(this)), "Руда 2048"));
		items.add(new Item("Настройки", MenuIcon.SETTINGS, () -> mc.gui.setScreen(new OptionsScreen(this, mc.options, false)), null));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			items.add(new Item("Моды", MenuIcon.MODS, this::openModMenu, null));
		}
		items.add(new Item("Выход", MenuIcon.QUIT, mc::stop, null));
		while (hover.size() < items.size()) {
			hover.add(new SmoothValue(0f, 14f));
		}
		if (openedAt == 0) {
			openedAt = Util.getMillis();
			MenuBackgrounds.refresh();
			shownId = MenuBackgrounds.selected().id();
			// The very first menu after loading fades in from the theme color; coming back from another screen doesn't.
			veil.set(firstOpen ? 1f : 0f);
			firstOpen = false;
		}
	}

	private void openModMenu() {
		try {
			Screen screen = (Screen) Class.forName(MODS_SCREEN).getConstructor(Screen.class).newInstance(this);
			Minecraft.getInstance().gui.setScreen(screen);
		} catch (ReflectiveOperationException | LinkageError e) {
			ElysiumVisuals.LOGGER.warn("Can't open Mod Menu: {}", e.toString());
		}
	}

	private static MainMenu module() {
		return ModuleManager.get().find(MainMenu.class);
	}

	private static Palette palette() {
		return ThemeManager.get().palette();
	}

	// ---------------------------------------------------------------------
	// Background
	// ---------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		Palette p = palette();
		MainMenu menu = module();
		advanceSpin();

		// Switching: fade out, wait until the new one is loaded, swap, fade in.
		if (targetId != null) {
			MenuBackgrounds.Background target = MenuBackgrounds.byId(targetId);
			boolean ready = target == null || target.kind() == MenuBackgrounds.Kind.GRADIENT || MenuBackgrounds.full(target) != null;
			if (veil.update(1f) > 0.985f && ready) {
				shownId = targetId;
				targetId = null;
				MenuBackgrounds.retainOnly(shownId);
				if (menu != null) {
					menu.setBackground(shownId);
				}
			}
		} else {
			MenuBackgrounds.Background b = shown();
			boolean ready = b.kind() == MenuBackgrounds.Kind.GRADIENT || MenuBackgrounds.full(b) != null;
			veil.update(ready ? 0f : veil.get());
			if (!ready && Util.getMillis() - openedAt > 4000) {
				veil.update(0f); // a broken image: show the gradient fallback instead of a dark screen
			}
		}

		photo = drawBackground(g, width, height, shown());

		// Scrim for legibility: a darker band behind the menu column and at the bottom.
		int dark = ColorUtil.withAlpha(p.bgBottom(), 0x00);
		RenderUtil.horizontalGradient(g, 0, 0, width / 2f, height, dark, ColorUtil.withAlpha(p.bgBottom(), 0x55));
		RenderUtil.horizontalGradient(g, width / 2f, 0, width - width / 2f, height, ColorUtil.withAlpha(p.bgBottom(), 0x55), dark);
		RenderUtil.roundedGradient(g, 0, height - 60, width, 60, 0, dark, ColorUtil.withAlpha(p.bgBottom(), 0x99));

		float v = veil.get();
		if (v > 0.003f) {
			RenderUtil.rect(g, 0, 0, width, height, ColorUtil.mulAlpha(ColorUtil.withAlpha(p.bgBottom(), 0xFF), v));
		}
	}

	private static void advanceSpin() {
		MainMenu menu = module();
		float speed = menu != null ? menu.spinSpeed() : 1f;
		spin = (spin + Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks() * 0.1f * speed) % 360f;
	}

	/** Draws {@code b} full-screen (the gradient while it loads). Returns whether a photo is shown. */
	private static boolean drawBackground(GuiGraphicsExtractor g, int width, int height, MenuBackgrounds.Background b) {
		Palette p = palette();
		MenuBackgrounds.Loaded loaded = b.kind() == MenuBackgrounds.Kind.GRADIENT ? null : MenuBackgrounds.full(b);
		if (b.kind() == MenuBackgrounds.Kind.PANORAMA && loaded != null) {
			Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.panoramaRenderState = new PanoramaRenderState(spin);
			return true;
		}
		if (b.kind() == MenuBackgrounds.Kind.IMAGE && loaded != null && loaded.image() != null) {
			drawCover(g, width, height, loaded.image());
			return true;
		}
		gradient(g, p, 0, 0, width, height, 1f);
		return false;
	}

	/**
	 * The selected menu background with a dark tint, for the Elysium screens
	 * opened from the menu (accounts, mini-game). Returns whether a photo is shown.
	 */
	public static boolean drawBackdrop(GuiGraphicsExtractor g, int width, int height) {
		advanceSpin();
		boolean photo = drawBackground(g, width, height, MenuBackgrounds.selected());
		if (photo) {
			RenderUtil.rect(g, 0, 0, width, height, ColorUtil.withAlpha(palette().bgBottom(), 0x73));
		}
		return photo;
	}

	private MenuBackgrounds.Background shown() {
		MenuBackgrounds.Background b = shownId != null ? MenuBackgrounds.byId(shownId) : null;
		return b != null ? b : MenuBackgrounds.selected();
	}

	/** The launcher's background: deep gradient with slowly drifting glow spots. */
	public static void gradient(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float strength) {
		RenderUtil.roundedGradient(g, x, y, w, h, 0, ColorUtil.withAlpha(p.bgTop(), 0xFF), ColorUtil.withAlpha(p.bgBottom(), 0xFF));
		double t = Util.getMillis() / 1000.0;
		float big = Math.max(w, h);
		float dx1 = (float) Math.sin(t / 13.0 * Math.PI) * w * 0.12f, dy1 = (float) Math.cos(t / 10.5 * Math.PI) * h * 0.12f;
		float dx2 = (float) Math.cos(t / 16.0 * Math.PI) * w * 0.10f, dy2 = (float) Math.sin(t / 13.5 * Math.PI) * h * 0.14f;
		float dx3 = (float) Math.sin(t / 19.0 * Math.PI) * w * 0.09f, dy3 = (float) Math.cos(t / 15.0 * Math.PI) * h * 0.09f;
		RenderUtil.blob(g, x + w * 0.15f + dx1, y + h * 0.1f + dy1, big * 0.42f, p.blob1(), 0.42f * strength);
		RenderUtil.blob(g, x + w * 0.88f + dx2, y + h * 0.35f + dy2, big * 0.36f, p.blob2(), 0.30f * strength);
		RenderUtil.blob(g, x + w * 0.5f + dx3, y + h * 1.0f + dy3, big * 0.45f, p.blob3(), 0.30f * strength);
	}

	/** Full-screen image, cropped to cover, slowly drifting. */
	private static void drawCover(GuiGraphicsExtractor g, int width, int height, DynamicTexture tex) {
		int iw = tex.getTexture().getWidth(0), ih = tex.getTexture().getHeight(0);
		float zoom = 1.06f;
		float s = Math.max(width / (float) iw, height / (float) ih) * zoom;
		float vw = width / s / iw, vh = height / s / ih; // visible part in UV
		double t = Util.getMillis() / 1000.0;
		float u0 = (1 - vw) / 2f + (float) Math.sin(t / 40.0 * Math.PI) * (1 - vw) / 2f * 0.9f;
		float v0 = (1 - vh) / 2f + (float) Math.cos(t / 52.0 * Math.PI) * (1 - vh) / 2f * 0.9f;
		g.blit(tex.getTextureView(), MenuBackgrounds.linear(), 0, 0, width, height, u0, u0 + vw, v0, v0 + vh);
	}

	// ---------------------------------------------------------------------
	// Menu
	// ---------------------------------------------------------------------

	private boolean compact() {
		return height < 300;
	}

	private int buttonWidth() {
		return 200;
	}

	private int buttonHeight() {
		return compact() ? 20 : 23;
	}

	private int gap() {
		return compact() ? 4 : 5;
	}

	/** Top of the first button. */
	private int buttonsTop() {
		int header = compact() ? 30 : 84;
		int total = header + items.size() * (buttonHeight() + gap()) - gap();
		return Math.max(compact() ? 44 : 20, (height - total) / 2 - 6) + header;
	}

	private int buttonsLeft() {
		return (width - buttonWidth()) / 2;
	}

	private float elapsed() {
		return (Util.getMillis() - openedAt) / 1000f;
	}

	private static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Palette p = palette();
		float t = elapsed();
		float pick = picker.update(pickerOpen ? 1f : 0f);
		boolean overPicker = pick > 0.5f && insidePicker(mouseX, mouseY);
		try {
			// Header: logo and brand.
			float headerIn = ease(t / 0.55f);
			RenderUtil.withAlpha(headerIn, () -> header(g, p, (1f - headerIn) * -6f));

			// Buttons, one after another.
			int x = buttonsLeft(), y = buttonsTop(), bw = buttonWidth(), bh = buttonHeight();
			for (int i = 0; i < items.size(); i++) {
				Item item = items.get(i);
				float in = ease((t - 0.08f - i * 0.045f) / 0.45f);
				int by = y + i * (bh + gap());
				boolean over = !overPicker && item.action() != null && mouseX >= x && mouseX < x + bw && mouseY >= by && mouseY < by + bh;
				float h = hover.get(i).update(over ? 1f : 0f);
				float slide = (1f - in) * 8f;
				int index = i;
				RenderUtil.withAlpha(in, () -> button(g, p, item, x, by + slide, bw, bh, h, index));
			}

			// Versions.
			float footIn = ease((t - 0.3f) / 0.6f);
			RenderUtil.withAlpha(footIn, () -> footer(g, p));

			// Background picker.
			RenderUtil.withAlpha(ease((t - 0.2f) / 0.5f), () -> pickerButton(g, p, mouseX, mouseY));
			if (pick > 0.004f) {
				RenderUtil.withAlpha(pick, () -> pickerPanel(g, p, mouseX, mouseY, pick));
			}
		} finally {
			RenderUtil.setAlpha(1f);
		}
	}

	private void header(GuiGraphicsExtractor g, Palette p, float dy) {
		float scale = compact() ? 1.4f : 2f;
		int brandW = Math.round(RenderUtil.brandWidth() * scale);
		g.pose().pushMatrix();
		if (compact()) {
			// Logo and brand side by side.
			float logo = 22;
			float total = logo + 8 + brandW;
			float lx = (width - total) / 2f, ly = buttonsTop() - 30 - logo / 2f - 4 + dy;
			RenderUtil.logo(g, lx, ly - logo / 2f + 6, logo, p, true);
			g.pose().translate(lx + logo + 8, ly + 6 - 9 * scale / 2f + 1);
			g.pose().scale(scale, scale);
			RenderUtil.brand(g, p, 0, 0);
		} else {
			float logo = 40;
			float top = buttonsTop() - 84 + dy;
			RenderUtil.logo(g, (width - logo) / 2f, top, logo, p, true);
			g.pose().translate((width - brandW) / 2f, top + logo + 10);
			g.pose().scale(scale, scale);
			RenderUtil.brand(g, p, 0, 0);
		}
		g.pose().popMatrix();
	}

	private void button(GuiGraphicsExtractor g, Palette p, Item item, float x, float y, int w, int h, float hv, int index) {
		float r = 8;
		if (photo) {
			RenderUtil.softGlow(g, x, y, w, h, r, 0x38000000, 8);
			RenderUtil.roundedRect(g, x, y, w, h, r, ColorUtil.withAlpha(p.bgBottom(), 0xA6));
		}
		if (hv > 0.01f) {
			RenderUtil.softGlow(g, x, y, w, h, r, ColorUtil.mulAlpha(p.accent(), 0.28f * hv), 6);
		}
		RenderUtil.glass(g, x, y, w, h, r, p, hv, hv * 0.55f);
		float shift = hv * 2f;
		boolean enabled = item.action() != null;
		int iconColor = enabled ? ColorUtil.mix(p.textDim(), p.accent2(), hv) : p.textFaint();
		item.icon().draw(g, x + 14 + shift, y + h / 2f, iconColor);
		int ty = Math.round(y + (h - 9) / 2f + 1);
		RenderUtil.text(g, item.label(), Face.BOLD, Math.round(x + 26 + shift), ty,
				enabled ? ColorUtil.mix(p.textDim(), p.text(), 0.6f + 0.4f * hv) : p.textFaint());
		if (item.hint() != null) {
			String hint = RenderUtil.ellipsize(item.hint(), w / 2 - 10, Face.REGULAR);
			RenderUtil.text(g, hint, Face.REGULAR, Math.round(x + w - 10 - RenderUtil.width(hint, Face.REGULAR)), ty, p.textFaint());
		} else {
			RenderUtil.chevron(g, x + w - 12 + hv * 2f, y + h / 2f, 0f, ColorUtil.mulAlpha(p.textFaint(), hv));
		}
	}

	private void footer(GuiGraphicsExtractor g, Palette p) {
		String mod = FabricLoader.getInstance().getModContainer(ElysiumVisuals.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String left = "Elysium Visuals " + mod;
		String right = "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric";
		int y = height - 13;
		RenderUtil.text(g, left, Face.SMALL, 8, y, p.textDim());
		RenderUtil.text(g, right, Face.SMALL, width - 8 - RenderUtil.width(right, Face.SMALL), y, p.textDim());
	}

	// ---------------------------------------------------------------------
	// Background picker
	// ---------------------------------------------------------------------

	private static final int PICKER_BUTTON = 22;

	private int pickerButtonX() {
		return width - 10 - PICKER_BUTTON;
	}

	private int pickerButtonY() {
		return 10;
	}

	private boolean overPickerButton(double mx, double my) {
		return mx >= pickerButtonX() && mx < pickerButtonX() + PICKER_BUTTON && my >= pickerButtonY() && my < pickerButtonY() + PICKER_BUTTON;
	}

	private void pickerButton(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		float hv = pickerButtonHover.update(overPickerButton(mx, my) ? 1f : 0f);
		int x = pickerButtonX(), y = pickerButtonY();
		if (photo) {
			RenderUtil.roundedRect(g, x, y, PICKER_BUTTON, PICKER_BUTTON, 7, ColorUtil.withAlpha(p.bgBottom(), 0xA6));
		}
		RenderUtil.glass(g, x, y, PICKER_BUTTON, PICKER_BUTTON, 7, p, hv, picker.get());
		int c = ColorUtil.mix(p.textDim(), p.text(), Math.max(hv, picker.get()));
		MenuIcon.IMAGE.draw(g, x + PICKER_BUTTON / 2f, y + PICKER_BUTTON / 2f, c);
		if (hv > 0.01f && !pickerOpen) {
			String tip = "Фон меню";
			int tw = RenderUtil.width(tip) + 10;
			RenderUtil.withAlpha(hv, () -> {
				RenderUtil.roundedRect(g, x - tw - 6, y + 4, tw, 15, 5, p.tooltip());
				RenderUtil.roundedOutline(g, x - tw - 6, y + 4, tw, 15, 5, 0, p.border());
				RenderUtil.text(g, tip, Face.REGULAR, x - tw - 1, y + 8, p.isDark() ? p.text() : 0xFFF3F1FF);
			});
		}
	}

	private int panelW() {
		return Math.min(250, width - 40);
	}

	private float panelX() {
		return width - 10 - panelW() + (1f - ease(picker.get())) * 30f;
	}

	private int panelY() {
		return pickerButtonY() + PICKER_BUTTON + 6;
	}

	private int panelH() {
		return height - panelY() - 24;
	}

	private boolean insidePicker(double mx, double my) {
		return mx >= panelX() && mx < panelX() + panelW() && my >= panelY() && my < panelY() + panelH();
	}

	private static final int PAD = 10, CARD_GAP = 8, FOOTER_H = 26, HEAD_H = 28;

	private int cardW() {
		return (panelW() - PAD * 2 - CARD_GAP) / 2;
	}

	private int cardH() {
		return cardW() / 2 + 15;
	}

	private int gridTop() {
		return panelY() + HEAD_H;
	}

	private int gridHeight() {
		return panelH() - HEAD_H - FOOTER_H - 4;
	}

	private float maxScroll() {
		int rows = (MenuBackgrounds.all().size() + 1) / 2;
		return Math.max(0, rows * (cardH() + CARD_GAP) - CARD_GAP - gridHeight());
	}

	private void pickerPanel(GuiGraphicsExtractor g, Palette p, int mx, int my, float open) {
		float x = panelX();
		int y = panelY(), w = panelW(), h = panelH();
		RenderUtil.window(g, x, y, w, h, 12, p);
		RenderUtil.text(g, "Фон меню", Face.BOLD, Math.round(x + PAD), y + 10, p.text());
		String sub = "Выберите фон";
		RenderUtil.caption(g, sub, Math.round(x + w - PAD - RenderUtil.captionWidth(sub)), y + 11, p.textFaint());

		List<MenuBackgrounds.Background> list = MenuBackgrounds.all();
		while (cardHover.size() < list.size()) {
			cardHover.add(new SmoothValue(0f, 14f));
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		float sc = scrollSmooth.update(scroll);
		int cw = cardW(), ch = cardH();
		int top = gridTop(), gh = gridHeight();
		String current = targetId != null ? targetId : shown().id();
		g.enableScissor(Math.round(x + 2), top, Math.round(x + w - 2), top + gh);
		for (int i = 0; i < list.size(); i++) {
			MenuBackgrounds.Background b = list.get(i);
			float cx = x + PAD + (i % 2) * (cw + CARD_GAP);
			float cy = top + (i / 2) * (ch + CARD_GAP) - sc;
			if (cy + ch < top - 2 || cy > top + gh + 2) {
				continue;
			}
			boolean over = my >= top && my < top + gh && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
			float hv = cardHover.get(i).update(over ? 1f : 0f);
			card(g, p, b, cx, cy, cw, ch, hv, b.id().equals(current));
		}
		g.disableScissor();
		if (maxScroll() > 0) {
			// Thin scroll bar.
			float frac = gh / (gh + maxScroll());
			float bh = Math.max(14, gh * frac);
			float by = top + (gh - bh) * (sc / maxScroll());
			RenderUtil.roundedRect(g, x + w - 5, by, 2, bh, 1, ColorUtil.mulAlpha(p.text(), 0.25f));
		}

		// Footer: open the folder for own images.
		int fy = y + h - FOOTER_H + 2;
		String label = "Открыть папку";
		int bw = RenderUtil.width(label, Face.BOLD) + 28;
		float bx = x + PAD;
		boolean overFolder = mx >= bx && mx < bx + bw && my >= fy && my < fy + 17;
		float fh = folderHover.update(overFolder ? 1f : 0f);
		RenderUtil.glass(g, bx, fy, bw, 17, 6, p, fh, 0f);
		MenuIcon.FOLDER.draw(g, bx + 10, fy + 8.5f, ColorUtil.mix(p.textDim(), p.accent2(), fh));
		RenderUtil.text(g, label, Face.BOLD, Math.round(bx + 19), fy + 5, p.text());
		String hint = "PNG / JPG";
		RenderUtil.caption(g, hint, Math.round(x + w - PAD - RenderUtil.captionWidth(hint)), fy + 6, p.textFaint());
	}

	private void card(GuiGraphicsExtractor g, Palette p, MenuBackgrounds.Background b, float x, float y, int w, int h, float hv, boolean selected) {
		RenderUtil.glass(g, x, y, w, h, 7, p, hv, selected ? 1f : 0f);
		int ix = Math.round(x + 3), iy = Math.round(y + 3), iw = w - 6, ih = (w - 6) / 2;
		if (b.kind() == MenuBackgrounds.Kind.GRADIENT) {
			g.enableScissor(ix, iy, ix + iw, iy + ih);
			gradient(g, p, ix, iy, iw, ih, 1f);
			g.disableScissor();
		} else {
			DynamicTexture thumb = MenuBackgrounds.thumb(b);
			if (thumb != null) {
				g.blit(thumb.getTextureView(), MenuBackgrounds.linear(), ix, iy, ix + iw, iy + ih, 0f, 1f, 0f, 1f);
			} else {
				RenderUtil.roundedRect(g, ix, iy, iw, ih, 5, p.inset());
				// Loading dots.
				double t = Util.getMillis() / 1000.0;
				for (int d = 0; d < 3; d++) {
					float a = 0.35f + 0.65f * (float) Math.max(0, Math.sin(t * 5 - d * 0.7));
					RenderUtil.roundedRect(g, ix + iw / 2f - 6 + d * 5, iy + ih / 2f - 1, 2.2f, 2.2f, 1.1f, ColorUtil.mulAlpha(p.textDim(), a));
				}
			}
		}
		// Soften the image corners into the card.
		RenderUtil.roundedOutline(g, ix - 0.5f, iy - 0.5f, iw + 1, ih + 1, 5, 1.5f, ColorUtil.withAlpha(p.bgBottom(), 0x60));
		String label = RenderUtil.ellipsize(b.label(), w - (selected ? 22 : 10), Face.REGULAR);
		RenderUtil.text(g, label, Face.REGULAR, Math.round(x + 5), Math.round(y + h - 12), selected ? p.text() : ColorUtil.mix(p.textDim(), p.text(), hv));
		if (selected) {
			RenderUtil.glowDot(g, x + w - 8, y + h - 7.5f, 4, p.accent());
		}
	}

	private MenuBackgrounds.Background cardAt(double mx, double my) {
		int top = gridTop(), gh = gridHeight();
		if (my < top || my >= top + gh) {
			return null;
		}
		List<MenuBackgrounds.Background> list = MenuBackgrounds.all();
		float x = panelX();
		int cw = cardW(), ch = cardH();
		for (int i = 0; i < list.size(); i++) {
			float cx = x + PAD + (i % 2) * (cw + CARD_GAP);
			float cy = top + (i / 2) * (ch + CARD_GAP) - scrollSmooth.get();
			if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
				return list.get(i);
			}
		}
		return null;
	}

	private void select(MenuBackgrounds.Background b) {
		String current = targetId != null ? targetId : shown().id();
		if (b.id().equals(current)) {
			return;
		}
		if (b.id().equals(shownId)) {
			targetId = null; // changed back before the swap
			return;
		}
		targetId = b.id();
		if (b.kind() != MenuBackgrounds.Kind.GRADIENT) {
			MenuBackgrounds.full(b); // start loading while the old one fades out
		}
	}

	// ---------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (event.button() != 0) {
			return false;
		}
		if (overPickerButton(mx, my)) {
			pickerOpen = !pickerOpen;
			if (pickerOpen) {
				MenuBackgrounds.refresh();
			}
			click();
			return true;
		}
		if (pickerOpen) {
			if (insidePicker(mx, my)) {
				MenuBackgrounds.Background b = cardAt(mx, my);
				if (b != null) {
					select(b);
					click();
					return true;
				}
				int fy = panelY() + panelH() - FOOTER_H + 2;
				float bx = panelX() + PAD;
				int bw = RenderUtil.width("Открыть папку", Face.BOLD) + 28;
				if (mx >= bx && mx < bx + bw && my >= fy && my < fy + 17) {
					click();
					Util.getPlatform().openPath(MenuBackgrounds.folder());
				}
				return true;
			}
			pickerOpen = false;
			return true;
		}
		int x = buttonsLeft(), y = buttonsTop(), bw = buttonWidth(), bh = buttonHeight();
		for (int i = 0; i < items.size(); i++) {
			int by = y + i * (bh + gap());
			if (mx >= x && mx < x + bw && my >= by && my < by + bh && items.get(i).action() != null) {
				click();
				items.get(i).action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (pickerOpen && insidePicker(x, y)) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) scrollY * 24f));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() && pickerOpen) {
			pickerOpen = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
	}

	@Override
	public void added() {
	}

	@Override
	public void removed() {
	}

	// ---------------------------------------------------------------------
	// Icons (10×10, centered)
	// ---------------------------------------------------------------------

	private enum MenuIcon {
		SINGLEPLAYER(() -> TabIcon.PLAYER),
		MULTIPLAYER(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedOutline(g, cx - 5, cy - 5, 10, 10, 5, 1.2f, c);
				RenderUtil.roundedOutline(g, cx - 2.2f, cy - 5, 4.4f, 10, 2.2f, 1.1f, c);
				RenderUtil.rect(g, cx - 4.6f, cy - 0.55f, 9.2f, 1.1f, c);
			}
		},
		ACCOUNTS(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedRect(g, cx - 4.2f, cy - 4.5f, 3.8f, 3.8f, 1.9f, c);
				RenderUtil.roundedRect(g, cx - 6f, cy + 0.2f, 7.4f, 4f, 2f, c);
				RenderUtil.roundedRect(g, cx + 1.4f, cy - 3.6f, 3.2f, 3.2f, 1.6f, ColorUtil.mulAlpha(c, 0.6f));
				RenderUtil.roundedRect(g, cx + 1.8f, cy + 0.6f, 4.2f, 3.6f, 1.8f, ColorUtil.mulAlpha(c, 0.6f));
			}
		},
		GAME(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedOutline(g, cx - 5.5f, cy - 3.5f, 11, 7, 3.5f, 1.2f, c);
				RenderUtil.rect(g, cx - 3.6f, cy - 0.5f, 3, 1, c);
				RenderUtil.rect(g, cx - 2.6f, cy - 1.5f, 1, 3, c);
				RenderUtil.roundedRect(g, cx + 1.6f, cy - 1.4f, 1.4f, 1.4f, 0.7f, c);
				RenderUtil.roundedRect(g, cx + 2.9f, cy + 0.1f, 1.4f, 1.4f, 0.7f, c);
			}
		},
		SETTINGS(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				for (int i = 0; i < 8; i++) {
					g.pose().pushMatrix();
					g.pose().translate(cx, cy);
					g.pose().rotate((float) (i * Math.PI / 4));
					RenderUtil.roundedRect(g, -1.1f, -5.4f, 2.2f, 2.6f, 0.6f, c);
					g.pose().popMatrix();
				}
				RenderUtil.roundedOutline(g, cx - 3.5f, cy - 3.5f, 7f, 7f, 3.5f, 1.8f, c);
			}
		},
		MODS(() -> TabIcon.UTILS),
		QUIT(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedOutline(g, cx - 5, cy - 4.5f, 6, 9, 1.5f, 1.2f, c);
				RenderUtil.rect(g, cx - 1.5f, cy - 0.6f, 6.2f, 1.2f, c);
				RenderUtil.stroke(g, cx + 2.6f, cy - 2.2f, cx + 4.8f, cy, 1.2f, c);
				RenderUtil.stroke(g, cx + 2.6f, cy + 2.2f, cx + 4.8f, cy, 1.2f, c);
			}
		},
		IMAGE(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedOutline(g, cx - 5.5f, cy - 4.5f, 11, 9, 2, 1.2f, c);
				RenderUtil.roundedRect(g, cx + 1.2f, cy - 2.8f, 2.2f, 2.2f, 1.1f, c);
				RenderUtil.stroke(g, cx - 4f, cy + 3f, cx - 1f, cy - 0.5f, 1.2f, c);
				RenderUtil.stroke(g, cx - 1f, cy - 0.5f, cx + 1.5f, cy + 2f, 1.2f, c);
				RenderUtil.stroke(g, cx + 1f, cy + 1.5f, cx + 2.5f, cy + 0.3f, 1.2f, c);
				RenderUtil.stroke(g, cx + 2.5f, cy + 0.3f, cx + 4.2f, cy + 3f, 1.2f, c);
			}
		},
		FOLDER(null) {
			@Override
			void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
				RenderUtil.roundedRect(g, cx - 5, cy - 4, 4.5f, 2.5f, 1, c);
				RenderUtil.roundedOutline(g, cx - 5, cy - 2.6f, 10, 7, 1.5f, 1.1f, c);
			}
		};

		private final Supplier<TabIcon> delegate;

		MenuIcon(Supplier<TabIcon> delegate) {
			this.delegate = delegate;
		}

		void draw(GuiGraphicsExtractor g, float cx, float cy, int c) {
			delegate.get().draw(g, cx, cy, c);
		}
	}
}
