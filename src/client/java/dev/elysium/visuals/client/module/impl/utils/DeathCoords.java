package dev.elysium.visuals.client.module.impl.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.particle.ParticleRenderTypes;
import dev.elysium.visuals.client.particle.ParticleTexture;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import dev.elysium.visuals.client.util.Alerts;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * After death: the coordinates and dimension in chat (only you see them;
 * click to copy) and, optionally, a light pillar over the spot until you get there.
 */
public class DeathCoords extends Module {
	private final BooleanSetting beam = add(new BooleanSetting("beam", "Столб света на месте смерти", true));
	private final NumberSetting reach = add(new NumberSetting("reach", "Убрать столб ближе чем", 3, 1, 20, 1, " бл.")).visibleWhen(beam::isOn);

	private BlockPos pos;
	private ResourceKey<Level> dimension;
	private boolean wasDead;

	public DeathCoords() {
		super("death_coords", "DeathCoords", "Координаты смерти в чат (клик копирует) и столб света", Category.UTILS);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	/** DeathPoint stays quiet in chat while this module writes the message. */
	public static boolean writesChat() {
		DeathCoords m = ModuleManager.get().find(DeathCoords.class);
		return m != null && m.isEnabled();
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			pos = player.blockPosition();
			dimension = player.level().dimension();
			String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
			Component link = Component.literal("[" + coords + "]")
					.withStyle(s -> s.withColor(ThemeColors.secondary() & 0xFFFFFF).withUnderlined(true)
							.withClickEvent(new ClickEvent.CopyToClipboard(coords))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Нажмите, чтобы скопировать"))));
			Alerts.chat(Component.literal("[Elysium] ").withColor(ThemeColors.primary() & 0xFFFFFF)
					.append(Component.literal("Вы умерли: ").withColor(0xFFFFFF))
					.append(link)
					.append(Component.literal(" · " + dimensionName(dimension)).withColor(0xBBBBBB)));
		}
		wasDead = dead;
		if (!dead && pos != null && player.level().dimension() == dimension) {
			double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
			if (Math.sqrt(dx * dx + dz * dz) < reach.get() && Math.abs(pos.getY() - player.getY()) < 6) {
				pos = null;
			}
		}
	}

	static String dimensionName(ResourceKey<Level> dim) {
		if (dim == Level.NETHER) {
			return "Незер";
		}
		return dim == Level.END ? "Энд" : dim == Level.OVERWORLD ? "Верхний мир" : dim.identifier().getPath();
	}

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !beam.isOn() || pos == null || mc.level == null || mc.player == null || mc.player.isDeadOrDying()
				|| mc.level.dimension() != dimension) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		float x = (float) (pos.getX() + 0.5 - cam.x), y = (float) (pos.getY() - cam.y), z = (float) (pos.getZ() + 0.5 - cam.z);
		int color = ThemeColors.primary() | 0xFF000000;
		float pulse = 0.8f + 0.2f * (float) Math.sin(System.nanoTime() / 3e8);
		float h = (float) (mc.level.getMaxY() - pos.getY());
		PoseStack ps = ctx.poseStack();
		ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(false), (pose, vc) -> {
			pillar(pose, vc, x, y, z, 1.4f, h, GlowGeometry.scaleAlpha(color, 0.25f * pulse));
			pillar(pose, vc, x, y, z, 0.45f, h, GlowGeometry.scaleAlpha(color, 0.8f));
		});
		ctx.submitNodeCollector().submitCustomGeometry(ps, ParticleRenderTypes.get(ParticleTexture.BLOOM, false), (pose, vc) ->
				GlowGeometry.flatSprite(pose, vc, x, y + 0.03f, z, 2.6f, 2.6f, 0, GlowGeometry.scaleAlpha(color, 0.6f * pulse)));
	}

	/** A camera-facing vertical quad, bright at the bottom and fading out up high. */
	private static void pillar(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float w, float h, int color) {
		float len = (float) Math.sqrt(x * x + z * z);
		if (len < 1e-4f) {
			return;
		}
		float sx = -z / len * w * 0.5f, sz = x / len * w * 0.5f;
		int top = color & 0x00FFFFFF;
		vc.addVertex(pose, x - sx, y, z - sz).setColor(color);
		vc.addVertex(pose, x + sx, y, z + sz).setColor(color);
		vc.addVertex(pose, x + sx, y + h, z + sz).setColor(top);
		vc.addVertex(pose, x - sx, y + h, z - sz).setColor(top);
	}
}
