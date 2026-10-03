package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.notify.Notifications;
import dev.elysium.visuals.client.notify.Toast;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * Small glass capsules centered just below the crosshair: a status dot and a
 * line of text. New ones appear on top (fade in, scaling from 0.9) and push
 * the older ones down; at most {@value #MAX} at once. They have a fixed place,
 * only the vertical offset is configurable.
 */
public class NotificationsModule extends Module {
	private static final int MAX = 3;
	private static final int H = 13;
	private static final int GAP = 3;

	private final MultiSelectSetting types = add(new MultiSelectSetting("types", "Показывать",
			List.of(
					option("modules", "Вкл/выкл модулей"),
					option("config", "Загрузка конфига"),
					option("pickup", "Подобранные предметы"),
					option("messages", "Сообщения модулей")),
			Set.of("modules", "config", "pickup", "messages")));
	private final NumberSetting duration = add(new NumberSetting("show_time", "Время показа", 1.5, 0.5, 5, 0.25, " с"));
	private final NumberSetting offset = add(new NumberSetting("offset", "Смещение под прицелом", 16, 4, 120, 1, " px"));

	private final List<Toast> toasts = new ArrayList<>();

	public NotificationsModule() {
		super("notifications", "Notifications", "Компактные уведомления под прицелом", Category.UTILS);
		enableByDefault();
		addHud(new Element());
		ModuleManager.get().onToggle((m, on) -> {
			if (m != this) {
				Notifications.toggled(m.name(), on);
			}
		});
	}

	public boolean shows(Notifications.Type type) {
		return types.isSelected(type.name().toLowerCase());
	}

	public void push(Toast toast) {
		toasts.add(0, toast);
		// Too many: the oldest fade out early.
		int alive = 0;
		for (Toast t : toasts) {
			if (!t.isLeaving() && ++alive > MAX) {
				t.leave();
			}
		}
	}

	@Override
	protected void onDisable() {
		toasts.clear();
	}

	private static int capsuleWidth(Toast t) {
		int w = 7 + 4 + 5 + RenderUtil.width(t.title) + 7;
		if (!t.text.isEmpty()) {
			w += 4 + RenderUtil.width(t.text);
		}
		return w;
	}

	private final class Element extends HudElement {
		private final Toast sample = new Toast("Notifications", "пример уведомления", ItemStack.EMPTY, Notifications.Tone.ACCENT);

		Element() {
			super("toasts", "Уведомления", Anchor.CROSSHAIR);
		}

		@Override
		public boolean isDraggable() {
			return false;
		}

		@Override
		public boolean hasContent() {
			return !toasts.isEmpty();
		}

		@Override
		protected void measure(boolean preview) {
			int w = 60;
			for (Toast t : toasts) {
				w = Math.max(w, capsuleWidth(t));
			}
			if (toasts.isEmpty() && preview) {
				w = Math.max(w, capsuleWidth(sample));
			}
			width = w;
			height = MAX * (H + GAP) - GAP;
		}

		@Override
		protected void placeFixed(int screenW, int screenH) {
			x = (screenW - width) / 2;
			y = Math.min(screenH / 2 + offset.intValue(), screenH - height);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
			float cx = x + width / 2f;
			if (toasts.isEmpty() && preview) {
				drawCapsule(g, p, sample, cx, y, 1f, 1f);
				return;
			}
			long now = Util.getMillis();
			long life = Math.round(duration.get() * 1000);
			int index = 0;
			for (int i = 0; i < toasts.size(); i++) {
				Toast t = toasts.get(i);
				if (now - t.createdMs > life) {
					t.leave();
				}
				if (t.isGone()) {
					toasts.remove(i--);
					continue;
				}
				float target = index * (H + GAP);
				if (t.offset.get() < 0) {
					t.offset.set(target);
				}
				float off = t.offset.update(target);
				drawCapsule(g, p, t, cx, y + Math.round(off), t.alpha(), t.scale());
				if (!t.isLeaving()) {
					index++;
				}
			}
		}

		/** One capsule centered on {@code cx}: glass, a status dot, the title and the muted text. */
		private void drawCapsule(GuiGraphicsExtractor g, Palette p, Toast t, float cx, int ty, float alpha, float scale) {
			int w = capsuleWidth(t);
			int tx = Math.round(cx - w / 2f);
			boolean scaled = scale < 0.999f;
			if (scaled) {
				g.pose().pushMatrix();
				g.pose().translate(cx, ty + H / 2f);
				g.pose().scale(scale, scale);
				g.pose().translate(-cx, -(ty + H / 2f));
			}
			RenderUtil.withAlpha(alpha, () -> {
				float r = H / 2f;
				// Translucent glass, so the capsule never hides what is behind the crosshair.
				RenderUtil.softGlow(g, tx, ty, w, H, r, 0x22000000, 4);
				int base = Math.min(ColorUtil.alpha(p.background()), 0x8C);
				RenderUtil.roundedRect(g, tx, ty, w, H, r, ColorUtil.withAlpha(ColorUtil.mixRgb(p.bgTop(), 0xFF000000, 0.25f), base));
				RenderUtil.roundedGradient(g, tx, ty, w, H, r, ColorUtil.withAlpha(p.text(), 0x12), ColorUtil.withAlpha(p.text(), 0x05));
				RenderUtil.roundedOutline(g, tx, ty, w, H, r, 0, p.border());

				int dot = switch (t.tone) {
					case POSITIVE -> Palette.OK;
					case MUTED -> p.textFaint();
					default -> p.accent2();
				};
				RenderUtil.glowDot(g, tx + 9, ty + H / 2f, 4, dot);
				int textX = tx + 7 + 4 + 5;
				int textY = ty + (H - 8) / 2;
				RenderUtil.text(g, t.title, RenderUtil.Face.REGULAR, textX, textY,
						t.tone == Notifications.Tone.MUTED ? p.textDim() : p.text());
				if (!t.text.isEmpty()) {
					RenderUtil.text(g, t.text, RenderUtil.Face.REGULAR, textX + RenderUtil.width(t.title) + 4, textY, p.textDim());
				}
			});
			if (scaled) {
				g.pose().popMatrix();
			}
		}
	}
}
