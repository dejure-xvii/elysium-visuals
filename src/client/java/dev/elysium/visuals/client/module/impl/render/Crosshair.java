package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;

/** Replaces the vanilla crosshair with a configurable one. */
public class Crosshair extends Module {
	private final NumberSetting length = add(new NumberSetting("length", "Длина линий", 4, 0, 12, 0.5));
	private final NumberSetting gap = add(new NumberSetting("gap", "Отступ от центра", 2, 0, 10, 0.5));
	private final NumberSetting thickness = add(new NumberSetting("thickness", "Толщина", 1, 0.5, 4, 0.5));
	private final BooleanSetting dot = add(new BooleanSetting("dot", "Точка в центре", false));
	private final BooleanSetting outline = add(new BooleanSetting("outline", "Обводка", true));
	private final BooleanSetting dynamic = add(new BooleanSetting("dynamic", "Расширяться при движении", true));
	private final BooleanSetting attackIndicator = add(new BooleanSetting("attack_indicator", "Индикатор силы удара", true));
	private final BooleanSetting entityColor = add(new BooleanSetting("entity_color", "Другой цвет при наведении на сущность", true));
	private final ColorSetting color = add(new ColorSetting("color", "Цвет", 0xFFFFFFFF));
	private final ColorSetting targetColor = add(new ColorSetting("target_color", "Цвет на сущности", 0xFFFF5A5A));

	private float spread;

	public Crosshair() {
		super("crosshair", "Crosshair", "Свой прицел: длина, отступ, толщина, точка, цвет", Category.RENDER);
	}

	@Override
	public boolean hidesVanillaHud(Identifier vanillaElement) {
		return vanillaElement.equals(VanillaHudElements.CROSSHAIR);
	}

	@Override
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator() || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float target = 0f;
		if (dynamic.isOn()) {
			target = (float) Math.min(4, player.getDeltaMovement().horizontalDistance() * 12);
			if (!player.onGround()) {
				target += 1.5f;
			}
		}
		spread += (target - spread) * 0.25f;

		boolean onEntity = entityColor.isOn() && mc.hitResult instanceof EntityHitResult;
		int c = onEntity ? targetColor.argb() : color.argb();
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		float t = thickness.floatValue(), len = length.floatValue(), gp = gap.floatValue() + spread;

		if (outline.isOn()) {
			int o = ColorUtil.withAlpha(0xFF000000, Math.round(ColorUtil.alpha(c) * 0.6f));
			lines(g, cx, cy, gp - 0.5f, len + 1, t + 1, o);
			if (dot.isOn()) {
				RenderUtil.rect(g, cx - t / 2 - 0.5f, cy - t / 2 - 0.5f, t + 1, t + 1, o);
			}
		}
		lines(g, cx, cy, gp, len, t, c);
		if (dot.isOn()) {
			RenderUtil.rect(g, cx - t / 2, cy - t / 2, t, t, c);
		}

		if (attackIndicator.isOn()) {
			float strength = player.getAttackStrengthScale(partialTick);
			if (strength < 1f) {
				float w = 16, y = cy + gp + len + 4;
				RenderUtil.rect(g, cx - w / 2, y, w, 2, 0x80000000);
				RenderUtil.rect(g, cx - w / 2, y, w * strength, 2, c);
			}
		}
	}

	private static void lines(GuiGraphicsExtractor g, float cx, float cy, float gap, float len, float t, int c) {
		if (len <= 0) {
			return;
		}
		RenderUtil.rect(g, cx - t / 2, cy - gap - len, t, len, c);
		RenderUtil.rect(g, cx - t / 2, cy + gap, t, len, c);
		RenderUtil.rect(g, cx - gap - len, cy - t / 2, len, t, c);
		RenderUtil.rect(g, cx + gap, cy - t / 2, len, t, c);
	}
}
