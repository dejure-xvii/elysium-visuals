package dev.elysium.visuals.client.alt;

import com.mojang.blaze3d.platform.NativeImage;
import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Skin faces for the account cards: the real skin of Microsoft accounts, the default skin otherwise. */
public final class SkinHeads {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Map<String, CompletableFuture<NativeImage>> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> LOADED = new ConcurrentHashMap<>();

	private SkinHeads() {
	}

	private static Identifier texture(AltAccount a) {
		String url = a.skinUrl();
		if (url != null && isMojang(url)) {
			Identifier id = LOADED.get(url);
			if (id != null) {
				return id;
			}
			CompletableFuture<NativeImage> f = PENDING.computeIfAbsent(url, u -> CompletableFuture.supplyAsync(() -> download(u)));
			if (f.isDone()) {
				NativeImage image = f.join();
				if (image != null) {
					id = ElysiumVisuals.id("alt_skin/" + Integer.toHexString(url.hashCode()));
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "elysium alt skin", image));
					LOADED.put(url, id);
					return id;
				}
			}
		}
		return DefaultPlayerSkin.get(a.profileId()).body().texturePath();
	}

	/** Skins are only fetched from Mojang's texture server. */
	private static boolean isMojang(String url) {
		try {
			URI u = URI.create(url);
			return "https".equals(u.getScheme()) && u.getHost() != null && u.getHost().toLowerCase(Locale.ROOT).equals("textures.minecraft.net");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static NativeImage download(String url) {
		try {
			HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200) {
				return null;
			}
			return NativeImage.read(res.body());
		} catch (Exception e) {
			return null;
		}
	}

	/** The face (and hat layer) at (x, y), {@code size} GUI units square. */
	public static void draw(GuiGraphicsExtractor g, AltAccount a, int x, int y, int size, int color) {
		Identifier tex = texture(a);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8f, 8f, size, size, 8, 8, 64, 64, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40f, 8f, size, size, 8, 8, 64, 64, color);
	}
}
