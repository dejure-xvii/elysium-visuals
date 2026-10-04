package dev.elysium.visuals.client.hud.style;

import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What the Interface elements show, independent of the style: list blocks
 * made of rows, the watermark parts and the target. Styles ({@link Skin}) only draw it.
 */
public final class HudData {
	private HudData() {
	}

	/** Which element a list block belongs to; styles draw the right-hand value differently per kind. */
	public enum Kind { BINDS, BUFFS, COOLDOWNS, FRIENDS, INFO }

	/**
	 * A list block: title and icon for the header, column captions for the
	 * "Карточки" footer.
	 */
	public record Block(Kind kind, String title, HudIcon icon, String leftColumn, String rightColumn) {
	}

	/**
	 * One row.
	 *
	 * @param sub      muted caption after the name ("Toggle"), may be empty
	 * @param value    right-hand text for binds (key), friends and info rows
	 * @param seconds  remaining time of buffs and cooldowns; {@link Float#POSITIVE_INFINITY} = infinite, NaN = unknown
	 * @param progress remaining part 0..1 for the time ring, or -1
	 * @param on       bind: module enabled; friend: online; buff: beneficial
	 */
	public record Row(String key, Lead lead, String name, String sub, String value, float seconds, float progress, boolean on) {
		public static Row text(String key, Lead lead, String name, String sub, String value, boolean on) {
			return new Row(key, lead, name, sub, value, Float.NaN, -1, on);
		}

		public static Row timed(String key, Lead lead, String name, float seconds, float progress, boolean on) {
			return new Row(key, lead, name, "", "", seconds, progress, on);
		}

		public boolean infinite() {
			return seconds == Float.POSITIVE_INFINITY;
		}
	}

	/** One watermark part, e.g. FPS: icon, short label ("FPS"), value ("144") and an optional lead (skin head). */
	public record Part(String id, HudIcon icon, String label, String value, boolean raw, Lead lead) {
	}

	/** Watermark: client name, Minecraft version and the selected parts. */
	public record Watermark(String client, String version, List<Part> parts) {
	}

	/** The fought entity, with already animated health values. */
	public interface Target {
		String name();

		/** Health 0..1, eased. */
		float health();

		/** Light trail behind the health bar (catches up after a hit). */
		float trail();

		/** Absorption as a part of max health, eased. */
		float absorption();

		/** Health points, eased. */
		float healthNumber();

		/** 0..1 content fade while switching to another target. */
		float content();

		/** Skin head (or spawn egg / initial) that shakes and flashes red on damage. */
		void drawHead(GuiGraphicsExtractor g, Palette p, int x, int y, int size, float radius);
	}

	/** Icon in front of a row. */
	public interface Lead {
		Lead NONE = (g, p, x, y, size) -> {
		};

		/** Draws into the {@code size}×{@code size} box at (x, y). */
		void draw(GuiGraphicsExtractor g, Palette p, int x, int y, int size);

		static Lead icon(HudIcon icon) {
			return (g, p, x, y, size) -> icon.draw(g, x + size / 2f, y + size / 2f, p.accent2());
		}

		static Lead sprite(Identifier sprite) {
			return (g, p, x, y, size) -> g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size, RenderUtil.alpha());
		}

		static Lead item(ItemStack stack) {
			return (g, p, x, y, size) -> {
				// Item models can't fade, so they only show while the row is mostly visible.
				if (RenderUtil.alpha() > 0.6f) {
					float s = size / 16f;
					g.pose().pushMatrix();
					g.pose().translate(x, y);
					g.pose().scale(s, s);
					g.item(stack, 0, 0);
					g.pose().popMatrix();
				}
			};
		}

		static Lead head(PlayerSkin skin) {
			return (g, p, x, y, size) -> PlayerFaceExtractor.extractRenderState(g, skin, x, y, size,
					((Math.round(255 * RenderUtil.alpha()) & 0xFF) << 24) | 0xFFFFFF);
		}
	}
}
