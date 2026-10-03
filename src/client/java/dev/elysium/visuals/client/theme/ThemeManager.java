package dev.elysium.visuals.client.theme;

import dev.elysium.visuals.client.gui.anim.Animation;
import dev.elysium.visuals.client.gui.anim.Easing;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the active theme and the user's custom theme. The GUI reads colors
 * through {@link #palette()}, so a change is visible on the very next frame.
 */
public final class ThemeManager {
	private static final ThemeManager INSTANCE = new ThemeManager();

	private final Theme custom = Themes.newCustom();
	private Theme active = Themes.DEFAULT;
	private boolean dirty;

	// Smooth cross-fade when switching between themes.
	private Palette fromPalette = Palette.of(Themes.DEFAULT);
	private final Animation transition = new Animation(1f, 240, Easing.OUT_QUINT);

	private ThemeManager() {
	}

	public static ThemeManager get() {
		return INSTANCE;
	}

	/** All themes shown in the list: presets followed by the custom theme. */
	public List<Theme> allThemes() {
		List<Theme> list = new ArrayList<>(Themes.PRESETS);
		list.add(custom);
		return list;
	}

	public Theme active() {
		return active;
	}

	public Theme custom() {
		return custom;
	}

	public boolean isCustomActive() {
		return active == custom;
	}

	public Theme byId(String id) {
		for (Theme t : allThemes()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** Switches the GUI to {@code theme} with a short color cross-fade. */
	public void select(Theme theme) {
		if (theme == active) {
			return;
		}
		fromPalette = palette();
		active = theme;
		transition.set(0f);
		transition.animateTo(1f);
		dirty = true;
	}

	/** Edits a custom color; activates the custom theme if needed. Applied instantly. */
	public void setCustomColor(ColorSlot slot, int argb) {
		custom.setColor(slot, argb);
		activateCustomInstantly();
	}

	public void setCustomGlass(boolean glass) {
		custom.setGlass(glass);
		if (isCustomActive()) {
			fromPalette = palette();
			transition.set(0f);
			transition.animateTo(1f);
		} else {
			select(custom);
		}
		dirty = true;
	}

	/** Copies a theme (normally the active preset) into the custom theme and activates it. */
	public void copyToCustom(Theme source) {
		if (source != custom) {
			custom.copyFrom(source);
		}
		select(custom);
		dirty = true;
	}

	/** Sets the active theme without animation; used when loading the config. */
	public void load(String activeId, Theme customValues) {
		if (customValues != null) {
			custom.copyFrom(customValues);
		}
		Theme t = byId(activeId);
		active = t != null ? t : Themes.DEFAULT;
		transition.set(1f);
		dirty = false;
	}

	/** Colors to draw with this frame. */
	public Palette palette() {
		Palette target = Palette.of(active);
		float t = transition.get();
		return t >= 1f ? target : Palette.lerp(fromPalette, target, t);
	}

	public boolean isDirty() {
		return dirty;
	}

	public void clearDirty() {
		dirty = false;
	}

	private void activateCustomInstantly() {
		if (!isCustomActive()) {
			// Fade from the previous theme, but subsequent edits apply instantly.
			select(custom);
		} else if (!transition.isDone()) {
			fromPalette = palette();
		}
		dirty = true;
	}
}
