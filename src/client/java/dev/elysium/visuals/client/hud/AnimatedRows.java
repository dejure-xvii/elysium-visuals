package dev.elysium.visuals.client.hud;

import dev.elysium.visuals.client.gui.anim.SmoothValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Rows of a HUD list that fade and grow in when they appear and fade and
 * collapse when they disappear (≈ 200 ms). Rows are matched between frames by
 * a key; a removed row keeps its last data and its place until it is gone.
 */
public final class AnimatedRows<T> {
	public static final class Row<T> {
		private final String key;
		private T value;
		private boolean alive;
		private final SmoothValue shown = new SmoothValue(0, 16f);

		private Row(String key) {
			this.key = key;
		}

		public T value() {
			return value;
		}

		/** 0..1 appearance; use it as the row's alpha. */
		public float shown() {
			return shown.get();
		}

		/** Height factor of the row (eased {@link #shown()}). */
		public float open() {
			return ease(shown.get());
		}
	}

	private final Function<T, String> key;
	private List<Row<T>> rows = new ArrayList<>();

	public AnimatedRows(Function<T, String> key) {
		this.key = key;
	}

	/** Feeds this frame's rows (in display order) and advances the animations. */
	public void update(List<T> current) {
		Map<String, Row<T>> byKey = new LinkedHashMap<>();
		for (Row<T> r : rows) {
			byKey.put(r.key, r);
		}
		List<Row<T>> next = new ArrayList<>(current.size() + rows.size());
		Set<String> used = new HashSet<>();
		for (T value : current) {
			String k = key.apply(value);
			for (int n = 2; !used.add(k); n++) {
				k = key.apply(value) + "#" + n;
			}
			Row<T> row = byKey.remove(k);
			if (row == null) {
				row = new Row<>(k);
			}
			row.value = value;
			row.alive = true;
			next.add(row);
		}
		// Rows that left stay right after the row that preceded them, until they have faded out.
		for (Row<T> leaving : byKey.values()) {
			leaving.alive = false;
			int at = 0;
			for (int i = rows.indexOf(leaving) - 1; i >= 0; i--) {
				int found = next.indexOf(rows.get(i));
				if (found >= 0) {
					at = found + 1;
					break;
				}
			}
			next.add(at, leaving);
		}
		next.removeIf(r -> r.shown.update(r.alive ? 1f : 0f) < 0.01f && !r.alive);
		rows = next;
	}

	public List<Row<T>> rows() {
		return rows;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Total animated height for rows of {@code rowH}. */
	public float height(float rowH) {
		float h = 0;
		for (Row<T> r : rows) {
			h += rowH * r.open();
		}
		return h;
	}

	/** Ease-out, so rows open quickly and settle softly. */
	public static float ease(float t) {
		return 1 - (1 - t) * (1 - t);
	}
}
