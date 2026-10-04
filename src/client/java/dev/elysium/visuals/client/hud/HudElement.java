package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;

/**
 * A draggable HUD element owned by a module. Subclasses measure themselves in
 * {@link #measure(boolean)} and draw at ({@link #x}, {@link #y}) in
 * {@link #draw(GuiGraphicsExtractor, Palette, boolean)}. Positioning, fading,
 * dragging and saving are handled by {@link HudManager}.
 */
public abstract class HudElement {
	/** Where the element goes until the user drags it somewhere. */
	public enum Anchor {
		/** Stacked downwards from the top-left corner. */
		TOP_LEFT,
		/** Stacked upwards from the bottom-right corner, clear of the hotbar. */
		BOTTOM_RIGHT,
		/** Next to the crosshair. */
		CROSSHAIR
	}

	private final String id;
	private final String name;
	private final Anchor anchor;
	private Module owner;
	private BooleanSupplier condition = () -> true;
	/** "Масштаб" slider (percent), created by the owning module; null = 100 %. */
	private NumberSetting scaleSetting;

	/** Saved position as a fraction of the free space, or null for the default anchor. */
	Float fx;
	Float fy;

	protected int x;
	protected int y;
	protected int width;
	protected int height;
	/** Fade in/out of the whole element (≈ 200 ms). */
	final SmoothValue visibility = new SmoothValue(0, 14f);
	private final SmoothValue animatedWidth = new SmoothValue(-1, 16f);
	private final SmoothValue animatedHeight = new SmoothValue(-1, 16f);

	protected HudElement(String id, String name, Anchor anchor) {
		this.id = id;
		this.name = name;
		this.anchor = anchor;
	}

	/** Extra condition for showing this element (e.g. a check box in the module settings). */
	public HudElement visibleWhen(BooleanSupplier condition) {
		this.condition = condition;
		return this;
	}

	public void setOwner(Module owner) {
		this.owner = owner;
	}

	/** The extra condition alone (e.g. the element is ticked in the module settings). */
	public boolean conditionMet() {
		return condition.getAsBoolean();
	}

	public void setScaleSetting(NumberSetting setting) {
		this.scaleSetting = setting;
	}

	/** User scale of the element, 0.5..1.5. Drawing happens at 1; HudManager scales it. */
	public float scale() {
		return scaleSetting == null ? 1f : scaleSetting.floatValue() / 100f;
	}

	/** Element id without the owning module ("watermark"). */
	public String localId() {
		return id;
	}

	/**
	 * True if the element stays visible without data (e.g. a block that shrinks
	 * to its header); it still gets sample data in the editor.
	 */
	public boolean showsWhenEmpty() {
		return false;
	}

	/** Enabled by the user: owning module on and the element selected. */
	public boolean isEnabled() {
		return owner != null && owner.isEnabled() && condition.getAsBoolean();
	}

	/** True if this element shows potion effects, so the vanilla effect icons are hidden while it is enabled. */
	public boolean replacesVanillaEffects() {
		return false;
	}

	/**
	 * False for elements with a fixed place (e.g. notifications under the
	 * crosshair): they can't be dragged and are positioned by {@link #placeFixed}.
	 */
	public boolean isDraggable() {
		return true;
	}

	/** Sets {@link #x}/{@link #y} of a non-draggable element; called after {@link #measure}. */
	protected void placeFixed(int screenW, int screenH) {
	}

	/** Eases width changes (rows appearing/disappearing) instead of jumping. */
	protected int animateWidth(int target) {
		if (animatedWidth.get() < 0) {
			animatedWidth.set(target);
		}
		return Math.round(animatedWidth.update(target));
	}

	/** Eases height changes (e.g. switching the Interface style) instead of jumping. */
	protected int animateHeight(int target) {
		if (animatedHeight.get() < 0) {
			animatedHeight.set(target);
		}
		return Math.round(animatedHeight.update(target));
	}

	/** Whether there is real data to show; when false the element is hidden outside the editor. */
	public boolean hasContent() {
		return true;
	}

	/**
	 * Computes {@link #width} and {@link #height}.
	 *
	 * @param preview true in the ClickGUI editor when there is no real data — show a sample instead
	 */
	protected abstract void measure(boolean preview);

	protected abstract void draw(GuiGraphicsExtractor g, Palette p, boolean preview);

	public String id() {
		return owner != null ? owner.id() + "/" + id : id;
	}

	public String name() {
		return name;
	}

	public Anchor anchor() {
		return anchor;
	}

	public boolean hasCustomPosition() {
		return fx != null && fy != null;
	}

	/** Current screen position and size (GUI units, scaled), as laid out in the last frame. */
	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return Math.round(width * scale());
	}

	public int height() {
		return Math.round(height * scale());
	}

	public boolean contains(double mx, double my) {
		return mx >= x - 2 && mx < x + width() + 2 && my >= y - 2 && my < y + height() + 2;
	}
}
