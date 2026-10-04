package dev.elysium.visuals.client.hud.element;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.ListElement;
import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

/** Modules that have a key bind, with the key; enabled modules are marked. */
public class BindsElement extends ListElement {
	private static final HudData.Block BLOCK = new HudData.Block(HudData.Kind.BINDS, "Бинды", HudIcon.KEYBOARD,
			"Модуль", "Клавиша");
	private static final HudData.Lead LEAD = HudData.Lead.icon(HudIcon.MODULE);

	public BindsElement() {
		super("binds", "Привязанные модули", Anchor.TOP_LEFT);
	}

	@Override
	protected HudData.Block block() {
		return BLOCK;
	}

	@Override
	protected List<HudData.Row> collect() {
		List<HudData.Row> result = new ArrayList<>();
		for (Module m : ModuleManager.get().modules()) {
			if (m.bind() != Module.NO_KEY) {
				String key = InputConstants.Type.KEYSYM.getOrCreate(m.bind()).getDisplayName().getString();
				result.add(HudData.Row.text(m.id(), LEAD, m.name(), m.bindTogglesModule() ? "Toggle" : "Action", key, m.isEnabled()));
			}
		}
		return result;
	}

	@Override
	protected List<HudData.Row> sample() {
		return List.of(
				HudData.Row.text("sample_aura", LEAD, "Aura", "Toggle", "R", true),
				HudData.Row.text("sample_sprint", LEAD, "AutoSprint", "Toggle", "V", false));
	}
}
