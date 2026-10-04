package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.render.Watermark;

import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.Option;
import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/** The four looks of the Interface HUD; one is applied to all its elements at once. */
public enum InterfaceStyle {
	MINIMAL("minimal", "Минимал", new MinimalSkin()),
	PLATES("plates", "Плашки", new PlatesSkin()),
	PANELS("panels", "Панели", new PanelsSkin()),
	CARDS("cards", "Карточки", new CardsSkin());

	public static final InterfaceStyle DEFAULT = CARDS;

	private final String id;
	private final String label;
	private final Skin skin;

	InterfaceStyle(String id, String label, Skin skin) {
		this.id = id;
		this.label = label;
		this.skin = skin;
	}

	public String id() {
		return id;
	}

	public Skin skin() {
		return skin;
	}

	/** Options for the style setting (ids are saved in configs). */
	public static List<Option> options() {
		return java.util.Arrays.stream(values()).map(s -> option(s.id, s.label)).toList();
	}

	public static InterfaceStyle byId(String id) {
		for (InterfaceStyle s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return DEFAULT;
	}

	/** Style selected in the Interface module. */
	public static Skin current() {
		Watermark m = ModuleManager.get().find(Watermark.class);
		return m == null ? DEFAULT.skin : m.style().skin;
	}
}
