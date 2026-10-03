package dev.elysium.visuals.client.gui.widget;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.module.setting.StringSetting;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Label and an input field for a {@link StringSetting}; changes apply while typing. */
public class TextSettingWidget extends UiContainer {
	private static final int LABEL_H = 15;
	private static final int FIELD_H = 16;

	private final StringSetting setting;
	private final TextField field;

	public TextSettingWidget(StringSetting setting) {
		this.setting = setting;
		this.field = add(new TextField(setting.defaultValue(), setting.maxLength(), c -> c >= 32 && c != 127, text -> field().setFocused(false)));
		this.field.setText(setting.get());
		this.height = LABEL_H + FIELD_H + 5;
	}

	private TextField field() {
		return field;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		// Two-way sync: typing updates the setting; a config load updates the field.
		if (field.isFocused()) {
			if (!field.text().equals(setting.get())) {
				setting.set(field.text());
			}
		} else if (!field.text().equals(setting.get())) {
			field.setText(setting.get());
		}
		RenderUtil.well(g, x, y, width, height, 5, p, 0f);
		RenderUtil.text(g, setting.name(), RenderUtil.Face.REGULAR, x + 8, y + 4, p.text());
		field.setBounds(x + 5, y + LABEL_H, width - 10, FIELD_H);
		super.render(g, p, mouseX, mouseY, delta);
	}
}
