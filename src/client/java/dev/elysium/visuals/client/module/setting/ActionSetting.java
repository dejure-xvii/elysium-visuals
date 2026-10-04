package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.function.Supplier;

/** A button in the module settings (e.g. "open another mod's settings"). Not saved. */
public class ActionSetting extends Setting<Boolean> {
	private final Supplier<String> label;
	private final Runnable action;

	public ActionSetting(String id, Supplier<String> label, Runnable action) {
		super(id, label.get(), false);
		this.label = label;
		this.action = action;
	}

	/** Button text; may change (e.g. when the action is unavailable). */
	public String label() {
		return label.get();
	}

	public void run() {
		action.run();
	}

	@Override
	public void set(Boolean newValue) {
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
	}
}
