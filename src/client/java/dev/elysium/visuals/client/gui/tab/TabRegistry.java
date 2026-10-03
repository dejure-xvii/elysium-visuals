package dev.elysium.visuals.client.gui.tab;

import dev.elysium.visuals.client.module.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The list of ClickGUI tabs, in sidebar order. Tabs are created fresh each
 * time the GUI opens, so they never hold stale state.
 *
 * <p>"Темы" comes first, then every module {@link Category} gets a
 * {@link ModulesTab} automatically. More tabs can be added with {@link #register}.
 */
public final class TabRegistry {
	private static final List<Supplier<Tab>> FACTORIES = new ArrayList<>();

	static {
		register(ThemesTab::new);
		for (Category category : Category.values()) {
			register(() -> new ModulesTab(category));
		}
	}

	private TabRegistry() {
	}

	public static void register(Supplier<Tab> factory) {
		FACTORIES.add(factory);
	}

	public static List<Tab> createTabs() {
		List<Tab> tabs = new ArrayList<>();
		for (Supplier<Tab> f : FACTORIES) {
			tabs.add(f.get());
		}
		return tabs;
	}
}
