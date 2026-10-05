package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * FPS savers. Options another installed mod already covers are greyed out
 * with its name, so nothing runs twice (Sodium, Entity Culling, ImmediatelyFast).
 * The level picks a preset; changing options by hand switches it to "Свой".
 */
public class Optimizer extends Module {
	private static final boolean ENTITY_CULLING = FabricLoader.getInstance().isModLoaded("entityculling");

	private static final Map<String, Set<String>> PRESETS = Map.of(
			"low", Set.of("vsync", "blur"),
			"medium", Set.of("vsync", "blur", "weather", "entity_cull", "entity_tiny"),
			"ultra", Set.of("vsync", "blur", "grass", "particles", "weather", "entity_cull", "entity_tiny", "entity_distance"));

	/** Set while a preset writes the options, so that doesn't count as a manual edit. */
	private boolean applyingPreset;

	/** Picking a level selects its options at once. */
	private final ModeSetting level = add(new ModeSetting("level", "Уровень",
			List.of(option("low", "Низкий"), option("medium", "Средний"), option("ultra", "Ультра"), option("custom", "Свой")), "medium") {
		@Override
		public void set(String v) {
			super.set(v);
			Set<String> preset = PRESETS.get(get());
			if (preset != null && options != null) {
				applyingPreset = true;
				try {
					options.set(new LinkedHashSet<>(preset));
				} finally {
					applyingPreset = false;
				}
			}
		}
	});
	/** Changing the options by hand switches the level to "Свой". */
	private final MultiSelectSetting options = add(new MultiSelectSetting("options", "Опции",
			List.of(option("vsync", "Синхронизация кадров (выкл. VSync)"),
					option("blur", "Убрать блюр меню"),
					option("grass", "Убрать траву и цветы"),
					option("particles", "Убрать партиклы"),
					option("weather", "Убрать погоду"),
					option("entity_cull", "Отсечение сущностей за стенами"),
					option("entity_tiny", "Окклюзия далёких и мелких сущностей"),
					option("entity_distance", "Лимит дальности сущностей")),
			PRESETS.get("medium")) {
		@Override
		public void set(Set<String> v) {
			super.set(v);
			if (!applyingPreset && level != null && !level.is("custom")) {
				Set<String> preset = PRESETS.get(level.get());
				if (preset == null || !preset.equals(get())) {
					level.set("custom");
				}
			}
		}
	})
			.disableWhen("entity_cull", () -> ENTITY_CULLING ? "уже делает Entity Culling" : null);
	private final NumberSetting entityDistance = add(new NumberSetting("entity_distance", "Дальность сущностей", 64, 16, 160, 4, " бл."))
			.visibleWhen(() -> options.isSelected("entity_distance"));

	private boolean vsyncWasOn;
	private boolean grassApplied;
	/** Entity id -> (visible bit << 62) | tick of the last wall check. */
	private final Int2LongOpenHashMap visibility = new Int2LongOpenHashMap();
	private static volatile boolean hideGrass;

	public Optimizer() {
		super("optimizer", "Optimizer", "Больше FPS: пресеты и отдельные опции, без конфликтов с Sodium и Entity Culling", Category.RENDER);
	}

	private static Optimizer instance() {
		Optimizer m = ModuleManager.get().find(Optimizer.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** True if the Optimizer is on and the option is selected and not covered by another mod. */
	public static boolean active(String option) {
		Optimizer m = instance();
		return m != null && m.options.isActive(option);
	}

	@Override
	public void onTick(Minecraft mc) {
		// VSync off while selected; restored afterwards.
		var vsync = mc.options.enableVsync();
		if (options.isActive("vsync")) {
			if (vsync.get()) {
				vsyncWasOn = true;
				vsync.set(false);
			}
		} else if (vsyncWasOn) {
			vsyncWasOn = false;
			vsync.set(true);
		}

		boolean grass = options.isActive("grass");
		if (grass != grassApplied) {
			setGrass(mc, grass);
		}
		if (mc.level != null && mc.level.getGameTime() % 200 == 0) {
			visibility.clear();
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (vsyncWasOn) {
			vsyncWasOn = false;
			mc.options.enableVsync().set(true);
		}
		if (grassApplied) {
			setGrass(mc, false);
		}
		visibility.clear();
	}

	private void setGrass(Minecraft mc, boolean on) {
		grassApplied = on;
		hideGrass = on;
		if (mc.level != null) {
			mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
		}
	}

	/** Grass, ferns and flowers are skipped by chunk meshing (vanilla and Sodium) while "Убрать траву" is on. */
	public static boolean hidesBlock(BlockState state) {
		if (!hideGrass) {
			return false;
		}
		return state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
				|| state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SHORT_DRY_GRASS)
				|| state.is(Blocks.TALL_DRY_GRASS) || state.is(Blocks.BUSH);
	}

	/** Called after vanilla decided the entity is visible: true hides it. */
	public static boolean cullEntity(Entity e) {
		Optimizer m = instance();
		Minecraft mc = Minecraft.getInstance();
		if (m == null || mc.player == null || e == mc.getCameraEntity() || e == mc.player || e.hasPassenger(mc.player)) {
			return false;
		}
		Vec3 cam = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
		AABB box = e.getBoundingBox();
		double dist = Math.sqrt(box.distanceToSqr(cam));
		if (m.options.isActive("entity_distance") && dist > m.entityDistance.get()) {
			return true;
		}
		if (m.options.isActive("entity_tiny")) {
			double size = Math.max(box.getXsize(), box.getYsize());
			if (dist > 24 && size / dist < 0.015) {
				return true;
			}
		}
		if (m.options.isActive("entity_cull") && dist > 8 && !e.isCurrentlyGlowing()) {
			return !m.visible(e, cam, box);
		}
		return false;
	}

	/** Line of sight to the entity through opaque blocks, re-checked every 4 ticks. */
	private boolean visible(Entity e, Vec3 cam, AABB box) {
		long now = e.level().getGameTime();
		long cached = visibility.getOrDefault(e.getId(), Long.MIN_VALUE);
		if (cached != Long.MIN_VALUE && now - (cached & 0x3FFFFFFFFFFFFFFFL) < 4) {
			return (cached >>> 62) != 0;
		}
		Vec3 center = box.getCenter();
		boolean vis = clear(e, cam, center) || clear(e, cam, new Vec3(center.x, box.maxY, center.z))
				|| clear(e, cam, new Vec3(center.x, box.minY + 0.1, center.z));
		visibility.put(e.getId(), (vis ? 1L << 62 : 0) | (now & 0x3FFFFFFFFFFFFFFFL));
		return vis;
	}

	private static boolean clear(Entity e, Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Mth.ceil(len / 0.45);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			int x = Mth.floor(from.x + dx * t), y = Mth.floor(from.y + dy * t), z = Mth.floor(from.z + dz * t);
			if (x == lx && y == ly && z == lz) {
				continue;
			}
			lx = x;
			ly = y;
			lz = z;
			pos.set(x, y, z);
			if (e.level().getBlockState(pos).isSolidRender()) {
				return false;
			}
		}
		return true;
	}
}
