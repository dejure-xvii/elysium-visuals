package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.elysium.visuals.client.util.ColorUtil;

/** ARGB color, shown as a swatch that expands into a color picker. Saved as HEX. */
public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String id, String name, int defaultArgb) {
		super(id, name, defaultArgb);
	}

	public int argb() {
		return value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(ColorUtil.toHex(value));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			Integer c = ColorUtil.parseHex(json.getAsString());
			if (c != null) {
				value = c;
			}
		}
	}
}
