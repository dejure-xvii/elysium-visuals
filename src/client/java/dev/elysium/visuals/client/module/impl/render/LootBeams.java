package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.ModeSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/** Light pillars over dropped items on the ground, colored by rarity or by the theme. */
public class LootBeams extends Module {
	private static final int MAX_BEAMS = 128;

	private final NumberSetting height = add(new NumberSetting("height", "Высота", 3, 0.5, 12, 0.25, " бл."));
	private final NumberSetting width = add(new NumberSetting("width", "Ширина", 0.18, 0.05, 0.8, 0.01, " бл."));
	private final NumberSetting opacity = add(new NumberSetting("opacity", "Непрозрачность", 0.7, 0.05, 1, 0.05));
	private final ModeSetting colorMode = add(new ModeSetting("color_mode", "Цвет",
			List.of(option("rarity", "По редкости"), option("theme", "Цвет темы")), "rarity"));
	private final NumberSetting radius = add(new NumberSetting("radius", "Радиус", 48, 8, 128, 1, " бл."));

	private record Beam(double x, double y, double z, int color) {
	}

	private final List<Beam> beams = new ArrayList<>();

	public LootBeams() {
		super("loot_beams", "LootBeams", "Световые столбы над выброшенными предметами", Category.RENDER);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	private int colorOf(ItemEntity item, int theme) {
		if (colorMode.is("theme")) {
			return theme;
		}
		TextColor c = TextColor.fromLegacyFormat(item.getItem().getRarity().color());
		return 0xFF000000 | (c == null ? 0xFFFFFF : c.getValue());
	}

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double r2 = radius.get() * radius.get();
		int theme = ThemeColors.primary();
		beams.clear();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ItemEntity item && item.onGround() && item.distanceToSqr(mc.player) <= r2 && beams.size() < MAX_BEAMS) {
				beams.add(new Beam(item.xo + (item.getX() - item.xo) * partial, item.yo + (item.getY() - item.yo) * partial,
						item.zo + (item.getZ() - item.zo) * partial, colorOf(item, theme)));
			}
		}
		if (beams.isEmpty()) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		float h = height.floatValue(), w = width.floatValue(), a = opacity.floatValue();
		float pulse = 0.85f + 0.15f * (float) Math.sin(System.nanoTime() / 4e8);
		List<Beam> list = List.copyOf(beams);
		PoseStack ps = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(false), (pose, vc) -> {
			for (Beam b : list) {
				float x = (float) (b.x - cam.x), y = (float) (b.y - cam.y), z = (float) (b.z - cam.z);
				// A soft wide body and a brighter thin core, both facing the camera.
				pillar(pose, vc, x, y, z, w * 2.4f, h, GlowGeometry.scaleAlpha(b.color, 0.22f * a * pulse));
				pillar(pose, vc, x, y, z, w, h * 0.9f, GlowGeometry.scaleAlpha(b.color, 0.75f * a));
			}
		});
		ctx.submitNodeCollector().submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) -> {
			for (Beam b : list) {
				GlowGeometry.flatSprite(pose, vc, (float) (b.x - cam.x), (float) (b.y - cam.y) + 0.02f, (float) (b.z - cam.z),
						w * 3.5f, w * 3.5f, 0, GlowGeometry.scaleAlpha(b.color, 0.5f * a * pulse));
			}
		});
	}

	/** A vertical quad turned towards the camera, opaque at the bottom and fading to nothing at the top. */
	private static void pillar(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float w, float h, int color) {
		float len = (float) Math.sqrt(x * x + z * z);
		if (len < 1e-4f) {
			return;
		}
		// Horizontal side vector, perpendicular to the view direction.
		float sx = -z / len * w * 0.5f, sz = x / len * w * 0.5f;
		int top = color & 0x00FFFFFF;
		vc.addVertex(pose, x - sx, y, z - sz).setColor(color);
		vc.addVertex(pose, x + sx, y, z + sz).setColor(color);
		vc.addVertex(pose, x + sx, y + h, z + sz).setColor(top);
		vc.addVertex(pose, x - sx, y + h, z - sz).setColor(top);
	}
}
