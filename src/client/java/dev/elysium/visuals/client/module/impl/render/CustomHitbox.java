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

/** 3D-хитбокс: настоящий параллелепипед (12 рёбер), заливка видимых граней и контур на уровне глаз. */
public class CustomHitbox extends Module {
    private static final int MAX_ENTITIES = 24;

    /** Рёбра куба: пары углов, различающихся ровно одним битом (bit0=X, bit1=Y, bit2=Z). */
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    /** Грани: 4 угла по периметру. Порядок: -X, +X, -Y, +Y, -Z, +Z. */
    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 5, 7, 6}
    };

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
    private final float[] sx = new float[8];
    private final float[] sy = new float[8];
    private final float[] ex = new float[4];
    private final float[] ey = new float[4];

    public CustomHitbox() {
        super("custom_hitbox", "CustomHitbox", "3D-хитбокс с настройкой цвета, толщины, заливки и линии глаз", Category.RENDER);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double max = range.floatValue() * range.floatValue();
        Vec3 cam = mc.player.getEyePosition(partialTick);
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
            if (drawBox(g, b, e.getEyeHeight(), cam) && ++drawn >= MAX_ENTITIES) {
                break;
            }
        }
    }

    private boolean drawBox(GuiGraphicsExtractor g, AABB b, float eyeHeight, Vec3 cam) {
        // Проецируем 8 углов; если хоть один за камерой — пропускаем сущность.
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!projector.project(x, y, z)) {
                return false;
            }
            sx[i] = (float) projector.x;
            sy[i] = (float) projector.y;
        }

        float th = thickness.floatValue();
        int line = color.argb() | 0xFF000000;

        // Заливка только видимых (лицевых) граней — они не перекрываются на экране.
        if (fill.isOn()) {
            int fc = ColorUtil.withAlpha(color.argb(), Math.round(fillAlpha.floatValue() / 100f * 255));
            boolean[] visible = {
                    cam.x < b.minX, cam.x > b.maxX,
                    cam.y < b.minY, cam.y > b.maxY,
                    cam.z < b.minZ, cam.z > b.maxZ
            };
            for (int f = 0; f < 6; f++) {
                if (visible[f]) {
                    fillQuad(g, FACES[f], fc);
                }
            }
        }

        // 12 рёбер параллелепипеда.
        for (int[] edge : EDGES) {
            drawLine(g, sx[edge[0]], sy[edge[0]], sx[edge[1]], sy[edge[1]], th, line);
        }

        // Линия глаз: горизонтальный контур вокруг бокса на высоте глаз.
        if (eyeLine.isOn()) {
            double y = b.minY + eyeHeight;
            boolean ok = true;
            for (int i = 0; i < 4 && ok; i++) {
                double x = (i & 1) == 0 ? b.minX : b.maxX;
                double z = (i & 2) == 0 ? b.minZ : b.maxZ;
                ok = projector.project(x, y, z);
                ex[i] = (float) projector.x;
                ey[i] = (float) projector.y;
            }
            if (ok) {
                int ec = eyeColor.argb() | 0xFF000000;
                // углы: 0=(minX,minZ) 1=(maxX,minZ) 2=(minX,maxZ) 3=(maxX,maxZ)
                drawLine(g, ex[0], ey[0], ex[1], ey[1], th, ec);
                drawLine(g, ex[1], ey[1], ex[3], ey[3], th, ec);
                drawLine(g, ex[3], ey[3], ex[2], ey[2], th, ec);
                drawLine(g, ex[2], ey[2], ex[0], ey[0], th, ec);
            }
        }
        return true;
    }

    /** Линия толщиной th из квадратиков, идущих вдоль отрезка. */
    private void drawLine(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float th, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float half = th / 2f;
        // Горизонтальные и вертикальные рёбра — один прямоугольник вместо цепочки.
        if (Math.abs(dy) < 0.5f) {
            RenderUtil.roundedRect(g, Math.min(x1, x2) - half, y1 - half, Math.abs(dx) + th, th, 0f, argb);
            return;
        }
        if (Math.abs(dx) < 0.5f) {
            RenderUtil.roundedRect(g, x1 - half, Math.min(y1, y2) - half, th, Math.abs(dy) + th, 0f, argb);
            return;
        }
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        // Шаг = толщина (квадраты касаются), число точек ограничено сверху.
        int n = Math.min(80, Math.max(1, (int) Math.ceil(len / Math.max(1f, th))));
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            RenderUtil.roundedRect(g, x1 + dx * t - half, y1 + dy * t - half, th, th, 0f, argb);
        }
    }

    /** Заливка выпуклого четырёхугольника горизонтальными полосками высотой 1px. */
    private void fillQuad(GuiGraphicsExtractor g, int[] q, int argb) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i : q) {
            minY = Math.min(minY, sy[i]);
            maxY = Math.max(maxY, sy[i]);
        }
        // Не больше ~32 полосок на грань: высота полоски растёт с размером грани.
        float rowH = Math.max(1f, (maxY - minY) / 32f);
        for (float y = (float) Math.floor(minY); y <= maxY; y += rowH) {
            float yc = y + rowH * 0.5f;
            float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                int a = q[k], c = q[(k + 1) & 3];
                float ya = sy[a], yb = sy[c];
                if ((yc < Math.min(ya, yb)) || (yc > Math.max(ya, yb)) || ya == yb) {
                    continue;
                }
                float x = sx[a] + (yc - ya) / (yb - ya) * (sx[c] - sx[a]);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            if (right > left) {
                RenderUtil.roundedRect(g, left, y, right - left, rowH, 0f, argb);
            }
        }
    }
}