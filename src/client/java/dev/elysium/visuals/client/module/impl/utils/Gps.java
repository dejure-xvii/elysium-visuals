package dev.elysium.visuals.client.module.impl.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.gps.GpsTarget;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.ActionSetting;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.theme.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shows the GPS mark ({@link GpsTarget}, set with {@code .gps} or AutoEventGPS):
 * a HUD plate with an arrow pointing to it and the distance, and a light pillar
 * on the spot. The mark disappears once you get there.
 */
public class Gps extends Module {
	private final BooleanSetting pillar = add(new BooleanSetting("pillar", "Столб света", true));
	private final BooleanSetting arrow = add(new BooleanSetting("arrow", "Стрелка на экране", true));
	@SuppressWarnings("unused")
	private final ActionSetting clear = add(new ActionSetting("clear", () -> "Убрать метку", GpsTarget::clear));

	public Gps() {
		super("gps", "GPS", "Метка .gps <x> <z>: стрелка с расстоянием и столб света", Category.UTILS);
		enableByDefault();
		addHud(new Plate()).visibleWhen(arrow::isOn);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	public void onTick(Minecraft mc) {
		GpsTarget.tick();
	}

	/** Direction to the mark relative to where you look, in radians (0 = straight ahead, clockwise). */
	static float relativeAngle(LocalPlayer p, GpsTarget.Mark m) {
		double dx = m.x() + 0.5 - p.getX(), dz = m.z() + 0.5 - p.getZ();
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		return (float) Math.toRadians(yaw - p.getYRot());
	}

	private final class Plate extends LabelElement {
		Plate() {
			super("plate", "GPS", Anchor.CROSSHAIR);
		}

		@Override
		public boolean hasContent() {
			return GpsTarget.get() != null && Minecraft.getInstance().player != null;
		}

		@Override
		protected List<Segment> segments(boolean preview) {
			GpsTarget.Mark m = GpsTarget.get();
			if (m == null) {
				return preview ? List.of(new Segment("GPS", 0, true), Segment.of("128 бл.")) : List.of();
			}
			return List.of(new Segment(m.label(), 0, true), Segment.of(Math.round(GpsTarget.distance()) + " бл."),
					new Segment(m.x() + " " + m.z(), 0, false));
		}

		@Override
		protected int leadWidth() {
			return 14;
		}

		@Override
		protected void drawLead(GuiGraphicsExtractor g, Palette p, int x, int y) {
			LocalPlayer player = Minecraft.getInstance().player;
			GpsTarget.Mark m = GpsTarget.get();
			float a = player != null && m != null ? relativeAngle(player, m) : 0;
			float cx = x + 5, cy = y + height / 2f;
			float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
			float tipX = cx + dx * 5, tipY = cy + dy * 5, tailX = cx - dx * 4, tailY = cy - dy * 4;
			int color = ThemeColors.primary() | 0xFF000000;
			RenderUtil.stroke(g, tailX, tailY, tipX, tipY, 1.6f, color);
			// Arrow head: two strokes back from the tip, ±35°.
			for (int s = -1; s <= 1; s += 2) {
				double h = a + Math.PI + s * Math.toRadians(35);
				RenderUtil.stroke(g, tipX, tipY, tipX + (float) Math.sin(h) * 4, tipY - (float) Math.cos(h) * 4, 1.6f, color);
			}
		}
	}

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		GpsTarget.Mark m = GpsTarget.get();
		if (!isEnabled() || !pillar.isOn() || m == null || mc.level == null || mc.player == null) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		float x = (float) (m.x() + 0.5 - cam.x), z = (float) (m.z() + 0.5 - cam.z);
		// The ground at the mark may not be loaded yet: start just below your own height, bright there.
		float y = (float) (mc.player.getY() - 2 - cam.y);
		float h = 400;
		int color = ThemeColors.primary() | 0xFF000000;
		float pulse = 0.8f + 0.2f * (float) Math.sin(System.nanoTime() / 3e8);
		PoseStack ps = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(false), (pose, vc) -> {
			GlowGeometry.pillar(pose, vc, x, y, z, 1.6f, h, GlowGeometry.scaleAlpha(color, 0.25f * pulse));
		});
		// A solid core keeps the theme color visible against a bright sky.
		ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.solid(false), (pose, vc) ->
				GlowGeometry.pillar(pose, vc, x, y, z, 0.5f, h, GlowGeometry.scaleAlpha(color, 0.9f)));
		float groundY = (float) (mc.player.getY() - cam.y);
		ctx.submitNodeCollector().submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) ->
				GlowGeometry.flatSprite(pose, vc, x, groundY + 0.03f, z, 2.6f, 2.6f, 0, GlowGeometry.scaleAlpha(color, 0.5f * pulse)));
	}
}
