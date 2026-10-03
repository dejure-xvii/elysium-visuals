package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** Number in a range with a step, shown as a slider. */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String suffix;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		this(id, name, defaultValue, min, max, step, "");
	}

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step, String suffix) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
		this.value = sanitize(defaultValue);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	public float floatValue() {
		return value.floatValue();
	}

	/** Position of the value in the range, 0..1. */
	public double fraction() {
		return max > min ? (value - min) / (max - min) : 0;
	}

	public void setFraction(double f) {
		set(min + (max - min) * Math.max(0, Math.min(1, f)));
	}

	/** Text shown next to the slider, e.g. "1.5x" or "12". */
	public String display() {
		int decimals = step >= 1 ? 0 : (step >= 0.1 ? 1 : 2);
		return String.format(Locale.ROOT, "%." + decimals + "f", value) + suffix;
	}

	@Override
	protected Double sanitize(Double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) {
			clamped = min + Math.round((clamped - min) / step) * step;
			// Avoid values like 0.30000000000000004.
			clamped = Math.round(clamped * 1e6) / 1e6;
		}
		return Math.max(min, Math.min(max, clamped));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = sanitize(json.getAsDouble());
		}
	}
}
