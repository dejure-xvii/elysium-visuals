package dev.elysium.visuals.client.module;

import dev.elysium.visuals.client.hud.HudElement;
import dev.elysium.visuals.client.module.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all modules. A new module is one class:
 *
 * <pre>{@code
 * public class MyModule extends Module {
 *     private final BooleanSetting option = add(new BooleanSetting("option", "Опция", true));
 *
 *     public MyModule() {
 *         super("my_module", "My Module", "Что делает модуль", Category.UTILS);
 *     }
 *
 *     @Override
 *     public void onTick(Minecraft mc) { ... }
 * }
 * }</pre>
 * and one line in {@link ModuleManager#registerModules()}. The GUI card,
 * settings widgets, key bind and saving all come from this base class.
 * Modules that draw on screen register draggable elements with {@link #addHud}.
 */
public abstract class Module {
	public static final int NO_KEY = -1;

	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<HudElement> hudElements = new ArrayList<>();
	private boolean enabled;
	private boolean enabledByDefault;
	private int bind = NO_KEY;
	private boolean bindWasDown;

	protected Module(String id, String name, String description, Category category) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
	}

	/** Registers a setting; call from field initialisers. */
	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** Registers a draggable HUD element shown while this module is enabled. */
	protected <E extends HudElement> E addHud(E element) {
		element.setOwner(this);
		hudElements.add(element);
		return element;
	}

	/** Makes a fresh install start with this module on (the config overrides it later). */
	protected void enableByDefault() {
		enabled = true;
		enabledByDefault = true;
	}

	/** Back to a fresh install: default on/off state, no bind, default settings (except global ones). */
	public void resetToDefaults() {
		resetSettings();
		setEnabled(enabledByDefault);
	}

	/** Default settings (except global ones) and no bind; the on/off state is left alone. */
	public void resetSettings() {
		for (Setting<?> s : settings) {
			if (!s.isGlobal()) {
				s.reset();
			}
		}
		setBind(NO_KEY);
	}

	public boolean isEnabledByDefault() {
		return enabledByDefault;
	}

	// --- Lifecycle hooks (override as needed) -----------------------------

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick (20/s) while enabled. */
	public void onTick(Minecraft mc) {
	}

	/**
	 * Draws full-screen effects (vignettes, a crosshair) every frame while
	 * enabled, under the draggable HUD elements. Not called while F1 hides the HUD.
	 */
	public void renderOverlay(GuiGraphicsExtractor g, float partialTick) {
	}

	/** True to hide a vanilla HUD layer (see {@code VanillaHudElements}) while this module is enabled. */
	public boolean hidesVanillaHud(Identifier vanillaElement) {
		return false;
	}

	// --- State --------------------------------------------------------------

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		ModuleManager.get().markDirty();
		ModuleManager.get().fireToggle(this, enabled);
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** GLFW key code, or {@link #NO_KEY}. */
	public int bind() {
		return bind;
	}

	public void setBind(int key) {
		if (bind != key) {
			bind = key;
			ModuleManager.get().markDirty();
		}
	}

	/** Used by the config loader: sets state without hooks or dirty marking. */
	void loadState(boolean enabled, int bind) {
		this.enabled = enabled;
		this.bind = bind;
		if (enabled) {
			onEnable();
		}
	}

	boolean updateBindState(boolean down) {
		boolean pressed = down && !bindWasDown;
		bindWasDown = down;
		return pressed;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public List<HudElement> hudElements() {
		return Collections.unmodifiableList(hudElements);
	}
}
