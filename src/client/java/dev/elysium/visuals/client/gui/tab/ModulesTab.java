package dev.elysium.visuals.client.gui.tab;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.TabIcon;
import dev.elysium.visuals.client.gui.widget.ModuleCard;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** A tab listing the modules of one {@link Category}, or the results of a module search. */
public class ModulesTab extends Tab {
	private static final int CARD_GAP = 5;

	private final String title;
	private final TabIcon icon;
	private final Supplier<List<Module>> source;
	private final boolean search;
	private final List<ModuleCard> cards = new ArrayList<>();
	private int contentX;
	private int contentY;
	private int contentW;

	public ModulesTab(Category category) {
		this(category.displayName(), category.icon(), () -> ModuleManager.get().byCategory(category), false);
	}

	private ModulesTab(String title, TabIcon icon, Supplier<List<Module>> source, boolean search) {
		this.title = title;
		this.icon = icon;
		this.source = source;
		this.search = search;
	}

	/** Modules of every category whose name or description contains {@code query} (case-insensitive). */
	public static ModulesTab search(String query) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		return new ModulesTab("Поиск", TabIcon.UTILS, () -> {
			List<Module> result = new ArrayList<>();
			for (Module m : ModuleManager.get().modules()) {
				if (m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) {
					result.add(m);
				}
			}
			return result;
		}, true);
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public TabIcon icon() {
		return icon;
	}

	@Override
	public String subtitle() {
		int count = cards.size();
		if (search) {
			return count == 0 ? "Ничего не найдено" : "Найдено " + count + " " + plural(count, "модуль", "модуля", "модулей");
		}
		return count == 0 ? "Модулей пока нет" : count + " " + plural(count, "модуль", "модуля", "модулей");
	}

	@Override
	protected int build(int x, int y, int width) {
		contentX = x;
		contentY = y;
		contentW = width;
		cards.clear();
		for (Module module : source.get()) {
			cards.add(add(new ModuleCard(module, search)));
		}
		return relayout();
	}

	@Override
	public void tick() {
		// Cards change height while expanding, so positions are recomputed every frame.
		setContentHeight(relayout());
	}

	private int relayout() {
		int cy = contentY;
		for (ModuleCard card : cards) {
			card.setBounds(contentX, cy, contentW, card.preferredHeight());
			cy += card.height() + CARD_GAP;
		}
		return cy - contentY;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		if (cards.isEmpty()) {
			String text = search ? "Попробуйте другой запрос" : "Здесь пока пусто";
			RenderUtil.text(g, text, RenderUtil.Face.REGULAR, contentX + (contentW - RenderUtil.width(text)) / 2, contentY + 30, p.textFaint());
		}
	}

	private static String plural(int n, String one, String few, String many) {
		int mod100 = n % 100, mod10 = n % 10;
		if (mod100 >= 11 && mod100 <= 14) {
			return many;
		}
		if (mod10 == 1) {
			return one;
		}
		return mod10 >= 2 && mod10 <= 4 ? few : many;
	}
}
