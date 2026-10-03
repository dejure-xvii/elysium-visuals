package dev.elysium.visuals.client.theme;

import java.util.EnumMap;
import java.util.Map;

/**
 * A GUI theme: four base colors plus a style flag. Everything else the GUI
 * draws is derived from these in {@link Palette}.
 */
public final class Theme {
	private final String id;
	private final String name;
	private final Map<ColorSlot, Integer> colors = new EnumMap<>(ColorSlot.class);
	private boolean glass;

	public Theme(String id, String name, int background, int accent, int text, int selectedTab, boolean glass) {
		this.id = id;
		this.name = name;
		colors.put(ColorSlot.BACKGROUND, background);
		colors.put(ColorSlot.ACCENT, accent);
		colors.put(ColorSlot.TEXT, text);
		colors.put(ColorSlot.SELECTED_TAB, selectedTab);
		this.glass = glass;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public int color(ColorSlot slot) {
		return colors.get(slot);
	}

	public void setColor(ColorSlot slot, int argb) {
		colors.put(slot, argb);
	}

	/** Liquid Glass style: translucent gradient panels with a glowing rim. */
	public boolean glass() {
		return glass;
	}

	public void setGlass(boolean glass) {
		this.glass = glass;
	}

	/** Copies colors and style from {@code other}, keeping this theme's id and name. */
	public void copyFrom(Theme other) {
		for (ColorSlot slot : ColorSlot.values()) {
			colors.put(slot, other.color(slot));
		}
		glass = other.glass;
	}
}
