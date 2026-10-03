package dev.elysium.visuals.client.gui;

import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.gui.anim.Animation;
import dev.elysium.visuals.client.gui.anim.Easing;
import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.TabIcon;
import dev.elysium.visuals.client.gui.tab.ModulesTab;
import dev.elysium.visuals.client.gui.tab.Tab;
import dev.elysium.visuals.client.gui.tab.TabRegistry;
import dev.elysium.visuals.client.gui.widget.SearchField;
import dev.elysium.visuals.client.hud.HudManager;
import dev.elysium.visuals.client.input.Keybinds;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The ClickGUI in the style of the Elysium launcher: a dark window with soft
 * glow spots, a narrow icon-only sidebar (logo on top, tabs, HUD editor at the
 * bottom) and the active tab's page on the right with a title, a caption and
 * the module search. Does not pause the game.
 */
public class ClickGuiScreen extends Screen {
	private static final int MAX_W = 460;
	private static final int MAX_H = 290;
	private static final int RADIUS = 12;

	// Sidebar (launcher: 68px wide, 46px items with a 14px radius → halved).
	private static final int SIDEBAR_INSET = 7;
	private static final int SIDEBAR_W = 32;
	private static final int ITEM = 22;
	private static final int ITEM_RADIUS = 7;
	private static final int LOGO = 18;
	private static final int ITEMS_TOP = 36;

	// Page header.
	private static final int CONTENT_GAP = 12;
	private static final int PAD = 12;
	private static final int HEADER_H = 34;
	private static final int SEARCH_H = 16;

	/** Last opened tab, remembered between openings; -1 = the first module tab. */
	private static int lastTab = -1;

	private final List<Tab> tabs = TabRegistry.createTabs();
	private final List<SmoothValue> tabHover = new ArrayList<>();
	private int activeTab;

	private final SearchField search = new SearchField(this::onSearch);
	/** Results page while the search box has text; replaces the active tab. */
	private ModulesTab searchTab;

	private final Animation open = new Animation(0f, 200, Easing.OUT_QUINT);
	private final Animation contentFade = new Animation(1f, 180, Easing.OUT_QUINT);
	private final SmoothValue indicatorY = new SmoothValue(0, 20f);
	private final SmoothValue searchDim = new SmoothValue(0, 20f);
	private boolean closing;

	private final Animation editAnim = new Animation(0f, 200, Easing.OUT_QUINT);
	private final SmoothValue hudButtonHover = new SmoothValue(0, 22f);
	private final SmoothValue doneHover = new SmoothValue(0, 22f);
	private boolean hudEdit;

	private int winX, winY, winW, winH;

	public ClickGuiScreen() {
		super(Component.literal("Elysium Visuals"));
		for (int i = 0; i < tabs.size(); i++) {
			tabHover.add(new SmoothValue(0, 22f));
		}
		int start = lastTab;
		if (start < 0) {
			start = 0;
			for (int i = 0; i < tabs.size(); i++) {
				if (tabs.get(i) instanceof ModulesTab) {
					start = i;
					break;
				}
			}
		}
		activeTab = Math.max(0, Math.min(start, tabs.size() - 1));
		open.animateTo(1f);
	}

	@Override
	protected void init() {
		winW = Math.min(MAX_W, width - 16);
		winH = Math.min(MAX_H, height - 16);
		winX = (width - winW) / 2;
		winY = (height - winH) / 2;
		for (Tab tab : tabs) {
			tab.layout(pageX(), pageY(), contentW(), pageH());
		}
		if (searchTab != null) {
			searchTab.layout(pageX(), pageY(), contentW(), pageH());
		}
		int sw = Math.max(80, Math.min(140, Math.round(contentW() * 0.38f)));
		search.setBounds(contentX() + contentW() - sw, contentY() + 1, sw, SEARCH_H);
		indicatorY.set(itemY(activeTab));
	}

	// --- Layout ---------------------------------------------------------------

	private int sidebarX() {
		return winX + SIDEBAR_INSET;
	}

	private int sidebarY() {
		return winY + SIDEBAR_INSET;
	}

	private int sidebarH() {
		return winH - SIDEBAR_INSET * 2;
	}

	private int contentX() {
		return sidebarX() + SIDEBAR_W + CONTENT_GAP;
	}

	private int contentY() {
		return winY + PAD;
	}

	private int contentW() {
		return winX + winW - PAD - 2 - contentX();
	}

	private int pageX() {
		return contentX();
	}

	private int pageY() {
		return contentY() + HEADER_H;
	}

	private int pageH() {
		return winY + winH - 8 - pageY();
	}

	private int itemX() {
		return sidebarX() + (SIDEBAR_W - ITEM) / 2;
	}

	private int itemStep() {
		// Tighter spacing on short windows so the HUD button at the bottom still fits.
		int available = sidebarH() - ITEMS_TOP - ITEM - 10;
		return Math.max(ITEM + 1, Math.min(ITEM + 5, available / Math.max(1, tabs.size())));
	}

	private int itemY(int index) {
		return sidebarY() + ITEMS_TOP + index * itemStep();
	}

	private int hudButtonY() {
		return sidebarY() + sidebarH() - 5 - ITEM;
	}

	private boolean overItem(double mx, double my, int itemY) {
		return mx >= itemX() && mx < itemX() + ITEM && my >= itemY && my < itemY + ITEM;
	}

	private boolean overHudButton(double mx, double my) {
		return overItem(mx, my, hudButtonY());
	}

	// --- HUD edit bar ---------------------------------------------------------

	// Edit-mode bar ("Готово") at the bottom center, above the hotbar and hearts.
	private static final String EDIT_HINT = "ПКМ — вернуть на место · Shift — без прилипания";
	private static final int DONE_W = 54;
	private static final int BAR_H = 22;

	/** Compact bar (just the button) when the full one can't find a free spot. */
	private boolean barCompact;

	private int barWidth() {
		return barCompact ? DONE_W + 8 : RenderUtil.width(EDIT_HINT) + DONE_W + 26;
	}

	private int barX() {
		return (width - barWidth()) / 2;
	}

	private int barY = -1;

	/** First of a few candidate spots that doesn't cover a HUD element (re-evaluated each frame while editing). */
	private int barY() {
		return barY >= 0 ? barY : height - 70;
	}

	private void placeEditBar() {
		int[] candidates = {height - 70, 4, height / 2 - BAR_H / 2, height / 3, height * 2 / 3, height - 96};
		for (boolean compactBar : new boolean[]{false, true}) {
			barCompact = compactBar;
			for (int y : candidates) {
				if (!HudManager.get().overlapsAny(barX(), y, barWidth(), BAR_H)) {
					barY = y;
					return;
				}
			}
		}
		// Everything is covered: keep the compact bar at the default spot.
		barY = height - 70;
	}

	private boolean overDone(double mx, double my) {
		int dx = barX() + barWidth() - DONE_W - 4;
		return mx >= dx && mx < dx + DONE_W && my >= barY() + 4 && my < barY() + BAR_H - 4;
	}

	private void setHudEdit(boolean edit) {
		hudEdit = edit;
		editAnim.animateTo(edit ? 1f : 0f);
		if (edit) {
			search.setFocused(false);
			if (currentTab() != null) {
				currentTab().focus(null);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Palette p = ThemeManager.get().palette();
		float t = open.get();
		// Gentle dimming with a hint of the theme's background; lighter for glass so the world shows through.
		int tint = ColorUtil.withAlpha(p.isDark() ? ColorUtil.mixRgb(0xFF000000, p.bgBottom(), 0.5f) : 0xFF000000, 0xFF);
		int top = ColorUtil.withAlpha(tint, ColorUtil.alpha(ColorUtil.mix(0x50000000, 0x28000000, p.glass())));
		int bottom = ColorUtil.withAlpha(tint, ColorUtil.alpha(ColorUtil.mix(0x88000000, 0x48000000, p.glass())));
		g.fillGradient(0, 0, width, height, ColorUtil.mulAlpha(top, t), ColorUtil.mulAlpha(bottom, t));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float t = open.get();
		if (closing && open.isDone()) {
			minecraft.execute(() -> {
				if (minecraft.gui.screen() == this) {
					minecraft.gui.setScreen(null);
				}
			});
			return;
		}

		Palette p = ThemeManager.get().palette();
		float e = editAnim.get();

		// HUD editor: elements are drawn here (above the dimming) so they can be dragged.
		HudManager.get().render(g, !closing);
		RenderUtil.setAlpha(t);
		HudManager.get().renderEditorOverlay(g, mouseX, mouseY);
		if (e > 0.01f) {
			// Don't move the bar while something is being dragged; it would jump under the cursor.
			if (!HudManager.get().isDragging()) {
				placeEditBar();
			}
			RenderUtil.setAlpha(t * e);
			renderEditBar(g, p, mouseX, mouseY);
		}
		RenderUtil.setAlpha(1f);

		// In HUD edit mode the window fades out of the way.
		float panelAlpha = t * (1f - e);
		if (panelAlpha < 0.004f) {
			return;
		}
		float scale = currentScale();
		float cx = winX + winW / 2f, cy = winY + winH / 2f;
		int mx = (int) Math.floor(toLocalX(mouseX, scale));
		int my = (int) Math.floor(toLocalY(mouseY, scale));

		RenderUtil.setAlpha(panelAlpha);
		g.pose().pushMatrix();
		// While fully open, no transform at all: text stays on whole pixels.
		if (scale < 0.9999f || t < 0.9999f) {
			g.pose().translate(cx, cy + (1 - t) * 6f);
			g.pose().scale(scale, scale);
			g.pose().translate(-cx, -cy);
		}
		try {
			RenderUtil.window(g, winX, winY, winW, winH, RADIUS, p);
			renderSidebar(g, p, mx, my);
			renderPage(g, p, mx, my, delta);
			renderTooltips(g, p);
		} finally {
			g.pose().popMatrix();
			RenderUtil.setAlpha(1f);
		}
	}

	private void renderEditBar(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY) {
		int bx = barX(), by = barY(), bw = barWidth();
		RenderUtil.panel(g, bx, by, bw, BAR_H, 8, p);
		if (!barCompact) {
			RenderUtil.text(g, EDIT_HINT, RenderUtil.Face.REGULAR, bx + 10, by + 7, p.textDim());
		}
		int dx = bx + bw - DONE_W - 4;
		float h = doneHover.update(overDone(mouseX, mouseY) ? 1f : 0f);
		accentButton(g, p, dx, by + 4, DONE_W, BAR_H - 8, h);
		String done = "Готово";
		RenderUtil.textBold(g, done, dx + (DONE_W - RenderUtil.widthBold(done)) / 2, by + 7, 0xFFFFFFFF);
	}

	/** The launcher's primary button: accent gradient, glow on hover, light inner rim. */
	private static void accentButton(GuiGraphicsExtractor g, Palette p, float x, float y, float w, float h, float hover) {
		RenderUtil.softGlow(g, x, y, w, h, 5, ColorUtil.mulAlpha(p.accent(), 0.3f + 0.25f * hover), 5);
		RenderUtil.roundedCorners(g, x, y, w, h, 5,
				ColorUtil.withAlpha(p.accent2(), 0xFF), p.accent(), p.accent(),
				ColorUtil.withAlpha(ColorUtil.mixRgb(p.accent(), 0xFF1A0A80, 0.25f), 0xFF));
		RenderUtil.fadedLine(g, x + 4, y + RenderUtil.hairline(), w - 8, RenderUtil.hairline(), 0x66FFFFFF);
	}

	private void renderSidebar(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		int sx = sidebarX(), sy = sidebarY(), sh = sidebarH();
		RenderUtil.glass(g, sx, sy, SIDEBAR_W, sh, 10, p, 0f, 0f);

		// Logo "E" on top, as in the launcher.
		RenderUtil.logo(g, sx + (SIDEBAR_W - LOGO) / 2f, sy + 8, LOGO, p, true);

		// Selected item: a rounded accent square that slides between the icons.
		boolean searching = searchTab != null;
		float dim = searchDim.update(searching ? 1f : 0f);
		float iy = indicatorY.update(itemY(activeTab));
		int ix = itemX();
		RenderUtil.withAlpha(1f - 0.65f * dim, () -> {
			RenderUtil.softGlow(g, ix, iy, ITEM, ITEM, ITEM_RADIUS, ColorUtil.mulAlpha(p.accent(), 0.3f), 6);
			RenderUtil.roundedGradient(g, ix, iy, ITEM, ITEM, ITEM_RADIUS, p.selectedTab(),
					ColorUtil.mulAlpha(p.selectedTab(), 0.42f));
			RenderUtil.roundedOutline(g, ix, iy, ITEM, ITEM, ITEM_RADIUS, 0, ColorUtil.mulAlpha(p.accent2(), 0.4f));
			RenderUtil.fadedLine(g, ix + 4, iy + RenderUtil.hairline(), ITEM - 8, RenderUtil.hairline(), ColorUtil.withAlpha(0xFFFFFF, 0x40));
		});

		for (int i = 0; i < tabs.size(); i++) {
			int ty = itemY(i);
			boolean active = i == activeTab && !searching;
			float h = tabHover.get(i).update(overItem(mx, my, ty) ? 1f : 0f);
			if (!active && h > 0.01f) {
				RenderUtil.roundedRect(g, ix, ty, ITEM, ITEM, ITEM_RADIUS, ColorUtil.withAlpha(p.text(), Math.round(0x10 * h)));
			}
			int color = active ? (p.isDark() ? 0xFFFFFFFF : p.text()) : ColorUtil.mix(p.textDim(), p.text(), h);
			tabs.get(i).icon().draw(g, ix + ITEM / 2f, ty + ITEM / 2f, color);
		}

		// HUD editor button at the bottom.
		int by = hudButtonY();
		float h = hudButtonHover.update(overHudButton(mx, my) ? 1f : 0f);
		RenderUtil.fadedLine(g, ix + 2, by - 6, ITEM - 4, RenderUtil.hairline(), p.border());
		if (h > 0.01f) {
			RenderUtil.roundedRect(g, ix, by, ITEM, ITEM, ITEM_RADIUS, ColorUtil.withAlpha(p.text(), Math.round(0x10 * h)));
		}
		TabIcon.HUD.draw(g, ix + ITEM / 2f, by + ITEM / 2f, ColorUtil.mix(p.textDim(), p.accent2(), h));
	}

	/** Tab names next to the hovered sidebar icon; drawn last so they float above the page. */
	private void renderTooltips(GuiGraphicsExtractor g, Palette p) {
		int tx = sidebarX() + SIDEBAR_W + 6;
		for (int i = 0; i < tabs.size(); i++) {
			RenderUtil.tooltip(g, p, tabs.get(i).title(), tx, itemY(i) + ITEM / 2, tabHover.get(i).get());
		}
		RenderUtil.tooltip(g, p, "Редактор HUD", tx, hudButtonY() + ITEM / 2, hudButtonHover.get());
	}

	private void renderPage(GuiGraphicsExtractor g, Palette p, int mx, int my, float delta) {
		Tab tab = currentTab();
		if (tab == null) {
			return;
		}
		// The search box stays put while pages change.
		search.render(g, p, mx, my, delta);

		float f = contentFade.get();
		boolean moving = f < 0.999f;
		g.pose().pushMatrix();
		if (moving) {
			g.pose().translate(0, (1 - f) * 6f);
		}
		try {
			RenderUtil.withAlpha(f, () -> {
				int x = contentX(), y = contentY();
				int titleRoom = search.x() - 10 - x;
				RenderUtil.text(g, RenderUtil.ellipsize(tab.title(), titleRoom, RenderUtil.Face.TITLE), RenderUtil.Face.TITLE, x, y, p.text());
				String caption = tab.subtitle();
				if (!caption.isEmpty()) {
					RenderUtil.caption(g, caption, x, y + 15, p.textDim());
				}
				tab.render(g, p, mx, my, delta);
			});
		} finally {
			g.pose().popMatrix();
		}
	}

	// ---------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------

	/** Converts screen coordinates into the (possibly scaled) window space. */
	private double toLocalX(double x, float scale) {
		float cx = winX + winW / 2f;
		return cx + (x - cx) / scale;
	}

	private double toLocalY(double y, float scale) {
		float cy = winY + winH / 2f;
		float t = open.get();
		return cy + (y - cy - (1 - t) * 6f) / scale;
	}

	private float currentScale() {
		return (0.96f + 0.04f * open.get()) * (1f - 0.04f * editAnim.get());
	}

	private Tab currentTab() {
		if (searchTab != null) {
			return searchTab;
		}
		return tabs.isEmpty() ? null : tabs.get(activeTab);
	}

	private void onSearch(String query) {
		boolean wasSearching = searchTab != null;
		Tab previous = currentTab();
		if (previous != null) {
			previous.focus(null);
		}
		if (query.isBlank()) {
			searchTab = null;
		} else {
			searchTab = ModulesTab.search(query);
			searchTab.layout(pageX(), pageY(), contentW(), pageH());
		}
		if (wasSearching != (searchTab != null)) {
			contentFade.set(0f);
			contentFade.animateTo(1f);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return false;
		}
		// HUD edit mode: the whole screen belongs to the HUD elements.
		if (hudEdit) {
			if (event.button() == 0 && overDone(event.x(), event.y())) {
				setHudEdit(false);
			} else {
				HudManager.get().mousePressed(event.x(), event.y(), event.button());
			}
			return true;
		}
		float s = currentScale();
		double mx = toLocalX(event.x(), s), my = toLocalY(event.y(), s);

		if (search.isHovered(mx, my)) {
			search.mouseClicked(mx, my, event.button());
			search.setFocused(true);
			Tab tab = currentTab();
			if (tab != null) {
				tab.focus(null);
			}
			return true;
		}
		search.setFocused(false);

		if (event.button() == 0 && overHudButton(mx, my)) {
			setHudEdit(true);
			return true;
		}

		// Outside the window: grab a HUD element.
		boolean insideWindow = mx >= winX && mx < winX + winW && my >= winY && my < winY + winH;
		if (!insideWindow) {
			if (HudManager.get().mousePressed(event.x(), event.y(), event.button())) {
				return true;
			}
		}

		for (int i = 0; i < tabs.size(); i++) {
			if (overItem(mx, my, itemY(i))) {
				switchTab(i);
				return true;
			}
		}
		Tab tab = currentTab();
		if (tab != null) {
			if (!tab.mouseClicked(mx, my, event.button())) {
				tab.focus(null);
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (HudManager.get().mouseReleased()) {
			return true;
		}
		Tab tab = currentTab();
		float s = currentScale();
		return tab != null && tab.mouseReleased(toLocalX(event.x(), s), toLocalY(event.y(), s), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (HudManager.get().mouseDragged(event.x(), event.y())) {
			return true;
		}
		Tab tab = currentTab();
		float s = currentScale();
		return tab != null && tab.mouseDragged(toLocalX(event.x(), s), toLocalY(event.y(), s), event.button());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (hudEdit) {
			return false;
		}
		Tab tab = currentTab();
		float s = currentScale();
		return tab != null && tab.mouseScrolled(toLocalX(x, s), toLocalY(y, s), scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Escape / the GUI key leave HUD edit mode first.
		if (hudEdit && (event.isEscape() || Keybinds.OPEN_GUI.matches(event))) {
			setHudEdit(false);
			return true;
		}
		if (hudEdit) {
			return super.keyPressed(event);
		}
		// Ctrl+F jumps into the search box.
		if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown()) {
			Tab tab = currentTab();
			if (tab != null) {
				tab.focus(null);
			}
			search.setFocused(true);
			return true;
		}
		if (search.isFocused()) {
			// Escape clears the query first, then leaves the box.
			if (event.isEscape()) {
				if (!search.text().isEmpty()) {
					search.clear();
				} else {
					search.setFocused(false);
				}
				return true;
			}
			search.keyPressed(event);
			// Everything else is typing: the GUI key must not close the window.
			return true;
		}
		Tab tab = currentTab();
		if (tab != null && tab.hasKeyboardFocus()) {
			// While typing, Escape only leaves the text field.
			if (event.isEscape()) {
				tab.focus(null);
				return true;
			}
			if (tab.keyPressed(event)) {
				return true;
			}
		} else if (Keybinds.OPEN_GUI.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (search.isFocused()) {
			return search.charTyped(event);
		}
		Tab tab = currentTab();
		return tab != null && tab.charTyped(event);
	}

	private void switchTab(int index) {
		boolean searching = searchTab != null;
		if (index == activeTab && !searching) {
			return;
		}
		currentTab().focus(null);
		if (searching) {
			// Picking a tab ends the search (onSearch fades the page in).
			activeTab = index;
			lastTab = index;
			search.setFocused(false);
			search.clear();
			return;
		}
		activeTab = index;
		lastTab = index;
		contentFade.set(0f);
		contentFade.animateTo(1f);
	}

	@Override
	public void onClose() {
		if (closing) {
			return;
		}
		closing = true;
		search.setFocused(false);
		Tab tab = currentTab();
		if (tab != null) {
			tab.focus(null);
		}
		open.animateTo(0f);
		ConfigManager.saveIfDirty();
	}

	@Override
	public void removed() {
		ConfigManager.saveIfDirty();
	}
}
