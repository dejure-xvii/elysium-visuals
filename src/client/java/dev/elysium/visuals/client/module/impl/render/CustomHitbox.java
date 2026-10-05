package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.ScreenProjector;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Хитбокс: цвет, толщина, заливка и линия глаз. Рисуется экранным прямоугольником (дёшево по FPS). */
public class CustomHitbox extends Module {
    private static final int MAX_ENTITIES = 40;

    private final BooleanSetting players = add(new BooleanSetting("players", "Игроки", true));
    private final BooleanSetting mobs = add(new BooleanSetting("mobs", "Мобы", false));
    private final NumberSetting range = add(new NumberSetting("range", "Дальность", 32, 8, 64, 4, " бл."));
    private final ColorSetting color = add(new ColorSetting("color", "Цвет хитбокса", 0xFFB48CFF));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Толщина линий", 1.5, 0.5, 4, 0.5, " px"));
    private final BooleanSetting fill = add(new BooleanSetting("fill", "Заливка", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("fill_alpha", "Прозрачность заливки", 25, 5, 100, 5, " %")).visibleWhen(fill::isOn);
    private final BooleanSetting eyeLine = add(new BooleanSetting("eye_line", "Линия глаз", true));
    private final ColorSetting eyeColor = add(new ColorSetting("eye_color", "Цвет линии глаз", 0xFFFF5C7A)).visibleWhen(eyeLine::isOn);

    private final ScreenProjector projector = new ScreenProjector();

    public CustomHitbox() {
        super("custom_hitbox", "CustomHitbox", "Хитбокс с настройкой цвета, толщины, заливки и линии глаз", Category.RENDER);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double max = range.floatValue() * range.floatValue();
        projector.begin(g.guiWidth(), g.guiHeight());
        int drawn = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || e.distanceToSqr(mc.player) > max) {
                continue;
            }
            if (e instanceof Player ? !players.isOn() : !mobs.isOn()) {
                continue;
            }
            Vec3 pos = e.getPosition(partialTick);
            AABB b = e.getBoundingBox().move(pos.subtract(e.position()));
            if (drawBox(g, b, e.getEyeHeight()) && ++drawn >= MAX_ENTITIES) {
                break;
            }
        }
    }

    private boolean drawBox(GuiGraphicsExtractor g, AABB b, float eyeHeight) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!projector.project(x, y, z)) {
                return false;
            }
            minX = Math.min(minX, (float) projector.x);
            maxX = Math.max(maxX, (float) projector.x);
            minY = Math.min(minY, (float) projector.y);
            maxY = Math.max(maxY, (float) projector.y);
        }
        float w = maxX - minX, h = maxY - minY;
        if (w < 2 || h < 2) {
            return false;
        }
        float th = thickness.floatValue();
        int line = color.argb() | 0xFF000000;

        if (fill.isOn()) {
            int fc = ColorUtil.withAlpha(color.argb(), Math.round(fillAlpha.floatValue() / 100f * 255));
            RenderUtil.roundedRect(g, minX, minY, w, h, 0f, fc);
        }
        RenderUtil.roundedRect(g, minX, minY, w, th, 0f, line);
        RenderUtil.roundedRect(g, minX, maxY - th, w, th, 0f, line);
        RenderUtil.roundedRect(g, minX, minY, th, h, 0f, line);
        RenderUtil.roundedRect(g, maxX - th, minY, th, h, 0f, line);

        if (eyeLine.isOn() && projector.project((b.minX + b.maxX) / 2, b.minY + eyeHeight, (b.minZ + b.maxZ) / 2)) {
            RenderUtil.roundedRect(g, minX, (float) projector.y - th / 2f, w, th, 0f, eyeColor.argb() | 0xFF000000);
        }
        return true;
    }
}