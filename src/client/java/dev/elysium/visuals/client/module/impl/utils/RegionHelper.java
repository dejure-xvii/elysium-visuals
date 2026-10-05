package dev.elysium.visuals.client.module.impl.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.hud.LabelElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.ActionSetting;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.region.CuiPayload;
import dev.elysium.visuals.client.region.Regions;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.ThemeColors;
import dev.elysium.visuals.client.render.WorldPipelines;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Help with WorldGuard claims. Region bounds come from {@code /rg info} in
 * chat; the WorldEdit selection comes over WorldEdit's CUI channel when the
 * server supports it (we send the CUI hello on joining), otherwise from
 * WorldEdit's "position set" replies. Regions are drawn as an outline with a
 * see-through fill; the selection gets its two points and a plate with its size,
 * volume and the command to claim it.
 */
public class RegionHelper extends Module {
	/** WorldEdit CUI protocol version we speak. */
	private static final String CUI_HELLO = "v|4";
	/** The WorldEdit CUI mod draws all of this itself and owns the channel. */
	private static final boolean WECUI_INSTALLED = FabricLoader.getInstance().isModLoaded("worldeditcui");

	private final BooleanSetting bounds = add(new BooleanSetting("bounds", "Границы РГ", true));
	private final BooleanSetting claiming = add(new BooleanSetting("claiming", "Создание привата", true));
	private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Через стены", false));
	private final NumberSetting fill = add(new NumberSetting("fill", "Заливка", 15, 0, 60, 1, "%"));
	@SuppressWarnings("unused")
	private final ActionSetting clear = add(new ActionSetting("clear", () -> "Очистить границы", () -> {
		Regions.clearRegions();
		Regions.clearSelection();
	}));

	private static boolean cuiRegistered;

	public RegionHelper() {
		super("region_helper", "RegionHelper", "Границы регионов WorldGuard и выделение WorldEdit", Category.UTILS);
		registerCui();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			Regions.reset();
			if (isEnabled()) {
				sayHello();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Regions.reset());
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
		addHud(new SelectionPlate()).visibleWhen(claiming::isOn);
	}

	private static void registerCui() {
		if (WECUI_INSTALLED || cuiRegistered) {
			return;
		}
		try {
			PayloadTypeRegistry.clientboundPlay().register(CuiPayload.TYPE, CuiPayload.CODEC);
			PayloadTypeRegistry.serverboundPlay().register(CuiPayload.TYPE, CuiPayload.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(CuiPayload.TYPE, (payload, context) -> onCui(payload.message()));
			cuiRegistered = true;
		} catch (RuntimeException e) {
			ElysiumVisuals.LOGGER.warn("WorldEdit CUI channel is taken by another mod; RegionHelper reads the chat only", e);
		}
	}

	private static RegionHelper active() {
		RegionHelper m = ModuleManager.get().find(RegionHelper.class);
		return m != null && m.isEnabled() ? m : null;
	}

	@Override
	protected void onEnable() {
		sayHello();
	}

	/** Tells WorldEdit we understand CUI, so it starts sending the selection. */
	private static void sayHello() {
		Minecraft mc = Minecraft.getInstance();
		if (cuiRegistered && mc.getConnection() != null && ClientPlayNetworking.canSend(CuiPayload.TYPE)) {
			ClientPlayNetworking.send(new CuiPayload(CUI_HELLO));
		}
	}

	static void onCui(String message) {
		if (active() != null) {
			Regions.onCui(message);
		}
	}

	/** Called (mixin) for each chat line. */
	public static void onChat(Component message) {
		if (active() != null) {
			Regions.onChat(message.getString());
		}
	}

	// --- World ------------------------------------------------------------------------------------

	private void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack ps = ctx.poseStack();
		boolean xray = throughWalls.isOn();
		int primary = ThemeColors.primary() | 0xFF000000, secondary = ThemeColors.secondary() | 0xFF000000;
		float fillAlpha = fill.floatValue() / 100f;
		if (bounds.isOn()) {
			for (Regions.Region r : Regions.regions()) {
				AABB b = r.box().move(-cam.x, -cam.y, -cam.z);
				if (fillAlpha > 0) {
					ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.solid(xray),
							(pose, vc) -> box(pose, vc, b, GlowGeometry.scaleAlpha(primary, fillAlpha)));
				}
				ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(xray),
						(pose, vc) -> edges(pose, vc, b, primary, 0.06f));
			}
		}
		BlockPos p1 = Regions.pos1(), p2 = Regions.pos2();
		if (claiming.isOn() && (p1 != null || p2 != null)) {
			if (p1 != null && p2 != null) {
				AABB sel = new AABB(Vec3.atLowerCornerOf(BlockPos.min(p1, p2)), Vec3.atLowerCornerOf(BlockPos.max(p1, p2)).add(1, 1, 1))
						.move(-cam.x, -cam.y, -cam.z);
				if (fillAlpha > 0) {
					ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.solid(xray),
							(pose, vc) -> box(pose, vc, sel, GlowGeometry.scaleAlpha(secondary, fillAlpha * 0.6f)));
				}
				ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(xray),
						(pose, vc) -> edges(pose, vc, sel, secondary, 0.05f));
			}
			// The points themselves: a slightly larger block each (pos1 red, pos2 blue, as in WorldEdit CUI).
			for (int i = 0; i < 2; i++) {
				BlockPos p = i == 0 ? p1 : p2;
				if (p == null) {
					continue;
				}
				AABB b = new AABB(p).inflate(0.02).move(-cam.x, -cam.y, -cam.z);
				int c = i == 0 ? 0xFFFF5A5A : 0xFF5A9BFF;
				ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.solid(true),
						(pose, vc) -> box(pose, vc, b, GlowGeometry.scaleAlpha(c, 0.25f)));
				ctx.submitNodeCollector().submitCustomGeometry(ps, WorldPipelines.glow(true),
						(pose, vc) -> edges(pose, vc, b, c, 0.04f));
			}
		}
	}

	/** The 12 edges of {@code b} as camera-facing ribbons. */
	private static void edges(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color, float width) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
		int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		// Wider far away, so big regions stay readable.
		for (int[] edge : e) {
			float[] a = corners[edge[0]], c = corners[edge[1]];
			float mx = (a[0] + c[0]) / 2, my = (a[1] + c[1]) / 2, mz = (a[2] + c[2]) / 2;
			float w = width * Math.max(1, (float) Math.sqrt(mx * mx + my * my + mz * mz) / 12);
			GlowGeometry.glowLine(pose, vc, a[0], a[1], a[2], c[0], c[1], c[2], w, 1, color, color);
		}
	}

	/** The six faces of {@code b}. */
	private static void box(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		quad(pose, vc, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, vc, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(pose, vc, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(pose, vc, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, vc, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(pose, vc, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color, float... v) {
		for (int i = 0; i < 12; i += 3) {
			vc.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(color);
		}
	}

	// --- HUD ----------------------------------------------------------------------------------------

	private final class SelectionPlate extends LabelElement {
		SelectionPlate() {
			super("selection", "Выделение привата", Anchor.TOP_LEFT);
		}

		@Override
		public boolean hasContent() {
			return Regions.selectionSize() != null;
		}

		@Override
		protected List<Segment> segments(boolean preview) {
			int[] s = Regions.selectionSize();
			if (s == null) {
				s = preview ? new int[]{12, 5, 30} : null;
			}
			if (s == null) {
				return List.of();
			}
			long volume = (long) s[0] * s[1] * s[2];
			return List.of(new Segment("Приват", 0, true),
					Segment.of(s[0] + "×" + s[1] + "×" + s[2]),
					Segment.of(String.format(Locale.ROOT, "%,d бл.", volume).replace(',', ' ')),
					new Segment(s[1] < 256 ? "//expand vert, /rg claim <имя>" : "/rg claim <имя>", 0, false));
		}
	}
}
