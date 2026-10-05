package dev.elysium.visuals.client.module.impl.render;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.MultiSelectSetting;

import java.util.List;
import java.util.Set;

import static dev.elysium.visuals.client.module.setting.MultiSelectSetting.option;

/**
 * The camera goes through blocks instead of stopping at them. Third person:
 * no pulling in towards the player in front of a wall. First person: no block
 * texture over the screen with the head in a block, and a closer near plane so
 * blocks right at the face aren't cut open. Plus switches for the vanilla
 * walking bob and the shake on damage.
 */
public class NoCameraClip extends Module {
	private final MultiSelectSetting views = add(new MultiSelectSetting("views", "Где",
			List.of(option("third", "Вид от 3-го лица"), option("first", "Вид от 1-го лица у стены")),
			Set.of("third", "first")));
	private final BooleanSetting walkBob = add(new BooleanSetting("walk_bob", "Тряска при ходьбе", true));
	private final BooleanSetting hurtShake = add(new BooleanSetting("hurt_shake", "Тряска при уроне", true));

	public NoCameraClip() {
		super("no_camera_clip", "NoCameraClip", "Камера проходит сквозь блоки; тряска камеры при ходьбе и уроне", Category.RENDER);
	}

	private static NoCameraClip active() {
		NoCameraClip m = ModuleManager.get().find(NoCameraClip.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** Third person: the camera keeps its full distance behind walls. */
	public static boolean thirdPersonThroughBlocks() {
		NoCameraClip m = active();
		return (m != null && m.views.isSelected("third")) || NoRender.hides("camera_clip");
	}

	/** First person: no in-wall overlay and a closer near plane. */
	public static boolean firstPersonThroughBlocks() {
		NoCameraClip m = active();
		return m != null && m.views.isSelected("first");
	}

	/** False: the vanilla view bobbing is skipped. */
	public static boolean walkBob() {
		NoCameraClip m = active();
		return m == null || m.walkBob.isOn();
	}

	/** False: the camera doesn't shake on damage. */
	public static boolean hurtShake() {
		NoCameraClip m = active();
		return (m == null || m.hurtShake.isOn()) && !NoRender.hides("hurt_cam");
	}
}
