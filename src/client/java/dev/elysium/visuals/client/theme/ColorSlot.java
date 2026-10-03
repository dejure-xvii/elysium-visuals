package dev.elysium.visuals.client.theme;

/** The user-editable colors of a theme. */
public enum ColorSlot {
	BACKGROUND("background", "Фон панели"),
	ACCENT("accent", "Акцент"),
	TEXT("text", "Текст"),
	SELECTED_TAB("selectedTab", "Выделенная вкладка");

	private final String key;
	private final String displayName;

	ColorSlot(String key, String displayName) {
		this.key = key;
		this.displayName = displayName;
	}

	/** Key used in the JSON config. */
	public String key() {
		return key;
	}

	public String displayName() {
		return displayName;
	}
}
