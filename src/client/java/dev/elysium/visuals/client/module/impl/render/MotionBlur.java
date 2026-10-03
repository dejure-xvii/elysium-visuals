package dev.elysium.visuals.client.module.impl.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import dev.elysium.visuals.client.render.FullscreenPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Motion blur on the world (before the hand and HUD are drawn). Each pixel is projected
 * back into the previous frame with the old camera, blurred along that motion,
 * and blended with an accumulation buffer of past frames.
 */
public class MotionBlur extends Module {
	private static final double TELEPORT_DISTANCE = 4;

	private static FullscreenPass pass;

	private final NumberSetting strength = add(new NumberSetting("strength", "Сила", 0.5, 0.1, 1, 0.05));
	private final NumberSetting samples = add(new NumberSetting("samples", "Количество сэмплов", 8, 4, 16, 1));
	private final BooleanSetting rotation = add(new BooleanSetting("rotation", "Учитывать поворот камеры", true));
	private final BooleanSetting movement = add(new BooleanSetting("movement", "Учитывать движение", true));

	private TextureTarget history;
	private TextureTarget output;
	private boolean hasHistory;
	private final Matrix4f prevView = new Matrix4f();
	private Vec3 prevCamera;
	// Scratch matrices, reused every frame.
	private final Matrix4f viewProj = new Matrix4f();
	private final Matrix4f reproj = new Matrix4f();
	private final Matrix4f prevViewProj = new Matrix4f();

	public MotionBlur() {
		super("motion_blur", "MotionBlur", "Размытие в движении с накоплением прошлых кадров", Category.RENDER);
	}

	/** Registers the pipeline before resources load. */
	public static void init() {
		pass = new FullscreenPass("motion_blur", "core/motion_blur",
				List.of("MainSampler", "DepthSampler", "HistorySampler"), "MotionInfo", 64 + 16, null);
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		hasHistory = false;
		prevCamera = null;
		if (history != null) {
			history.destroyBuffers();
			history = null;
		}
		if (output != null) {
			output.destroyBuffers();
			output = null;
		}
	}

	/** Called (mixin) right after the world is rendered, before the hand and the GUI. */
	public static void process(GameRenderer renderer) {
		MotionBlur m = ModuleManager.get().find(MotionBlur.class);
		if (m != null && m.isEnabled() && Minecraft.getInstance().level != null) {
			m.apply(renderer);
		}
	}

	private void apply(GameRenderer renderer) {
		RenderTarget main = renderer.mainRenderTarget();
		GpuTextureView color = main.getColorTextureView(), depth = main.getDepthTextureView();
		if (color == null || depth == null || main.getColorTexture() == null) {
			return;
		}
		int w = main.width, h = main.height;
		if (history == null) {
			history = new TextureTarget("elysium motion blur history", w, h, false, GpuFormat.RGBA8_UNORM);
			output = new TextureTarget("elysium motion blur output", w, h, false, GpuFormat.RGBA8_UNORM);
			hasHistory = false;
		} else if (history.width != w || history.height != h) {
			history.resize(w, h);
			output.resize(w, h);
			hasHistory = false;
		}

		CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
		Vec3 pos = camera.pos;
		boolean teleported = prevCamera == null || pos.distanceToSqr(prevCamera) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (!hasHistory || teleported) {
			encoder.copyTextureToTexture(main.getColorTexture(), history.getColorTexture(), 0, 0, 0, 0, 0, w, h);
			hasHistory = true;
			prevView.set(camera.viewRotationMatrix);
			prevCamera = pos;
		}

		// current NDC -> camera-relative world -> (moved by the camera delta) -> previous NDC
		camera.projectionMatrix.mul(camera.viewRotationMatrix, viewProj);
		viewProj.invert();
		camera.projectionMatrix.mul(rotation.isOn() ? prevView : camera.viewRotationMatrix, prevViewProj);
		reproj.set(prevViewProj);
		if (movement.isOn()) {
			reproj.translate((float) (pos.x - prevCamera.x), (float) (pos.y - prevCamera.y), (float) (pos.z - prevCamera.z));
		}
		reproj.mul(viewProj);

		float s = strength.floatValue();
		float accumulate = s * 0.45f;
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		pass.run(output.getColorTextureView(),
				new GpuTextureView[]{color, depth, history.getColorTextureView()},
				new boolean[]{true, false, false},
				b -> b.putMat4f(reproj)
						.putVec4(s * 2f, samples.intValue(), accumulate, zeroToOne ? 1f : 0f));
		encoder.copyTextureToTexture(output.getColorTexture(), main.getColorTexture(), 0, 0, 0, 0, 0, w, h);
		encoder.copyTextureToTexture(output.getColorTexture(), history.getColorTexture(), 0, 0, 0, 0, 0, w, h);

		prevView.set(camera.viewRotationMatrix);
		prevCamera = pos;
	}
}
