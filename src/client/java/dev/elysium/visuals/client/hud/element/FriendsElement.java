package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.AnimatedRows;
import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Function;

/** Friends that are online on the current server, each with a green status dot. */
public class FriendsElement extends HudElement {
	private static final int ROW_H = 13;
	private static final String CAPTION = "Друзья";

	private final AnimatedRows<String> rows = new AnimatedRows<>(Function.identity());
	private String right = "";

	public FriendsElement() {
		super("friends", "Список друзей", Anchor.TOP_LEFT);
	}

	@Override
	public boolean hasContent() {
		return !FriendsModule.onlineFriends().isEmpty();
	}

	@Override
	protected void measure(boolean preview) {
		List<String> current = FriendsModule.onlineFriends();
		if (!current.isEmpty() || preview) {
			rows.update(current);
			right = current.size() + " онлайн";
		}
		int w = Math.max(104, HudStyle.headerWidth(CAPTION, right));
		if (rows.isEmpty()) {
			w = Math.max(w, HudStyle.PAD * 2 + RenderUtil.width(emptyText()));
		}
		for (AnimatedRows.Row<String> r : rows.rows()) {
			w = Math.max(w, HudStyle.PAD * 2 + 10 + RenderUtil.width(r.value()));
		}
		width = animateWidth(w);
		height = HudStyle.TITLE_H + 3 + Math.round(Math.max(ROW_H, rows.height(ROW_H))) + 4;
	}

	private static String emptyText() {
		return FriendsModule.friendCount() == 0 ? "Добавьте друзей: .friend add" : "Нет друзей онлайн";
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Palette p, boolean preview) {
		HudStyle.panel(g, p, x, y, width, height);
		int top = HudStyle.header(g, p, HudIcon.FRIENDS, CAPTION, right, x, y, width);
		float empty = 1f - Math.min(1f, rows.height(ROW_H) / ROW_H);
		if (empty > 0.01f) {
			RenderUtil.withAlpha(empty, () -> RenderUtil.text(g, null, emptyText(), x + HudStyle.PAD, top + 3, p.textDim()));
		}
		float ry = top;
		for (AnimatedRows.Row<String> r : rows.rows()) {
			int rowY = Math.round(ry);
			RenderUtil.withAlpha(r.shown(), () -> {
				HudStyle.dot(g, x + HudStyle.PAD + 2.5f, rowY + 6.5f, Palette.OK);
				RenderUtil.text(g, null, r.value(), x + HudStyle.PAD + 9, rowY + 3, p.text());
			});
			ry += ROW_H * r.open();
		}
	}
}
