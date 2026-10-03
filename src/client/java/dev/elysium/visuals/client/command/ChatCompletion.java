package dev.elysium.visuals.client.command;

import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudStyle;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.theme.ThemeManager;
import dev.elysium.visuals.client.util.ColorUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Tab completion for chat commands: Tab / Shift+Tab cycle through the
 * candidates, a themed list above the input shows them, and the rest of the
 * first match is hinted in gray after the cursor.
 */
final class ChatCompletion {
	private static final int MAX_ROWS = 8;
	private static final int ROW_H = 11;

	private final EditBox input;
	/** Completion for {@link #cachedFor}, recomputed only when the text changes (config names come from disk). */
	private String cachedFor;
	private CommandManager.Completion cached;
	/** While cycling: the text we produced, the candidates and the current one. */
	private String applied;
	private CommandManager.Completion cycle;
	private int index;
	private boolean hinting;

	private ChatCompletion(EditBox input) {
		this.input = input;
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			EditBox input = findInput(screen);
			if (input == null) {
				return;
			}
			ChatCompletion completion = new ChatCompletion(input);
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
					event.key() != GLFW.GLFW_KEY_TAB || !completion.tab(Minecraft.getInstance().hasShiftDown()));
			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> completion.render(g));
		});
	}

	private static EditBox findInput(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof EditBox box) {
				return box;
			}
		}
		return null;
	}

	private CommandManager.Completion current() {
		String value = input.getValue();
		if (!value.equals(cachedFor)) {
			cachedFor = value;
			cached = CommandManager.get().complete(value);
		}
		return cached;
	}

	private static boolean applies(String value) {
		return !Panic.isActive() && value.startsWith(CommandManager.PREFIX)
				&& (value.equals(CommandManager.PREFIX) || CommandManager.isCommand(value));
	}

	/** Handles Tab; returns true if it was used for our completion (so vanilla doesn't see it). */
	private boolean tab(boolean backwards) {
		String value = input.getValue();
		if (!applies(value)) {
			return false;
		}
		if (cycle == null || !value.equals(applied)) {
			cycle = current();
			index = backwards ? cycle.candidates().size() - 1 : 0;
		} else {
			int n = cycle.candidates().size();
			index = (index + (backwards ? -1 : 1) + n) % n;
		}
		List<String> candidates = cycle.candidates();
		if (candidates.isEmpty()) {
			return true;
		}
		String text = value.substring(0, cycle.start()) + candidates.get(index);
		// A single match is final: add a space so the next argument can be typed right away.
		if (candidates.size() == 1) {
			text += " ";
		}
		input.setValue(text);
		input.moveCursorToEnd(false);
		applied = text;
		if (candidates.size() == 1) {
			cycle = null;
		}
		return true;
	}

	private void render(GuiGraphicsExtractor g) {
		String value = input.getValue();
		if (!applies(value)) {
			clearHint();
			return;
		}
		boolean cycling = cycle != null && value.equals(applied);
		CommandManager.Completion c = cycling ? cycle : current();
		List<String> candidates = c.candidates();
		updateHint(value, c, cycling);
		if (candidates.isEmpty()) {
			return;
		}

		Palette p = ThemeManager.get().palette();
		int selected = cycling ? index : -1;
		// Scroll the window of visible rows so the selected one is always shown.
		int first = Math.max(0, Math.min(selected - MAX_ROWS + 1, candidates.size() - MAX_ROWS));
		first = Math.max(0, Math.min(first, selected < 0 ? 0 : selected));
		int rows = Math.min(MAX_ROWS, candidates.size());
		int w = 0;
		for (int i = first; i < first + rows; i++) {
			w = Math.max(w, RenderUtil.width(candidates.get(i)));
		}
		w += 12;
		int typedW = Minecraft.getInstance().font.width(value.substring(0, Math.min(c.start(), value.length())));
		int x = Math.max(2, Math.min(input.getX() + typedW - 4, g.guiWidth() - w - 2));
		int h = rows * ROW_H + 6;
		int y = input.getY() - 4 - h;
		HudStyle.panel(g, p, x, y, w, h);
		for (int i = 0; i < rows; i++) {
			int idx = first + i;
			int ry = y + 3 + i * ROW_H;
			if (idx == selected) {
				RenderUtil.roundedRect(g, x + 2, ry, w - 4, ROW_H, 3, ColorUtil.mulAlpha(p.accent(), 0.3f));
			}
			int color = idx == selected || (selected < 0 && idx == 0) ? p.accent() : p.text();
			RenderUtil.text(g, null, candidates.get(idx), x + 6, ry + 2, color);
		}
	}

	/** Gray rest-of-word hint after the cursor, like vanilla command suggestions. */
	private void updateHint(String value, CommandManager.Completion c, boolean cycling) {
		if (cycling || c.candidates().isEmpty() || input.getCursorPosition() != value.length()) {
			clearHint();
			return;
		}
		String typed = value.substring(Math.min(c.start(), value.length()));
		String first = c.candidates().get(0);
		if (first.length() > typed.length()) {
			input.setSuggestion(first.substring(typed.length()));
			hinting = true;
		} else {
			clearHint();
		}
	}

	private void clearHint() {
		// Only undo our own hint; vanilla manages its suggestion for normal chat and /commands.
		if (hinting) {
			input.setSuggestion(null);
			hinting = false;
		}
	}
}
