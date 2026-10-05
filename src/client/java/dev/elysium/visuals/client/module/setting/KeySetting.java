package dev.elysium.visuals.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elysium.visuals.client.module.Module;
import net.minecraft.client.Minecraft;

/** A key (GLFW code, or {@link Module#NO_KEY}) used by a module itself, e.g. "hold to zoom". Shown as a bind button. */
public class KeySetting extends Setting<Integer> {
	public KeySetting(String id, String name, int defaultKey) {
		super(id, name, defaultKey);
	}

	public int key() {
		return value;
	}

	/** True while the key is physically held (never while a screen such as the chat is open). */
	public boolean isDown() {
		Minecraft mc = Minecraft.getInstance();
		return value != Module.NO_KEY && mc.gui.screen() == null && InputConstants.isKeyDown(mc.getWindow(), value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = json.getAsInt();
		}
	}
}
