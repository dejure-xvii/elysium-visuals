package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** Exactly one option out of a fixed list, shown as a dropdown. Saved as the option id. */
public class ModeSetting extends Setting<String> {
	private final List<MultiSelectSetting.Option> options;

	public ModeSetting(String id, String name, List<MultiSelectSetting.Option> options, String defaultId) {
		super(id, name, defaultId);
		this.options = List.copyOf(options);
	}

	public List<MultiSelectSetting.Option> options() {
		return options;
	}

	public boolean is(String optionId) {
		return value.equals(optionId);
	}

	/** Label of the selected option. */
	public String label() {
		for (MultiSelectSetting.Option o : options) {
			if (o.id().equals(value)) {
				return o.label();
			}
		}
		return value;
	}

	@Override
	protected String sanitize(String v) {
		for (MultiSelectSetting.Option o : options) {
			if (o.id().equals(v)) {
				return v;
			}
		}
		return defaultValue();
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
