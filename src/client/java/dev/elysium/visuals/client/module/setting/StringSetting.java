package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A single line of text, shown as an input field. */
public class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String v) {
		String s = v == null ? "" : v.replace('\n', ' ');
		return s.length() > maxLength ? s.substring(0, maxLength) : s;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			value = sanitize(json.getAsString());
		}
	}
}
