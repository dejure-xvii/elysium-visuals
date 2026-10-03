package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import dev.elysium.visuals.client.module.ModuleManager;

import java.util.function.BooleanSupplier;

/**
 * A configurable value of a module. Changing it through {@link #set} marks the
 * config dirty so it gets saved.
 */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;
	private boolean global;

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	/** Key in the JSON config (stable; don't rename once released). */
	public String id() {
		return id;
	}

	/** Name shown in the GUI. */
	public String name() {
		return name;
	}

	public T get() {
		return value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void set(T newValue) {
		T sanitized = sanitize(newValue);
		if (!sanitized.equals(value)) {
			value = sanitized;
			ModuleManager.get().markDirty();
		}
	}

	/**
	 * Shows this setting in the GUI only while {@code condition} holds (e.g. a
	 * slider that only matters when some option is selected). The value is
	 * kept and saved either way.
	 */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
		return (S) this;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	/**
	 * Marks this setting as shared by all configs (e.g. the friend list):
	 * {@code .cfg load} and {@code .cfg reset} leave it untouched.
	 */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S global() {
		this.global = true;
		return (S) this;
	}

	public boolean isGlobal() {
		return global;
	}

	public void reset() {
		set(defaultValue);
	}

	/** Clamps or otherwise validates a value; the default accepts anything. */
	protected T sanitize(T v) {
		return v;
	}

	public abstract JsonElement toJson();

	/** Loads a value from the config without marking it dirty. Invalid input is ignored. */
	public abstract void fromJson(JsonElement json);
}
