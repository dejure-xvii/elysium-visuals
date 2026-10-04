package dev.elysium.visuals.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.gui.ClickGuiScreen;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws all HUD elements, lays out the ones without a saved position, and
 * handles dragging them while the ClickGUI or the chat is open. Dragged
 * elements snap to the screen edges/center and to each other (hold Shift to
 * move freely); positions are stored relative to the free space, so they stay
 * on screen after a resolution or GUI scale change.
 */
public final class HudManager {
	private static final HudManager INSTANCE = new HudManager();
	private static final int MARGIN = 4;
	private static final int GAP = 4;

	/** How close (in GUI units) an edge has to be to snap. */
	private static final int SNAP = 4;

	private final List<HudElement> elements = new ArrayList<>();
	private HudElement dragging;
	private double dragOffsetX;
	private double dragOffsetY;
	private boolean dirty;
	/** Snap guide lines of the current drag, or null. */
	private Integer guideX;
	private Integer guideY;

	private HudManager() {
	}

	public static HudManager get() {
		return INSTANCE;
	}

	/** Collects elements from all modules; call after modules are registered. */
	public void init() {
		for (Module m : ModuleManager.get().modules()) {
			elements.addAll(m.hudElements());
		}
		TargetTracker.init();
		HudElementRegistry.addLast(ElysiumVisuals.id("hud"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.hud.isHidden() || Panic.isActive()) {
				return;
			}
			// Full-screen module overlays (vignettes, crosshair) go under the elements.
			for (Module m : ModuleManager.get().modules()) {
				if (m.isEnabled()) {
					m.renderOverlay(g, delta.getGameTimeDeltaPartialTick(false));
				}
			}
			// While the ClickGUI is open it draws the editor version itself.
			if (!(mc.gui.screen() instanceof ClickGuiScreen)) {
				render(g, false);
			}
		});
		// Our effects list replaces the vanilla effect icons (top-right) while it is shown.
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (g, delta) -> {
			if (!replacesVanillaEffects()) {
				vanilla.extractRenderState(g, delta);
			}
		});
		// Modules can hide vanilla layers (Crosshair draws its own in renderOverlay; NoRender hides some).
		for (Identifier id : List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.SCOREBOARD, VanillaHudElements.TITLE_AND_SUBTITLE)) {
			HudElementRegistry.replaceElement(id, vanilla -> (g, delta) -> {
				for (Module m : ModuleManager.get().modules()) {
					if (m.isEnabled() && m.hidesVanillaHud(id)) {
						return;
					}
				}
				vanilla.extractRenderState(g, delta);
			});
		}
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof ChatScreen && !Panic.isActive()) {
				hookChatScreen(screen);
			}
		});
	}

	private boolean replacesVanillaEffects() {
		for (HudElement e : elements) {
			if (e.replacesVanillaEffects() && e.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param editing true inside the ClickGUI: every enabled element is shown
	 *                (with sample data if needed) so it can be positioned
	 */
	public void render(GuiGraphicsExtractor g, boolean editing) {
		Palette p = ThemeManager.get().palette();
		int sw = g.guiWidth(), sh = g.guiHeight();
		float leftY = MARGIN;
		float rightY = sh - MARGIN;
		// The top-left stack wraps into another column when it reaches the bottom.
		int leftX = MARGIN;
		int leftColumnW = 0;

		for (HudElement e : elements) {
			boolean content = e.hasContent();
			boolean show = e.isEnabled() && (editing || content || e.showsWhenEmpty());
			float v = e.visibility.update(show ? 1f : 0f);
			if (v < 0.01f) {
				continue;
			}
			boolean preview = editing && !content;
			e.measure(preview);

			if (!e.isDraggable()) {
				e.placeFixed(sw, sh);
			} else if (e != dragging) {
				if (e.hasCustomPosition()) {
					e.x = Math.round(e.fx * Math.max(0, sw - e.width()));
					e.y = Math.round(e.fy * Math.max(0, sh - e.height()));
				} else {
					switch (e.anchor()) {
						case TOP_LEFT -> {
							if (leftY > MARGIN && leftY + e.height() > sh - MARGIN) {
								leftX += leftColumnW + GAP;
								leftY = MARGIN;
								leftColumnW = 0;
							}
							e.x = leftX;
							e.y = Math.round(leftY);
							leftY += (e.height() + GAP) * v;
							leftColumnW = Math.max(leftColumnW, e.width());
						}
						case BOTTOM_RIGHT -> {
							e.x = sw - MARGIN - e.width();
							e.y = Math.round(rightY - e.height());
							// Keep clear of the hotbar, hearts and hunger in the bottom center.
							if (e.x < sw / 2 + 100 && e.y + e.height() > sh - 50) {
								e.y = sh - 50 - e.height();
								rightY = e.y;
							}
							rightY -= (e.height() + GAP) * v;
						}
						case CROSSHAIR -> {
							// Centered under the crosshair, below the notifications and above the hearts/hunger rows.
							e.x = (sw - e.width()) / 2;
							e.y = Math.max(sh / 2 + 16, Math.min(sh / 2 + 70, sh - 52 - e.height()));
						}
					}
				}
				e.x = Math.max(0, Math.min(sw - e.width(), e.x));
				e.y = Math.max(0, Math.min(sh - e.height(), e.y));
			}

			if (e == dragging) {
				continue; // drawn last, on top of everything
			}
			RenderUtil.withAlpha(v, () -> drawScaled(g, p, e, preview));
		}
		if (dragging != null && dragging.visibility.get() >= 0.01f) {
			HudElement d = dragging;
			RenderUtil.withAlpha(d.visibility.get(), () -> drawScaled(g, p, d, editing && !d.hasContent()));
		}
	}

	/** Draws the element at its user scale, growing from its top-left corner. */
	private static void drawScaled(GuiGraphicsExtractor g, Palette p, HudElement e, boolean preview) {
		float s = e.scale();
		if (s == 1f) {
			e.draw(g, p, preview);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(e.x, e.y);
		g.pose().scale(s, s);
		g.pose().translate(-e.x, -e.y);
		e.draw(g, p, preview);
		g.pose().popMatrix();
	}

	/** Topmost visible element under the cursor (the one a click would grab). */
	private HudElement elementAt(double mx, double my) {
		if (dragging != null) {
			return dragging;
		}
		for (int i = elements.size() - 1; i >= 0; i--) {
			HudElement e = elements.get(i);
			if (e.isDraggable() && e.isEnabled() && e.visibility.get() >= 0.5f && e.contains(mx, my)) {
				return e;
			}
		}
		return null;
	}

	/** Whether a screen rectangle overlaps any visible element (used to place the editor bar). */
	public boolean overlapsAny(int x, int y, int w, int h) {
		for (HudElement e : elements) {
			if (e.isEnabled() && e.visibility.get() >= 0.01f
					&& x < e.x + e.width() + 4 && x + w > e.x - 4 && y < e.y + e.height() + 4 && y + h > e.y - 4) {
				return true;
			}
		}
		return false;
	}

	/** Outlines and hints drawn by the ClickGUI on top of the elements. */
	public void renderEditorOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Palette p = ThemeManager.get().palette();
		HudElement hovered = elementAt(mouseX, mouseY);
		for (HudElement e : elements) {
			float v = e.visibility.get();
			if (!e.isEnabled() || !e.isDraggable() || v < 0.01f) {
				continue;
			}
			boolean active = e == hovered;
			int color = active ? p.accent() : ColorUtil.mulAlpha(p.text(), 0.35f);
			RenderUtil.roundedOutline(g, e.x - 2, e.y - 2, e.width() + 4, e.height() + 4, 7, active ? 1f : 0,
					ColorUtil.mulAlpha(color, v));
			if (active) {
				String label = e.name();
				int lw = RenderUtil.width(label) + 10;
				int ly = e.y - 16 >= 0 ? e.y - 16 : e.y + e.height() + 4;
				RenderUtil.roundedRect(g, e.x - 2, ly, lw, 12, 4, p.accent());
				RenderUtil.text(g, null, label, e.x + 3, ly + 3, 0xFFFFFFFF);
			}
		}
		if (hovered != null) {
			g.requestCursor(CursorTypes.RESIZE_ALL);
		}
		renderGuides(g, p);
	}

	/** Hover outline, snap guides and the move cursor while dragging from the chat screen. */
	private void renderChatOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Palette p = ThemeManager.get().palette();
		HudElement hovered = elementAt(mouseX, mouseY);
		if (hovered != null) {
			float v = hovered.visibility.get();
			int color = hovered == dragging ? p.accent() : ColorUtil.mulAlpha(p.accent(), 0.7f);
			RenderUtil.roundedOutline(g, hovered.x - 2, hovered.y - 2, hovered.width() + 4, hovered.height() + 4, 7, 0,
					ColorUtil.mulAlpha(color, v));
			g.requestCursor(CursorTypes.RESIZE_ALL);
		}
		renderGuides(g, p);
	}

	/** Thin accent lines showing what the dragged element snapped to. */
	private void renderGuides(GuiGraphicsExtractor g, Palette p) {
		if (dragging == null) {
			return;
		}
		int color = ColorUtil.mulAlpha(p.accent(), 0.55f);
		float hair = 1f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		if (guideX != null) {
			RenderUtil.rect(g, guideX, 0, hair, g.guiHeight(), color);
		}
		if (guideY != null) {
			RenderUtil.rect(g, 0, guideY, g.guiWidth(), hair, color);
		}
	}

	// --- Dragging (screen coordinates) ------------------------------------

	/** Lets the HUD elements be dragged while the chat is open (the ClickGUI forwards its own input). */
	private void hookChatScreen(Screen screen) {
		ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			if (!Minecraft.getInstance().gui.hud.isHidden()) {
				renderChatOverlay(g, mouseX, mouseY);
			}
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
				Minecraft.getInstance().gui.hud.isHidden() || !mousePressed(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> !mouseDragged(event.x(), event.y()));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !mouseReleased());
		ScreenEvents.remove(screen).register(s -> {
			mouseReleased();
			ConfigManager.saveIfDirty();
		});
	}

	public boolean mousePressed(double mx, double my, int button) {
		HudElement e = elementAt(mx, my);
		if (e == null) {
			return false;
		}
		if (button == 1) {
			e.fx = null;
			e.fy = null;
			dirty = true;
		} else if (button == 0) {
			dragging = e;
			dragOffsetX = mx - e.x;
			dragOffsetY = my - e.y;
		}
		return true;
	}

	public boolean mouseDragged(double mx, double my) {
		if (dragging == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int freeW = Math.max(0, sw - dragging.width()), freeH = Math.max(0, sh - dragging.height());
		int nx = Math.max(0, Math.min(freeW, (int) Math.round(mx - dragOffsetX)));
		int ny = Math.max(0, Math.min(freeH, (int) Math.round(my - dragOffsetY)));
		guideX = null;
		guideY = null;
		// Shift moves freely, without snapping.
		if (!mc.hasShiftDown()) {
			nx = snapAxis(nx, dragging.width(), sw, true);
			ny = snapAxis(ny, dragging.height(), sh, false);
		}
		dragging.x = Math.max(0, Math.min(freeW, nx));
		dragging.y = Math.max(0, Math.min(freeH, ny));
		dragging.fx = freeW > 0 ? dragging.x / (float) freeW : 0f;
		dragging.fy = freeH > 0 ? dragging.y / (float) freeH : 0f;
		dirty = true;
		return true;
	}

	/**
	 * Snaps one axis of the dragged element to the screen margins/center and to
	 * the edges of the other visible elements (aligned or placed next to them).
	 * Records the guide line to draw.
	 */
	private int snapAxis(int pos, int size, int screen, boolean horizontal) {
		int best = pos;
		int bestDist = SNAP + 1;
		Integer guide = null;
		// {target position of the element's start, guide line coordinate}
		List<int[]> targets = new ArrayList<>();
		targets.add(new int[]{MARGIN, MARGIN});
		targets.add(new int[]{screen - MARGIN - size, screen - MARGIN});
		targets.add(new int[]{(screen - size) / 2, screen / 2});
		for (HudElement o : elements) {
			if (o == dragging || !o.isDraggable() || !o.isEnabled() || o.visibility.get() < 0.5f) {
				continue;
			}
			int oStart = horizontal ? o.x : o.y;
			int oEnd = oStart + (horizontal ? o.width() : o.height());
			targets.add(new int[]{oStart, oStart});                // starts aligned
			targets.add(new int[]{oEnd - size, oEnd});             // ends aligned
			targets.add(new int[]{oEnd + GAP, oEnd});              // right after it
			targets.add(new int[]{oStart - GAP - size, oStart});   // right before it
			targets.add(new int[]{(oStart + oEnd - size) / 2, (oStart + oEnd) / 2}); // centers aligned
		}
		for (int[] t : targets) {
			int d = Math.abs(t[0] - pos);
			if (d < bestDist && t[0] >= 0 && t[0] + size <= screen) {
				bestDist = d;
				best = t[0];
				guide = t[1];
			}
		}
		if (horizontal) {
			guideX = guide;
		} else {
			guideY = guide;
		}
		return best;
	}

	public boolean mouseReleased() {
		boolean was = dragging != null;
		dragging = null;
		guideX = null;
		guideY = null;
		return was;
	}

	public boolean isDragging() {
		return dragging != null;
	}

	// --- Config -------------------------------------------------------------

	public boolean isDirty() {
		return dirty;
	}

	public void clearDirty() {
		dirty = false;
	}

	/** Every element back to its default anchor. */
	public void resetPositions() {
		for (HudElement e : elements) {
			e.fx = null;
			e.fy = null;
		}
		dirty = true;
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		for (HudElement e : elements) {
			if (e.isDraggable() && e.hasCustomPosition()) {
				JsonArray pos = new JsonArray();
				pos.add(e.fx);
				pos.add(e.fy);
				root.add(e.id(), pos);
			}
		}
		return root;
	}

	public void fromJson(JsonObject root) {
		for (HudElement e : elements) {
			JsonElement pos = root.get(e.id());
			if (pos != null && pos.isJsonArray() && pos.getAsJsonArray().size() == 2) {
				try {
					e.fx = Math.max(0f, Math.min(1f, pos.getAsJsonArray().get(0).getAsFloat()));
					e.fy = Math.max(0f, Math.min(1f, pos.getAsJsonArray().get(1).getAsFloat()));
				} catch (RuntimeException ex) {
					e.fx = null;
					e.fy = null;
				}
			}
		}
		dirty = false;
	}
}
