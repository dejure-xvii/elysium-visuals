package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Any number of options out of a fixed list, shown as a dropdown with a check
 * box per option. Saved as a JSON array of option ids.
 */
public class MultiSelectSetting extends Setting<Set<String>> {
	public record Option(String id, String label) {
	}

	private final List<Option> options;
	private final java.util.Map<String, java.util.function.Supplier<String>> disabled = new java.util.HashMap<>();

	public MultiSelectSetting(String id, String name, List<Option> options, Set<String> defaultSelected) {
		super(id, name, Collections.unmodifiableSet(new LinkedHashSet<>(defaultSelected)));
		this.options = List.copyOf(options);
	}

	public static Option option(String id, String label) {
		return new Option(id, label);
	}

	public List<Option> options() {
		return options;
	}

	/** Shows {@code optionId} greyed out with the reason the supplier gives (null = available). */
	public MultiSelectSetting disableWhen(String optionId, java.util.function.Supplier<String> reason) {
		disabled.put(optionId, reason);
		return this;
	}

	/** Why the option can't be used right now, or null. */
	public String disabledReason(String optionId) {
		java.util.function.Supplier<String> r = disabled.get(optionId);
		return r == null ? null : r.get();
	}

	/** Selected and not disabled. */
	public boolean isActive(String optionId) {
		return isSelected(optionId) && disabledReason(optionId) == null;
	}

	public boolean isSelected(String optionId) {
		return value.contains(optionId);
	}

	public void toggle(String optionId) {
		Set<String> next = new LinkedHashSet<>(value);
		if (!next.remove(optionId)) {
			next.add(optionId);
		}
		set(next);
	}

	public int selectedCount() {
		return value.size();
	}

	@Override
	protected Set<String> sanitize(Set<String> v) {
		// Keep option order and drop ids that no longer exist.
		Set<String> result = new LinkedHashSet<>();
		for (Option o : options) {
			if (v.contains(o.id())) {
				result.add(o.id());
			}
		}
		return Collections.unmodifiableSet(result);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		value.forEach(array::add);
		return array;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonArray()) {
			return;
		}
		Set<String> loaded = new LinkedHashSet<>();
		for (JsonElement e : json.getAsJsonArray()) {
			if (e.isJsonPrimitive()) {
				loaded.add(e.getAsString());
			}
		}
		value = sanitize(loaded);
	}
}
