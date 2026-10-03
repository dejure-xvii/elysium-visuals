package dev.elysium.visuals.client.module;

import dev.elysium.visuals.client.gui.render.TabIcon;

/** Module categories; each one gets its own ClickGUI tab. */
public enum Category {
	PLAYER("Player", TabIcon.PLAYER),
	RENDER("Render", TabIcon.RENDER),
	UTILS("Utils", TabIcon.UTILS),
	FARM("Farm", TabIcon.FARM);

	private final String displayName;
	private final TabIcon icon;

	Category(String displayName, TabIcon icon) {
		this.displayName = displayName;
		this.icon = icon;
	}

	public String displayName() {
		return displayName;
	}

	public TabIcon icon() {
		return icon;
	}
}
