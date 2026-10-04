package dev.elysium.visuals.client.alt;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.RenderUtil.Face;
import dev.elysium.visuals.client.gui.widget.TextField;
import dev.elysium.visuals.client.menu.ElysiumTitleScreen;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The Alt Manager: the account list shared with the launcher, offline and
 * Microsoft accounts, switching the game's account without a restart.
 */
public class AltManagerScreen extends Screen {
	private final Screen parent;
	private final TextField nickField;
	private final Map<String, SmoothValue> hovers = new HashMap<>();
	private final SmoothValue open = new SmoothValue(0f, 11f);
	private final SmoothValue msOverlay = new SmoothValue(0f, 13f);

	private record Hit(float x, float y, float w, float h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();

	private float scroll;
	private final SmoothValue scrollSmooth = new SmoothValue(0f, 16f);
	private AltAccount renaming;
	private String confirmDelete;
	private long confirmUntil;
	private String busyId;
	private String message;
	private boolean messageError;
	private long messageAt;
	private AltManager.MsLogin msLogin;

	public AltManagerScreen(Screen parent) {
		super(Component.literal("Аккаунты"));
		this.parent = parent;
		this.nickField = new TextField("Ник для офлайн-аккаунта", 16,
				c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_'), s -> submitNick());
	}

	@Override
	protected void init() {
		AltManager.reload();
	}

	private static Palette palette() {
		return ThemeManager.get().palette();
	}

	private int winW() {
		return Math.min(440, width - 32);
	}

	private int winH() {
		return Math.min(320, height - 24);
	}

	private int winX() {
		return (width - winW()) / 2;
	}

	private int winY() {
		return (height - winH()) / 2;
	}

	private void say(String text, boolean error) {
		message = text;
		messageError = error;
		messageAt = Util.getMillis();
	}

	private float hover(String key, boolean over) {
		return hovers.computeIfAbsent(key, k -> new SmoothValue(0f, 14f)).update(over ? 1f : 0f);
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	// ---------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------

	private void submitNick() {
		String nick = nickField.text().trim();
		String error;
		if (renaming != null) {
			error = AltManager.rename(renaming, nick);
			if (error == null) {
				say("Ник изменён на " + nick, false);
				renaming = null;
			}
		} else {
			error = AltManager.addOffline(nick);
			if (error == null) {
				say("Добавлен офлайн-аккаунт " + nick, false);
			}
		}
		if (error != null) {
			say(error, true);
		} else {
			nickField.setText("");
		}
	}

	private void login(AltAccount a) {
		if (busyId != null) {
			return;
		}
		busyId = a.id();
		CompletableFuture<Void> f = AltManager.login(a);
		f.whenCompleteAsync((v, t) -> {
			busyId = null;
			if (t != null) {
				Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
				say(cause instanceof MicrosoftAuth.AuthException ? cause.getMessage() : "Не удалось войти", true);
			} else {
				say("Вы вошли как " + Minecraft.getInstance().getUser().getName(), false);
			}
		}, Minecraft.getInstance());
	}

	private void delete(AltAccount a) {
		if (!a.id().equals(confirmDelete) || Util.getMillis() > confirmUntil) {
			confirmDelete = a.id();
			confirmUntil = Util.getMillis() + 3000;
			return;
		}
		confirmDelete = null;
		AltManager.remove(a);
		say("Аккаунт " + a.username() + " удалён", false);
	}

	private void startMicrosoft() {
		if (!MicrosoftAuth.configured()) {
			say("Вход Microsoft пока не настроен: нужен Client ID", true);
			return;
		}
		msLogin = AltManager.startMicrosoft(a -> say("Вы вошли как " + a.username(), false));
	}

	// ---------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (Minecraft.getInstance().level == null) {
			ElysiumTitleScreen.drawBackdrop(g, width, height);
		} else {
			super.extractBackground(g, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Palette p = palette();
		hits.clear();
		float o = open.update(1f);
		float ms = msOverlay.update(msLogin != null ? 1f : 0f);
		boolean modal = msLogin != null;
		int mx = modal ? -10000 : mouseX, my = modal ? -10000 : mouseY;
		try {
			RenderUtil.setAlpha(o);
			g.pose().pushMatrix();
			g.pose().translate(0, (1f - o) * 8f);
			window(g, p, mx, my);
			g.pose().popMatrix();
			if (ms > 0.004f) {
				RenderUtil.setAlpha(ms);
				msPanel(g, p, mouseX, mouseY);
			}
		} finally {
			RenderUtil.setAlpha(1f);
		}
		if (msLogin != null && msLogin.done() && msLogin.error() == null) {
			msLogin = null;
		}
	}

	private void window(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		int x = winX(), y = winY(), w = winW(), h = winH();
		RenderUtil.window(g, x, y, w, h, 14, p);

		// Header.
		RenderUtil.text(g, "Аккаунты", Face.TITLE, x + 16, y + 13, p.text());
		RenderUtil.text(g, "Общий список с Elysium Launcher", Face.SMALL, x + 16, y + 31, p.textFaint());
		float ch = hover("close", inside(mx, my, x + w - 28, y + 10, 18, 18));
		RenderUtil.glass(g, x + w - 28, y + 10, 18, 18, 6, p, ch, 0f);
		RenderUtil.cross(g, x + w - 19, y + 19, 3f, ColorUtil.mix(p.textDim(), p.text(), ch));
		hits.add(new Hit(x + w - 28, y + 10, 18, 18, this::onClose));

		// List.
		int listX = x + 12, listY = y + 46, listW = w - 24, listH = h - 46 - 76;
		List<AltAccount> list = AltManager.accounts();
		String current = AltManager.currentId();
		int cardH = 34, gap = 6;
		float max = Math.max(0, list.size() * (cardH + gap) - gap - listH);
		scroll = Math.max(0, Math.min(scroll, max));
		float sc = scrollSmooth.update(scroll);
		if (list.isEmpty()) {
			String empty = "Аккаунтов пока нет: добавьте офлайн-ник или войдите через Microsoft";
			RenderUtil.text(g, RenderUtil.ellipsize(empty, listW - 10, Face.REGULAR), Face.REGULAR,
					listX + (listW - Math.min(listW - 10, RenderUtil.width(empty))) / 2, listY + listH / 2 - 4, p.textFaint());
		}
		g.enableScissor(listX - 2, listY, listX + listW + 2, listY + listH);
		for (int i = 0; i < list.size(); i++) {
			AltAccount a = list.get(i);
			float cy = listY + i * (cardH + gap) - sc;
			if (cy + cardH < listY || cy > listY + listH) {
				continue;
			}
			boolean inList = my >= listY && my < listY + listH;
			card(g, p, a, listX, cy, listW - (max > 0 ? 6 : 0), cardH, inList ? mx : -10000, inList ? my : -10000, a.id().equals(current));
		}
		g.disableScissor();
		if (max > 0) {
			float frac = listH / (listH + max);
			float bh = Math.max(14, listH * frac);
			RenderUtil.roundedRect(g, listX + listW - 2, listY + (listH - bh) * (sc / max), 2, bh, 1, ColorUtil.mulAlpha(p.text(), 0.25f));
		}

		// Status line.
		int fy = y + h - 70;
		float age = (Util.getMillis() - messageAt) / 1000f;
		if (message != null && age < 5f) {
			float a = Math.min(1f, (5f - age) / 0.6f);
			int c = messageError ? Palette.DANGER : Palette.OK;
			RenderUtil.withAlpha(a, () -> {
				RenderUtil.glowDot(g, x + 18, fy + 5, 4, c);
				RenderUtil.text(g, RenderUtil.ellipsize(message, w - 44, Face.REGULAR), Face.REGULAR, x + 25, fy + 1, p.text());
			});
		} else if (!AltManager.canSwitch()) {
			RenderUtil.glowDot(g, x + 18, fy + 5, 4, Palette.WARN);
			RenderUtil.text(g, "Выйдите с сервера, чтобы сменить аккаунт", Face.REGULAR, x + 25, fy + 1, p.textDim());
		}

		// Nick row.
		int ry = y + h - 54;
		int addW = 90, diceW = 22;
		int fieldW = w - 24 - addW - diceW - 12 - (renaming != null ? 26 : 0);
		nickField.setBounds(x + 12, ry, fieldW, 18);
		nickField.render(g, p, mx, my, 0f);
		hits.add(new Hit(x + 12, ry, fieldW, 18, () -> nickField.setFocused(true)));
		float dx = x + 12 + fieldW + 6;
		float dh = hover("dice", inside(mx, my, dx, ry, diceW, 18));
		RenderUtil.glass(g, dx, ry, diceW, 18, 6, p, dh, 0f);
		dice(g, dx + diceW / 2f, ry + 9, ColorUtil.mix(p.textDim(), p.accent2(), dh));
		hits.add(new Hit(dx, ry, diceW, 18, () -> {
			nickField.setText(AltManager.randomNick());
			nickField.setFocused(true);
		}));
		float ax = dx + diceW + 6;
		String addLabel = renaming != null ? "Сохранить" : "Добавить";
		pill(g, p, "add", addLabel, ax, ry, addW, 18, mx, my, true, true, this::submitNick);
		if (renaming != null) {
			float cx = ax + addW + 6;
			float hh = hover("cancel_rename", inside(mx, my, cx, ry, 20, 18));
			RenderUtil.glass(g, cx, ry, 20, 18, 6, p, hh, 0f);
			RenderUtil.cross(g, cx + 10, ry + 9, 2.6f, ColorUtil.mix(p.textDim(), p.text(), hh));
			hits.add(new Hit(cx, ry, 20, 18, () -> {
				renaming = null;
				nickField.setText("");
			}));
		}

		// Microsoft.
		int my2 = y + h - 30;
		boolean configured = MicrosoftAuth.configured();
		float mh = hover("ms", configured && inside(mx, my, x + 12, my2, w - 24, 20));
		RenderUtil.withAlpha(configured ? 1f : 0.55f, () -> {
			RenderUtil.glass(g, x + 12, my2, w - 24, 20, 7, p, mh, 0.35f + 0.4f * mh);
			String label = configured ? "Войти через Microsoft" : "Вход через Microsoft: нужен Client ID";
			int lw = RenderUtil.width(label, Face.BOLD) + 16;
			float lx = x + 12 + (w - 24 - lw) / 2f;
			msLogo(g, lx + 4, my2 + 10, configured ? p.accent2() : p.textFaint());
			RenderUtil.text(g, label, Face.BOLD, Math.round(lx + 16), my2 + 6, p.text());
		});
		hits.add(new Hit(x + 12, my2, w - 24, 20, this::startMicrosoft));
	}

	private void card(GuiGraphicsExtractor g, Palette p, AltAccount a, float x, float y, float w, int h, int mx, int my, boolean current) {
		boolean over = inside(mx, my, x, y, w, h);
		float hv = hover("card:" + a.id(), over);
		RenderUtil.glass(g, x, y, w, h, 9, p, hv, current ? 0.7f : 0f);
		int head = 22;
		int hx = Math.round(x + 7), hy = Math.round(y + (h - head) / 2f);
		RenderUtil.softGlow(g, hx, hy, head, head, 3, 0x40000000, 3);
		SkinHeads.draw(g, a, hx, hy, head, ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * RenderUtil.alpha())));

		int tx = hx + head + 9;
		RenderUtil.textRaw(g, a.username(), tx, Math.round(y + 7), p.text());
		String kind = a.microsoft() ? "Microsoft" : "Офлайн";
		RenderUtil.caption(g, kind, tx, Math.round(y + 20), a.microsoft() ? p.accent2() : p.textFaint());
		if (current) {
			int kx = tx + RenderUtil.captionWidth(kind) + 10;
			RenderUtil.glowDot(g, kx, y + 23.5f, 4, Palette.OK);
			RenderUtil.caption(g, "Текущий", kx + 6, Math.round(y + 20), p.textDim());
		}

		// Actions, right-aligned.
		float bx = x + w - 8;
		float by = y + (h - 16) / 2f;
		boolean deleting = a.id().equals(confirmDelete) && Util.getMillis() < confirmUntil;
		String delLabel = deleting ? "Удалить?" : null;
		float delW = deleting ? RenderUtil.width(delLabel, Face.BOLD) + 16 : 18;
		bx -= delW;
		float dh = hover("del:" + a.id(), inside(mx, my, bx, by, delW, 16));
		int danger = Palette.DANGER;
		RenderUtil.glass(g, bx, by, delW, 16, 6, p, dh, 0f);
		if (deleting) {
			RenderUtil.roundedRect(g, bx, by, delW, 16, 6, ColorUtil.mulAlpha(danger, 0.25f));
			RenderUtil.text(g, delLabel, Face.BOLD, Math.round(bx + 8), Math.round(by + 4), danger);
		} else {
			trash(g, bx + 9, by + 8, ColorUtil.mix(p.textFaint(), danger, dh));
		}
		hits.add(new Hit(bx, by, delW, 16, () -> delete(a)));

		if (!a.microsoft()) {
			bx -= 6 + 18;
			float eh = hover("edit:" + a.id(), inside(mx, my, bx, by, 18, 16));
			RenderUtil.glass(g, bx, by, 18, 16, 6, p, eh, 0f);
			pencil(g, bx + 9, by + 8, ColorUtil.mix(p.textFaint(), p.accent2(), eh));
			float ex = bx;
			hits.add(new Hit(ex, by, 18, 16, () -> {
				renaming = a;
				nickField.setText(a.username());
				nickField.setFocused(true);
			}));
		}

		if (!current) {
			boolean busy = a.id().equals(busyId);
			String label = busy ? "Входим…" : "Войти";
			float lw = RenderUtil.width(label, Face.BOLD) + 18;
			bx -= 6 + lw;
			pill(g, p, "login:" + a.id(), label, bx, by, lw, 16, mx, my, AltManager.canSwitch() && busyId == null, true, () -> login(a));
		}
	}

	/** A glass button with a label; accent-tinted when {@code primary}. */
	private void pill(GuiGraphicsExtractor g, Palette p, String key, String label, float x, float y, float w, float h,
					  int mx, int my, boolean enabled, boolean primary, Runnable action) {
		float hv = hover(key, enabled && inside(mx, my, x, y, w, h));
		RenderUtil.withAlpha(enabled ? 1f : 0.5f, () -> {
			if (primary && hv > 0.01f) {
				RenderUtil.softGlow(g, x, y, w, h, 6, ColorUtil.mulAlpha(p.accent(), 0.3f * hv), 4);
			}
			RenderUtil.glass(g, x, y, w, h, 6, p, hv, primary ? 0.45f + 0.45f * hv : 0f);
			RenderUtil.text(g, label, Face.BOLD, Math.round(x + (w - RenderUtil.width(label, Face.BOLD)) / 2f), Math.round(y + (h - 9) / 2f + 1), p.text());
		});
		if (enabled) {
			hits.add(new Hit(x, y, w, h, action));
		}
	}

	private void msPanel(GuiGraphicsExtractor g, Palette p, int mx, int my) {
		AltManager.MsLogin login = msLogin;
		RenderUtil.rect(g, 0, 0, width, height, ColorUtil.withAlpha(p.bgBottom(), 0x99));
		int w = Math.min(280, width - 40), h = 170;
		int x = (width - w) / 2, y = (height - h) / 2;
		RenderUtil.window(g, x, y, w, h, 14, p);
		RenderUtil.text(g, "Вход через Microsoft", Face.TITLE, x + 16, y + 14, p.text());
		if (login == null) {
			return;
		}
		MicrosoftAuth.DeviceCode code = login.code();
		if (login.error() != null) {
			RenderUtil.glowDot(g, x + 20, y + 48, 4, Palette.DANGER);
			RenderUtil.text(g, RenderUtil.ellipsize(login.error(), w - 44, Face.REGULAR), Face.REGULAR, x + 28, y + 44, p.text());
			pill(g, p, "ms_close", "Закрыть", x + w - 96, y + h - 30, 80, 18, mx, my, true, false, () -> msLogin = null);
			return;
		}
		RenderUtil.text(g, "1. Откройте страницу и войдите в аккаунт", Face.REGULAR, x + 16, y + 38, p.textDim());
		RenderUtil.text(g, "2. Введите этот код:", Face.REGULAR, x + 16, y + 51, p.textDim());
		if (code != null) {
			String c = code.userCode();
			float scale = 2f;
			int cw = Math.round(RenderUtil.width(c, Face.TITLE) * scale);
			RenderUtil.well(g, x + 16, y + 66, w - 32, 34, 8, p, 0f);
			g.pose().pushMatrix();
			g.pose().translate(x + (w - cw) / 2f, y + 72);
			g.pose().scale(scale, scale);
			RenderUtil.text(g, c, Face.TITLE, 0, 0, p.text());
			g.pose().popMatrix();
			pill(g, p, "ms_open", "Открыть страницу", x + 16, y + 108, 130, 18, mx, my, true, true, () -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(c);
				Util.getPlatform().openUri(code.verificationUri());
				say("Код скопирован в буфер обмена", false);
			});
			pill(g, p, "ms_copy", "Копировать код", x + 152, y + 108, w - 168, 18, mx, my, true, false, () -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(c);
				say("Код скопирован", false);
			});
		}
		// Spinner dots + status.
		double t = Util.getMillis() / 1000.0;
		for (int d = 0; d < 3; d++) {
			float a = 0.35f + 0.65f * (float) Math.max(0, Math.sin(t * 5 - d * 0.7));
			RenderUtil.roundedRect(g, x + 18 + d * 5, y + h - 23, 2.4f, 2.4f, 1.2f, ColorUtil.mulAlpha(p.accent2(), a));
		}
		RenderUtil.text(g, RenderUtil.ellipsize(login.status(), w - 140, Face.REGULAR), Face.REGULAR, x + 38, y + h - 26, p.textDim());
		pill(g, p, "ms_cancel", "Отмена", x + w - 96, y + h - 30, 80, 18, mx, my, true, false, () -> {
			login.cancel();
			msLogin = null;
		});
	}

	// ---------------------------------------------------------------------
	// Icons
	// ---------------------------------------------------------------------

	private static void dice(GuiGraphicsExtractor g, float cx, float cy, int c) {
		RenderUtil.roundedOutline(g, cx - 4.5f, cy - 4.5f, 9, 9, 2, 1.1f, c);
		RenderUtil.roundedRect(g, cx - 2.9f, cy - 2.9f, 1.8f, 1.8f, 0.9f, c);
		RenderUtil.roundedRect(g, cx - 0.9f, cy - 0.9f, 1.8f, 1.8f, 0.9f, c);
		RenderUtil.roundedRect(g, cx + 1.1f, cy + 1.1f, 1.8f, 1.8f, 0.9f, c);
	}

	private static void trash(GuiGraphicsExtractor g, float cx, float cy, int c) {
		RenderUtil.rect(g, cx - 3.5f, cy - 3.2f, 7, 1.1f, c);
		RenderUtil.rect(g, cx - 1.2f, cy - 4.4f, 2.4f, 1.1f, c);
		RenderUtil.roundedOutline(g, cx - 2.7f, cy - 2, 5.4f, 6.2f, 1, 1.1f, c);
	}

	private static void pencil(GuiGraphicsExtractor g, float cx, float cy, int c) {
		RenderUtil.stroke(g, cx - 3, cy + 3, cx + 2.6f, cy - 2.6f, 1.6f, c);
		RenderUtil.roundedRect(g, cx - 3.8f, cy + 2.6f, 1.6f, 1.2f, 0.4f, c);
	}

	private static void msLogo(GuiGraphicsExtractor g, float cx, float cy, int c) {
		float s = 3.2f, gap = 0.8f;
		RenderUtil.rect(g, cx - s - gap / 2, cy - s - gap / 2, s, s, c);
		RenderUtil.rect(g, cx + gap / 2, cy - s - gap / 2, s, s, ColorUtil.mulAlpha(c, 0.75f));
		RenderUtil.rect(g, cx - s - gap / 2, cy + gap / 2, s, s, ColorUtil.mulAlpha(c, 0.75f));
		RenderUtil.rect(g, cx + gap / 2, cy + gap / 2, s, s, ColorUtil.mulAlpha(c, 0.55f));
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return false;
		}
		boolean wasFocused = nickField.isFocused();
		nickField.setFocused(false);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				if (msLogin != null && !isModalHit(hit)) {
					continue;
				}
				click();
				hit.action().run();
				return true;
			}
		}
		if (wasFocused && renaming == null) {
			nickField.setFocused(false);
		}
		return true;
	}

	/** While the Microsoft panel is open only its own buttons react. */
	private boolean isModalHit(Hit hit) {
		int w = Math.min(280, width - 40), h = 170;
		int x = (width - w) / 2, y = (height - h) / 2;
		return hit.x() >= x && hit.x() < x + w && hit.y() >= y && hit.y() < y + h;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll - (float) scrollY * 20f);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (msLogin != null) {
				msLogin.cancel();
				msLogin = null;
				return true;
			}
			if (renaming != null) {
				renaming = null;
				nickField.setText("");
				return true;
			}
			onClose();
			return true;
		}
		if (nickField.isFocused() && nickField.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (nickField.isFocused()) {
			return nickField.charTyped(event);
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		if (msLogin != null) {
			msLogin.cancel();
		}
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public void removed() {
		if (msLogin != null) {
			msLogin.cancel();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
