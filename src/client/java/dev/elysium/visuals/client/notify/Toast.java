package dev.elysium.visuals.client.notify;

import dev.elysium.visuals.client.gui.anim.Animation;
import dev.elysium.visuals.client.gui.anim.Easing;
import dev.elysium.visuals.client.gui.anim.SmoothValue;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/** One toast: content plus its animation state. */
public final class Toast {
	public final String title;
	public final String text;
	public final ItemStack icon;
	public final Notifications.Tone tone;
	public final long createdMs = Util.getMillis();
	/** Appearance: fades in while scaling from 0.9 to 1. */
	public final Animation in = new Animation(0, 200, Easing.OUT_CUBIC);
	/** Disappearance: a plain fade. */
	public final Animation out = new Animation(1, 260, Easing.OUT_CUBIC);
	/** Animated vertical offset inside the stack, so toasts glide when a new one pushes them down. */
	public final SmoothValue offset = new SmoothValue(-1, 16f);
	private boolean leaving;

	public Toast(String title, String text, ItemStack icon, Notifications.Tone tone) {
		this.title = title;
		this.text = text;
		this.icon = icon;
		this.tone = tone;
		in.animateTo(1);
	}

	public boolean isLeaving() {
		return leaving;
	}

	public void leave() {
		if (!leaving) {
			leaving = true;
			out.animateTo(0);
		}
	}

	/** Fully faded out after leaving. */
	public boolean isGone() {
		return leaving && out.isDone();
	}

	public float alpha() {
		return in.get() * out.get();
	}

	public float scale() {
		return 0.9f + 0.1f * in.get();
	}
}
