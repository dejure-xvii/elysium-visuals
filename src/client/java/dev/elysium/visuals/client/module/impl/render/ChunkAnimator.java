package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.compat.SodiumCompat;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Locale;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Chunk sections slide into place when they first appear. Vanilla: the
 * section's model-view matrix gets an offset (see {@code LevelRendererChunkMixin}).
 * Sodium: its terrain vertex shader is patched ({@link #patchSodiumShader}) to
 * offset each section by the time Sodium itself records for its first upload;
 * the settings are baked into the shader as constants, so a change makes Sodium
 * rebuild its programs (once the settings stop changing).
 */
public class ChunkAnimator extends Module {
	/** How far a section travels, in blocks. */
	private static final float TRAVEL = 64f;
	/** Settings must stay the same this long before Sodium's shaders are rebuilt (a slider is being dragged). */
	private static final long REBUILD_DELAY_MS = 400;

	private final ModeSetting mode = add(new ModeSetting("mode", "Режим",
			List.of(option("below", "Снизу"), option("above", "Сверху"), option("hybrid", "Гибрид"), option("slide", "Сдвиг")),
			"below"));
	private final ModeSetting easing = add(new ModeSetting("easing", "Плавность",
			List.of(option("sine", "Синус"), option("cubic", "Кубическая"), option("quad", "Квадратичная"), option("bounce", "Отскок")),
			"cubic"));
	private final NumberSetting duration = add(new NumberSetting("duration", "Длительность", 1000, 100, 3000, 50, " мс"));
	private final BooleanSetting skipNear = add(new BooleanSetting("skip_near", "Не анимировать рядом", true));
	private final NumberSetting nearRadius = add(new NumberSetting("near_radius", "Радиус «рядом»", 2, 1, 8, 1, " чанк."))
			.visibleWhen(skipNear::isOn);

	private static String compiledKey = "";
	private static String pendingKey = "";
	private static long pendingSince;

	public ChunkAnimator() {
		super("chunk_animator", "ChunkAnimator", "Анимация появления чанков (совместим с Sodium)", Category.RENDER);
		if (SodiumCompat.INSTALLED) {
			ClientTickEvents.END_CLIENT_TICK.register(mc -> watchSodiumShader());
		}
	}

	private static ChunkAnimator active() {
		ChunkAnimator m = ModuleManager.get().find(ChunkAnimator.class);
		return m != null && m.isEnabled() ? m : null;
	}

	// --- Easing (same formulas as in the Sodium shader patch) ----------------------------------

	private int easingIndex() {
		return switch (easing.get()) {
			case "sine" -> 0;
			case "quad" -> 2;
			case "bounce" -> 3;
			default -> 1;
		};
	}

	private int modeIndex() {
		return switch (mode.get()) {
			case "above" -> 1;
			case "hybrid" -> 2;
			case "slide" -> 3;
			default -> 0;
		};
	}

	private static float ease(int type, float t) {
		return switch (type) {
			case 0 -> (float) Math.sin(t * Math.PI / 2);
			case 2 -> 1 - (1 - t) * (1 - t);
			case 3 -> bounce(t);
			default -> 1 - (1 - t) * (1 - t) * (1 - t);
		};
	}

	private static float bounce(float t) {
		final float n = 7.5625f, d = 2.75f;
		if (t < 1 / d) {
			return n * t * t;
		} else if (t < 2 / d) {
			t -= 1.5f / d;
			return n * t * t + 0.75f;
		} else if (t < 2.5f / d) {
			t -= 2.25f / d;
			return n * t * t + 0.9375f;
		}
		t -= 2.625f / d;
		return n * t * t + 0.984375f;
	}

	// --- Vanilla renderer -------------------------------------------------------------------

	/**
	 * The model-view matrix for a vanilla chunk section with render origin
	 * {@code origin} that was first uploaded at {@code uploadedMs}, or the
	 * matrix itself when it isn't animated.
	 */
	public static Matrix4fc animate(Matrix4fc modelView, BlockPos origin, long uploadedMs) {
		ChunkAnimator m = active();
		if (m == null || uploadedMs == 0) {
			return modelView;
		}
		long elapsed = Util.getMillis() - uploadedMs;
		float t = (float) elapsed / m.duration.floatValue();
		if (t >= 1 || elapsed < 0) {
			return modelView;
		}
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double cx = origin.getX() + 8 - cam.x, cy = origin.getY() + 8 - cam.y, cz = origin.getZ() + 8 - cam.z;
		if (m.skipNear.isOn()) {
			double r = m.nearRadius.get() * 16;
			if (cx * cx + cz * cz < r * r) {
				return modelView;
			}
		}
		float k = (1 - ease(m.easingIndex(), Math.max(0, t))) * TRAVEL;
		float dx = 0, dy = 0, dz = 0;
		switch (m.modeIndex()) {
			case 1 -> dy = k;
			case 2 -> dy = (((origin.getX() >> 4) + (origin.getZ() >> 4)) & 1) == 0 ? -k : k;
			case 3 -> {
				double len = Math.sqrt(cx * cx + cz * cz);
				if (len > 1e-3) {
					dx = (float) (cx / len * k);
					dz = (float) (cz / len * k);
				}
			}
			default -> dy = -k;
		}
		return new Matrix4f(modelView).translate(dx, dy, dz);
	}

	// --- Sodium -----------------------------------------------------------------------------

	/** The shader constants for the current settings, or "" when nothing is animated. */
	private static String sodiumKey() {
		ChunkAnimator m = active();
		if (m == null) {
			return "";
		}
		float near = m.skipNear.isOn() ? m.nearRadius.floatValue() * 16 : 0;
		return String.format(Locale.ROOT, "%d,%d,%.1f,%.1f", m.modeIndex(), m.easingIndex(), m.duration.floatValue(), near * near);
	}

	private static void watchSodiumShader() {
		String key = sodiumKey();
		long now = Util.getMillis();
		if (!key.equals(pendingKey)) {
			pendingKey = key;
			pendingSince = now;
		}
		if (!pendingKey.equals(compiledKey) && now - pendingSince >= REBUILD_DELAY_MS) {
			compiledKey = pendingKey;
			SodiumCompat.rebuildTerrainShaders();
		}
	}

	private static final String SODIUM_MAIN = "void main() {";
	private static final String SODIUM_POSITION = "vec3 position = _vert_position + translation;";

	/**
	 * Adds the animation to Sodium's terrain vertex shader source; returns it
	 * unchanged when the module is off or the source doesn't look as expected.
	 */
	public static String patchSodiumShader(String source) {
		String key = sodiumKey();
		compiledKey = key;
		if (key.isEmpty() || !source.contains(SODIUM_MAIN) || !source.contains(SODIUM_POSITION)
				|| !source.contains("u_SectionTimeInfo") || !source.contains("u_CurrentTime")) {
			return source;
		}
		String[] p = key.split(",");
		String functions = """
				// Elysium ChunkAnimator
				float elysium_bounce(float t) {
				    const float n = 7.5625, d = 2.75;
				    if (t < 1.0 / d) return n * t * t;
				    if (t < 2.0 / d) { t -= 1.5 / d; return n * t * t + 0.75; }
				    if (t < 2.5 / d) { t -= 2.25 / d; return n * t * t + 0.9375; }
				    t -= 2.625 / d; return n * t * t + 0.984375;
				}

				float elysium_ease(float t) {
				    int e = %2$s;
				    if (e == 0) return sin(t * 1.5707963);
				    if (e == 2) return 1.0 - (1.0 - t) * (1.0 - t);
				    if (e == 3) return elysium_bounce(t);
				    return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
				}

				vec3 elysium_chunk_offset(vec3 center, uint drawId) {
				    int t0 = texelFetch(u_SectionTimeInfo, int((u_RegionID * 256u) + drawId)).r;
				    if (t0 < 0 || dot(center.xz, center.xz) < %4$s) return vec3(0.0);
				    float t = clamp(float(u_CurrentTime - t0) / %3$s, 0.0, 1.0);
				    if (t >= 1.0) return vec3(0.0);
				    float k = (1.0 - elysium_ease(t)) * %5$s;
				    int mode = %1$s;
				    if (mode == 1) return vec3(0.0, k, 0.0);
				    if (mode == 2) {
				        uvec3 c = (uvec3(drawId) >> uvec3(5u, 0u, 2u)) & uvec3(7u, 3u, 7u);
				        return vec3(0.0, ((c.x + c.z) & 1u) == 0u ? -k : k, 0.0);
				    }
				    if (mode == 3) {
				        float len = length(center.xz);
				        return len > 0.001 ? vec3(center.x / len * k, 0.0, center.z / len * k) : vec3(0.0);
				    }
				    return vec3(0.0, -k, 0.0);
				}

				""".formatted(p[0], p[1], p[2], p[3], String.format(Locale.ROOT, "%.1f", TRAVEL));
		String call = SODIUM_POSITION + "\n    position += elysium_chunk_offset(translation + vec3(8.0), _draw_id);";
		return source.replace(SODIUM_MAIN, functions + SODIUM_MAIN).replace(SODIUM_POSITION, call);
	}
}
