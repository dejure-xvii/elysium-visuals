package dev.elysium.visuals.client.theme;

import java.util.List;

/** Built-in theme presets. */
public final class Themes {
	public static final String CUSTOM_ID = "custom";

	/** Colors of the Elysium launcher: #0A0912 background, #7C5CFF accent, #F3F1FF text. The default. */
	public static final Theme ELYSIUM = new Theme("elysium", "Elysium",
			0xF20A0912, 0xFF7C5CFF, 0xFFF3F1FF, 0x8C7C5CFF, false);

	public static final Theme DARK = new Theme("dark", "Тёмная",
			0xF2141620, 0xFF7C5CFF, 0xFFE9EAF2, 0xFF262A3A, false);

	public static final Theme LIGHT = new Theme("light", "Светлая",
			0xF5F5F6FA, 0xFF3B7BFF, 0xFF1D2030, 0xFFDDE6FF, false);

	public static final Theme LIQUID_GLASS = new Theme("liquid_glass", "Liquid Glass",
			0x38FFFFFF, 0xFF8FDBFF, 0xFFFFFFFF, 0x40FFFFFF, true);

	public static final List<Theme> PRESETS = List.of(ELYSIUM, DARK, LIGHT, LIQUID_GLASS);

	/** Theme used on first start, after a reset or when the config can't be read. */
	public static final Theme DEFAULT = ELYSIUM;

	private Themes() {
	}

	/** A fresh custom theme, initialised from the default preset. */
	public static Theme newCustom() {
		Theme custom = new Theme(CUSTOM_ID, "Своя", 0, 0, 0, 0, false);
		custom.copyFrom(DEFAULT);
		return custom;
	}
}
