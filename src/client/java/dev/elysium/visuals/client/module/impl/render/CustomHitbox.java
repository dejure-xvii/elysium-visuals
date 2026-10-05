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

/** Свой хитбокс: цвет, толщина линий, заливка и отдельная линия на уровне глаз. */
public class CustomHitbox extends Module {
    private final BooleanSetting players = add(new BooleanSetting("players", "Игроки", true));
    private final BooleanSetting mobs = add(new BooleanSetting("mobs", "Мобы", false));
    private final ColorSetting color = add(new ColorSetting("color", "Цвет хитбокса", 0xFFB48CFF));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Толщина линий", 1.5, 0.5, 4, 0.5, " px"));
    private final BooleanSetting fill = add(new BooleanSetting("fill", "Заливка", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("fill_alpha", "Прозрачность заливки", 25, 5, 100, 5, " %")).visibleWhen(fill::isOn);
    private final BooleanSetting eyeLine = add(new BooleanSetting("eye_line", "Линия глаз", true));
    private final ColorSetting eyeColor = add(new ColorSetting("eye_color", "Цвет линии глаз", 0xFFFF5C7A)).visibleWhen(eyeLine::isOn);

    private final ScreenProjector projector = new ScreenProjector();
    private final float[] cx = new float[8], cy = new float[8];

    public CustomHitbox() {
        super("custom_hitbox", "CustomHitbox", "Хитбокс с настройкой цвета, толщины, заливки и линии глаз", Category.RENDER);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        projector.begin(g.guiWidth(), g.guiHeight());
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || e.distanceToSqr(mc.player) > 64 * 64) {
                continue;
            }
            boolean isPlayer = e instanceof Player;
            if (isPlayer ? !players.isOn() : !mobs.isOn()) {
                continue;
            }
            Vec3 pos = e.getPosition(partialTick);
            AABB b = e.getBoundingBox().move(pos.subtract(e.position()));
            drawBox(g, b, e.getEyeHeight());
        }
    }

    private void drawBox(GuiGraphicsExtractor g, AABB b, float eyeHeight) {
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!projector.project(x, y, z)) {
                return; // угол за экраном, пропускаем сущность целиком
            }
            cx[i] = (float) projector.x;
            cy[i] = (float) projector.y;
        }
        float th = thickness.floatValue();
        int line = color.argb() | 0xFF000000;

        if (fill.isOn()) {
            int fc = ColorUtil.withAlpha(color.argb(), Math.round(fillAlpha.floatValue() / 100f * 255));
            for (int axis = 0; axis < 3; axis++) {
                int a = 1 << axis, u = 1 << ((axis + 1) % 3), v = 1 << ((axis + 2) % 3);
                for (int side = 0; side < 2; side++) {
                    int c0 = side == 0 ? 0 : a;
                    fillQuad(g, c0, c0 | u, c0 | u | v, c0 | v, fc);
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) == 0) {
                    line(g, cx[i], cy[i], cx[i | bit], cy[i | bit], th, line);
                }
            }
        }

        if (eyeLine.isOn()) {
            double y = b.minY + eyeHeight;
            double[][] p = {{b.minX, b.minZ}, {b.maxX, b.minZ}, {b.maxX, b.maxZ}, {b.minX, b.maxZ}};
            float[] ex = new float[4], ey = new float[4];
            for (int i = 0; i < 4; i++) {
                if (!projector.project(p[i][0], y, p[i][1])) {
                    return;
                }
                ex[i] = (float) projector.x;
                ey[i] = (float) projector.y;
            }
            int ec = eyeColor.argb() | 0xFF000000;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(g, ex[i], ey[i], ex[j], ey[j], th, ec);
            }
        }
    }

    /** Заливка грани полосами от ребра c0-c1 к ребру c3-c2. */
    private void fillQuad(GuiGraphicsExtractor g, int c0, int c1, int c2, int c3, int argb) {
        int strips = 10;
        float size = Math.max(2f, dist(cx[c0], cy[c0], cx[c1], cy[c1]) / strips + 1f);
        for (int k = 0; k <= strips; k++) {
            float t = k / (float) strips;
            float x1 = cx[c0] + (cx[c1] - cx[c0]) * t, y1 = cy[c0] + (cy[c1] - cy[c0]) * t;
            float x2 = cx[c3] + (cx[c2] - cx[c3]) * t, y2 = cy[c3] + (cy[c2] - cy[c3]) * t;
            line(g, x1, y1, x2, y2, size, argb);
        }
    }

    /** Линия из квадратиков: у GUI-рендера нет примитива линии. */
    private void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float th, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        int n = Math.min(500, Math.max(1, (int) Math.ceil(dist(x1, y1, x2, y2) / Math.max(1f, th * 0.9f))));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            RenderUtil.roundedRect(g, x1 + dx * t - th / 2f, y1 + dy * t - th / 2f, th, th, th / 2f, argb);
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}