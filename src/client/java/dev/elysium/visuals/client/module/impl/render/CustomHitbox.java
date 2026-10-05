package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.ColorSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.GlowGeometry;
import dev.elysium.visuals.client.render.WorldPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Настоящий 3D-хитбокс в мировом проходе (как BlockOverlay): контур из 12 рёбер,
 * необязательная заливка граней и линия на уровне глаз. Цвет, толщина, дальность — в настройках.
 */
public class CustomHitbox extends Module {
    /** Грани чуть выносим наружу, чтобы они не мерцали о сам бокс. */
    private static final double INFLATE = 0.002;

    private final BooleanSetting players = add(new BooleanSetting("players", "Игроки", true));
    private final BooleanSetting mobs = add(new BooleanSetting("mobs", "Мобы", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("through_walls", "Сквозь стены", true));
    private final NumberSetting range = add(new NumberSetting("range", "Дальность", 64, 8, 128, 4, " бл."));
    private final ColorSetting color = add(new ColorSetting("color", "Цвет хитбокса", 0xFFB48CFF));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Толщина линий", 1.5, 0.5, 6, 0.25));
    private final BooleanSetting fill = add(new BooleanSetting("fill", "Заливка", false));
    private final NumberSetting fillAlpha = add(new NumberSetting("fill_alpha", "Прозрачность заливки", 0.2, 0.05, 1, 0.05))
            .visibleWhen(fill::isOn);
    private final BooleanSetting eyeLine = add(new BooleanSetting("eye_line", "Линия глаз", true));
    private final ColorSetting eyeColor = add(new ColorSetting("eye_color", "Цвет линии глаз", 0xFFFF5C7A))
            .visibleWhen(eyeLine::isOn);

    /** Переиспользуемые списки: боксы сущностей и высота глаз для каждого. */
    private final List<AABB> boxes = new ArrayList<>();
    private final List<Double> eyeYs = new ArrayList<>();

    public CustomHitbox() {
        super("custom_hitbox", "CustomHitbox", "3D-хитбокс с настройкой цвета, толщины, заливки и линии глаз", Category.RENDER);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
    }

    private void render(LevelRenderContext ctx) {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double maxSq = range.get() * range.get();
        boxes.clear();
        eyeYs.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || e.isRemoved() || e.isSpectator()
                    || e.distanceToSqr(mc.player) > maxSq) {
                continue;
            }
            if (e instanceof Player ? !players.isOn() : !mobs.isOn()) {
                continue;
            }
            Vec3 interp = e.getPosition(partial);
            AABB box = e.getBoundingBox().move(interp.subtract(e.position()));
            boxes.add(box);
            eyeYs.add(box.minY + e.getEyeHeight());
        }
        if (boxes.isEmpty()) {
            return;
        }

        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        PoseStack poseStack = ctx.poseStack();
        boolean xray = throughWalls.isOn();
        int line = color.argb() | 0xFF000000;
        int eye = eyeColor.argb() | 0xFF000000;
        float thick = thickness.floatValue();
        boolean drawEye = eyeLine.isOn();

        if (fill.isOn()) {
            int fillColor = GlowGeometry.scaleAlpha(line, fillAlpha.floatValue());
            ctx.submitNodeCollector().submitCustomGeometry(poseStack, WorldPipelines.plasma(xray), (pose, vc) -> {
                for (AABB box : boxes) {
                    fillBox(pose, vc, box.inflate(INFLATE), cam, fillColor);
                }
            });
        }

        ctx.submitNodeCollector().submitCustomGeometry(poseStack, WorldPipelines.solid(xray), (pose, vc) -> {
            for (int i = 0; i < boxes.size(); i++) {
                AABB box = boxes.get(i).inflate(INFLATE * 2);
                // Постоянная толщина на экране: растёт с расстоянием до сущности.
                double dx = (box.minX + box.maxX) / 2 - cam.x, dy = (box.minY + box.maxY) / 2 - cam.y, dz = (box.minZ + box.maxZ) / 2 - cam.z;
                float w = thick * 0.0025f * (float) Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                outlineBox(pose, vc, box, cam, w, line);
                if (drawEye) {
                    eyeRing(pose, vc, box, eyeYs.get(i), cam, w, eye);
                }
            }
        });
    }

    /** Заливка: 6 граней однотонным цветом (тем же приёмом, что в BlockOverlay, но без движения плазмы). */
    private static void fillBox(PoseStack.Pose pose, VertexConsumer vc, AABB box, Vec3 cam, int color) {
        float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
        float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
        face(pose, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color, false);
        face(pose, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color, true);
        face(pose, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color, true);
        face(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color, false);
        face(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color, false);
        face(pose, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color, true);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int color, boolean swap) {
        // UV и нормаль нужны формату plasma-пайплайна; для ровной заливки их значения не важны.
        vc.addVertex(pose, ax, ay, az).setUv(0, 0).setColor(color).setNormal(0, 0, 0);
        vc.addVertex(pose, bx, by, bz).setUv(swap ? 0 : 1, swap ? 1 : 0).setColor(color).setNormal(0, 0, 0);
        vc.addVertex(pose, cx, cy, cz).setUv(1, 1).setColor(color).setNormal(0, 0, 0);
        vc.addVertex(pose, dx, dy, dz).setUv(swap ? 1 : 0, swap ? 0 : 1).setColor(color).setNormal(0, 0, 0);
    }

    private static void outlineBox(PoseStack.Pose pose, VertexConsumer vc, AABB box, Vec3 cam, float w, int c) {
        float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
        float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);
        GlowGeometry.glowLine(pose, vc, x0, y0, z0, x1, y0, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y0, z0, x1, y0, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y0, z1, x0, y0, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y0, z1, x0, y0, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y1, z0, x1, y1, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y1, z0, x1, y1, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y1, z1, x0, y1, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y1, z1, x0, y1, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y0, z0, x0, y1, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y0, z0, x1, y1, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y0, z1, x1, y1, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y0, z1, x0, y1, z1, w, 1, c, c);
    }

    /** Горизонтальный контур вокруг бокса на высоте глаз. */
    private static void eyeRing(PoseStack.Pose pose, VertexConsumer vc, AABB box, double eyeY, Vec3 cam, float w, int c) {
        float x0 = (float) (box.minX - cam.x), x1 = (float) (box.maxX - cam.x);
        float z0 = (float) (box.minZ - cam.z), z1 = (float) (box.maxZ - cam.z);
        float y = (float) (eyeY - cam.y);
        GlowGeometry.glowLine(pose, vc, x0, y, z0, x1, y, z0, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y, z0, x1, y, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x1, y, z1, x0, y, z1, w, 1, c, c);
        GlowGeometry.glowLine(pose, vc, x0, y, z1, x0, y, z0, w, 1, c, c);
    }
}
