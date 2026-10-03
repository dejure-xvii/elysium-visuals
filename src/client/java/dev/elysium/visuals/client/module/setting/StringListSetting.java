package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * An editable list of strings (e.g. player names). Entries are unique
 * (case-insensitive) and must pass {@code validator}. Saved as a JSON array.
 */
public class StringListSetting extends Setting<List<String>> {
	private final Predicate<String> validator;
	private final String placeholder;

	public StringListSetting(String id, String name, String placeholder, Predicate<String> validator) {
		super(id, name, List.of());
		this.validator = validator;
		this.placeholder = placeholder;
	}

	/** Hint shown in the empty input field. */
	public String placeholder() {
		return placeholder;
	}

	public boolean isValid(String entry) {
		return validator.test(entry);
	}

	public boolean contains(String entry) {
		for (String s : value) {
			if (s.equalsIgnoreCase(entry)) {
				return true;
			}
		}
		return false;
	}

	/** @return true if the entry was added */
	public boolean add(String entry) {
		entry = entry.trim();
		if (!isValid(entry) || contains(entry)) {
			return false;
		}
		List<String> next = new ArrayList<>(value);
		next.add(entry);
		set(next);
		return true;
	}

	public void remove(String entry) {
		List<String> next = new ArrayList<>(value);
		next.removeIf(s -> s.equalsIgnoreCase(entry));
		set(next);
	}

	@Override
	protected List<String> sanitize(List<String> v) {
		List<String> result = new ArrayList<>();
		for (String s : v) {
			String t = s.trim();
			boolean duplicate = result.stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).equals(t.toLowerCase(Locale.ROOT)));
			if (isValid(t) && !duplicate) {
				result.add(t);
			}
		}
		return Collections.unmodifiableList(result);
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
		List<String> loaded = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray()) {
			if (e.isJsonPrimitive()) {
				loaded.add(e.getAsString());
			}
		}
		value = sanitize(loaded);
	}
}
