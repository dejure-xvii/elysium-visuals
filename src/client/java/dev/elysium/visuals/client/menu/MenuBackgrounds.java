package dev.elysium.visuals.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.MainMenu;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The main menu backgrounds: the built-in 360° panoramas (CC0 photos from
 * Poly Haven, shown as a slowly turning cube map like the vanilla one), the
 * animated gradient of the launcher and the user's own PNG/JPG images from
 * {@code config/elysium-visuals/backgrounds}.
 *
 * <p>Images are decoded and converted on a background thread; the GPU
 * textures are created on the render thread once that is done. Only the
 * selected background is kept in video memory, plus small thumbnails.
 */
public final class MenuBackgrounds {
	public enum Kind { PANORAMA, GRADIENT, IMAGE }

	public record Background(String id, String label, Kind kind, Path file) {
	}

	public static final String GRADIENT = "gradient";
	private static final int FACE = 1024;

	private static final List<Background> BUILT_IN = List.of(
			new Background("forest", "Лес", Kind.PANORAMA, null),
			new Background("river", "Река", Kind.PANORAMA, null),
			new Background("mountains", "Горы", Kind.PANORAMA, null),
			new Background("night", "Ночное небо", Kind.PANORAMA, null),
			new Background("sunset", "Закат", Kind.PANORAMA, null),
			new Background(GRADIENT, "Анимированный градиент", Kind.GRADIENT, null));

	private static List<Background> all = new ArrayList<>(BUILT_IN);

	/** A loaded background: a cube map or a flat image. */
	public static final class Loaded {
		final CompletableFuture<Object> data;
		CubeMap cube;
		Identifier cubeId;
		DynamicTexture image;
		boolean failed;

		Loaded(CompletableFuture<Object> data) {
			this.data = data;
		}

		public DynamicTexture image() {
			return image;
		}

		void close() {
			if (cube != null) {
				cube.close();
				Minecraft.getInstance().getTextureManager().release(cubeId);
			}
			if (image != null) {
				image.close();
			}
			data.thenAccept(o -> {
				if (o instanceof NativeImage n && cube == null && image == null) {
					n.close();
				}
			});
		}
	}

	private static final Map<String, Loaded> FULL = new HashMap<>();
	private static final Map<String, Loaded> THUMBS = new HashMap<>();

	private MenuBackgrounds() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(ElysiumVisuals.MOD_ID).resolve("backgrounds");
	}

	/** Rescans the user's folder. */
	public static void refresh() {
		List<Background> list = new ArrayList<>(BUILT_IN);
		Path dir = folder();
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(Files::isRegularFile)
						.filter(f -> {
							String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
							return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
						})
						.sorted()
						.forEach(f -> {
							String name = f.getFileName().toString();
							String label = name.substring(0, name.lastIndexOf('.'));
							list.add(new Background("file:" + name, label, Kind.IMAGE, f));
						});
			}
		} catch (IOException e) {
			ElysiumVisuals.LOGGER.warn("Can't read the menu backgrounds folder: {}", e.toString());
		}
		all = list;
	}

	public static List<Background> all() {
		return all;
	}

	public static Background byId(String id) {
		for (Background b : all) {
			if (b.id().equals(id)) {
				return b;
			}
		}
		return null;
	}

	/** The selected background (falls back to the first panorama). */
	public static Background selected() {
		MainMenu menu = ModuleManager.get().find(MainMenu.class);
		Background b = menu != null ? byId(menu.background()) : null;
		if (b == null && menu != null && menu.background().startsWith("file:")) {
			refresh();
			b = byId(menu.background());
		}
		return b != null ? b : BUILT_IN.getFirst();
	}

	public static GpuSampler linear() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	// ---------------------------------------------------------------------
	// Full-size backgrounds
	// ---------------------------------------------------------------------

	/** The background ready to draw, or null while it is still loading. Starts loading on first call. */
	public static Loaded full(Background b) {
		if (b.kind() == Kind.GRADIENT) {
			return null;
		}
		Loaded l = FULL.computeIfAbsent(b.id(), id -> new Loaded(CompletableFuture.supplyAsync(() -> loadFull(b))));
		if (!finish(l, b, false)) {
			return null;
		}
		return l;
	}

	/** Frees every full-size background except {@code id} (called once a switch is done). */
	public static void retainOnly(String id) {
		FULL.entrySet().removeIf(e -> {
			if (!e.getKey().equals(id) && e.getValue().data.isDone()) {
				e.getValue().close();
				return true;
			}
			return false;
		});
	}

	/** The cube map to draw instead of the vanilla panorama, or null to keep the vanilla one. */
	public static CubeMap activeCube() {
		MainMenu menu = ModuleManager.get().find(MainMenu.class);
		if (menu == null || !menu.isEnabled()) {
			return null;
		}
		Background b = selected();
		if (b.kind() != Kind.PANORAMA) {
			return null;
		}
		Loaded l = full(b);
		return l != null ? l.cube : null;
	}

	private static Object loadFull(Background b) {
		try {
			if (b.kind() == Kind.PANORAMA) {
				return MenuImages.cubeStrip(readBuiltIn(b, 1), FACE);
			}
			try (InputStream in = Files.newInputStream(b.file())) {
				return MenuImages.toNative(MenuImages.fit(MenuImages.decode(in, 1), 2560, 1600));
			}
		} catch (Throwable t) {
			ElysiumVisuals.LOGGER.warn("Can't load menu background {}: {}", b.id(), t.toString());
			return t;
		}
	}

	private static MenuImages.Pixels readBuiltIn(Background b, int step) throws IOException {
		Identifier res = ElysiumVisuals.id("textures/gui/menu/" + b.id() + ".jpg");
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
		try (InputStream in = rm.open(res)) {
			return MenuImages.decode(in, step);
		}
	}

	/** Uploads a finished decode on the render thread. Returns whether the background is ready. */
	private static boolean finish(Loaded l, Background b, boolean thumb) {
		if (l.cube != null || l.image != null) {
			return true;
		}
		if (l.failed || !l.data.isDone()) {
			return false;
		}
		Object o = l.data.join();
		if (!(o instanceof NativeImage image)) {
			l.failed = true;
			return false;
		}
		try {
			if (b.kind() == Kind.PANORAMA && !thumb) {
				l.cubeId = ElysiumVisuals.id("menu/" + b.id());
				Minecraft.getInstance().getTextureManager().registerAndLoad(l.cubeId, new PreloadedCube(l.cubeId, image, b));
				l.cube = new CubeMap(l.cubeId);
			} else {
				l.image = new DynamicTexture(() -> ElysiumVisuals.MOD_ID + " menu " + b.id(), image);
			}
			return true;
		} catch (Throwable t) {
			ElysiumVisuals.LOGGER.warn("Can't upload menu background {}: {}", b.id(), t.toString());
			l.failed = true;
			return false;
		}
	}

	// ---------------------------------------------------------------------
	// Thumbnails (2:1)
	// ---------------------------------------------------------------------

	public static final int THUMB_W = 256, THUMB_H = 128;

	/** The thumbnail texture, or null while loading (and always for the gradient). */
	public static DynamicTexture thumb(Background b) {
		if (b.kind() == Kind.GRADIENT) {
			return null;
		}
		Loaded l = THUMBS.computeIfAbsent(b.id(), id -> new Loaded(CompletableFuture.supplyAsync(() -> loadThumb(b))));
		return finish(l, b, true) ? l.image : null;
	}

	private static Object loadThumb(Background b) {
		try {
			MenuImages.Pixels p;
			if (b.kind() == Kind.PANORAMA) {
				// The horizon band of the photo.
				p = readBuiltIn(b, 8);
				int h = p.height() / 2, w = h * 2;
				p = MenuImages.resize(p, (p.width() - w) / 2, p.height() / 4, w, h, THUMB_W, THUMB_H);
			} else {
				try (InputStream in = Files.newInputStream(b.file())) {
					p = MenuImages.decode(in, 1);
				}
				// Cover-crop to 2:1.
				int w = p.width(), h = p.height();
				int cw = Math.min(w, h * 2), ch = Math.min(h, w / 2);
				p = MenuImages.resize(p, (w - cw) / 2, (h - ch) / 2, cw, Math.max(1, ch), THUMB_W, THUMB_H);
			}
			return MenuImages.toNative(p);
		} catch (Throwable t) {
			ElysiumVisuals.LOGGER.warn("Can't load menu thumbnail {}: {}", b.id(), t.toString());
			return t;
		}
	}

	/** The cube texture, filled from the already converted image (or converted again on a resource reload). */
	private static final class PreloadedCube extends CubeMapTexture {
		private NativeImage pending;
		private final Background background;

		PreloadedCube(Identifier id, NativeImage image, Background background) {
			super(id);
			this.pending = image;
			this.background = background;
		}

		@Override
		public TextureContents loadContents(ResourceManager resources) throws IOException {
			NativeImage image = pending;
			pending = null;
			if (image == null) {
				image = MenuImages.cubeStrip(readBuiltIn(background, 1), FACE);
			}
			return new TextureContents(image, new TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0f));
		}
	}
}
