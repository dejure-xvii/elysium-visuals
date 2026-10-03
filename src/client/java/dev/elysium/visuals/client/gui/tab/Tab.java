package dev.elysium.visuals.client.gui.tab;

import dev.elysium.visuals.client.gui.anim.SmoothValue;
import dev.elysium.visuals.client.gui.render.RenderUtil;
import dev.elysium.visuals.client.gui.render.TabIcon;
import dev.elysium.visuals.client.gui.widget.UiContainer;
import dev.elysium.visuals.client.theme.Palette;
import dev.elysium.visuals.client.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A ClickGUI category shown in the sidebar. Subclasses add widgets in
 * {@link #build(int, int, int)}; the base class handles clipping and scrolling.
 *
 * <p>To add a new tab: extend this class and register it in {@link TabRegistry}.
 */
public abstract class Tab extends UiContainer {
	private final SmoothValue scroll = new SmoothValue(0, 16f);
	private float scrollTarget;
	private int contentHeight;

	/** Name shown in the sidebar. */
	public abstract String title();

	/** Icon shown in the sidebar. */
	public abstract TabIcon icon();

	/** Small caption under the page title (drawn upper-case), e.g. "6 модулей". */
	public String subtitle() {
		return "";
	}

	/** Updates the scrollable height when content changes size (e.g. a card expands). */
	protected void setContentHeight(int contentHeight) {
		this.contentHeight = contentHeight;
		clampScroll();
	}

	/**
	 * Creates and positions widgets for the given content area.
	 * Called on open and whenever the window is resized.
	 *
	 * @return total height of the content (may exceed the view; it then scrolls)
	 */
	protected abstract int build(int x, int y, int width);

	/** Draws non-widget content (headings etc.) before the widgets. Coordinates are in content space. */
	protected void renderContent(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
	}

	/** Called every frame before rendering; lets a tab react to external changes. */
	public void tick() {
	}

	public final void layout(int x, int y, int width, int height) {
		setBounds(x, y, width, height);
		clear();
		contentHeight = build(x, y, width);
		clampScroll();
		scroll.set(scrollTarget);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Palette p, int mouseX, int mouseY, float delta) {
		tick();
		float off = scroll.update(scrollTarget);
		boolean inView = isHovered(mouseX, mouseY);
		int my = inView ? Math.round(mouseY + off) : Integer.MIN_VALUE / 2;
		int mx = inView ? mouseX : Integer.MIN_VALUE / 2;

		g.enableScissor(x, y, x + width, y + height);
		g.pose().pushMatrix();
		g.pose().translate(0, -off);
		renderContent(g, p, mx, my, delta);
		super.render(g, p, mx, my, delta);
		g.pose().popMatrix();
		g.disableScissor();

		renderScrollbar(g, p, off);
	}

	private void renderScrollbar(GuiGraphicsExtractor g, Palette p, float off) {
		int max = maxScroll();
		if (max <= 0) {
			return;
		}
		float viewFrac = height / (float) contentHeight;
		float barH = Math.max(16, height * viewFrac);
		float barY = y + (height - barH) * (off / max);
		RenderUtil.roundedRect(g, x + width + 3, barY, 2, barH, 1, ColorUtil.mulAlpha(p.text(), 0.16f));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!isHovered(mx, my)) {
			return false;
		}
		return super.mouseClicked(mx, my + scroll.get(), button);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		return super.mouseReleased(mx, my + scroll.get(), button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button) {
		return super.mouseDragged(mx, my + scroll.get(), button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!isHovered(mx, my)) {
			return false;
		}
		if (super.mouseScrolled(mx, my + scroll.get(), amount)) {
			return true;
		}
		scrollTarget -= (float) amount * 24f;
		clampScroll();
		return maxScroll() > 0;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - height);
	}

	private void clampScroll() {
		scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
	}
}
