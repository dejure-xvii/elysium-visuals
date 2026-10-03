package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.world.entity.HumanoidArm;

/** Moves and scales the first-person hands, separately for each arm. */
public class ViewModel extends Module {
	private final NumberSetting rightX = add(new NumberSetting("right_x", "Правая рука X", 0, -1, 1, 0.01));
	private final NumberSetting rightY = add(new NumberSetting("right_y", "Правая рука Y", 0, -1, 1, 0.01));
	private final NumberSetting rightZ = add(new NumberSetting("right_z", "Правая рука Z", 0, -1, 1, 0.01));
	private final NumberSetting rightScale = add(new NumberSetting("right_scale", "Правая рука: размер", 1, 0.5, 1.5, 0.01, "x"));
	private final NumberSetting leftX = add(new NumberSetting("left_x", "Левая рука X", 0, -1, 1, 0.01));
	private final NumberSetting leftY = add(new NumberSetting("left_y", "Левая рука Y", 0, -1, 1, 0.01));
	private final NumberSetting leftZ = add(new NumberSetting("left_z", "Левая рука Z", 0, -1, 1, 0.01));
	private final NumberSetting leftScale = add(new NumberSetting("left_scale", "Левая рука: размер", 1, 0.5, 1.5, 0.01, "x"));

	public ViewModel() {
		super("view_model", "ViewModel", "Положение и размер рук от первого лица", Category.RENDER);
	}

	private static ViewModel active() {
		ViewModel m = ModuleManager.get().find(ViewModel.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** Called (mixin) right after the hand's pose is pushed: offset of the whole arm. */
	public static void translate(PoseStack pose, HumanoidArm arm) {
		ViewModel m = active();
		if (m != null) {
			boolean right = arm == HumanoidArm.RIGHT;
			pose.translate((right ? m.rightX : m.leftX).floatValue(), (right ? m.rightY : m.leftY).floatValue(),
					(right ? m.rightZ : m.leftZ).floatValue());
		}
	}

	/** Called (mixin) just before the held item is drawn, so it scales around itself. */
	public static void scale(PoseStack pose, HumanoidArm arm) {
		ViewModel m = active();
		if (m != null) {
			float s = (arm == HumanoidArm.RIGHT ? m.rightScale : m.leftScale).floatValue();
			pose.scale(s, s, s);
		}
	}
}
