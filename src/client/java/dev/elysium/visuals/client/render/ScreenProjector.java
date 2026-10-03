package dev.elysium.visuals.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Projects world positions onto the GUI (for plates that follow things in the world). */
public final class ScreenProjector {
	private final Matrix4f viewProj = new Matrix4f();
	private final Vector4f clip = new Vector4f();
	private CameraRenderState camera;
	private int guiW, guiH;
	public float x, y;

	/** Call once per frame before {@link #project}. */
	public void begin(int guiWidth, int guiHeight) {
		camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
		camera.projectionMatrix.mul(camera.viewRotationMatrix, viewProj);
		guiW = guiWidth;
		guiH = guiHeight;
	}

	/** Projects a world position into {@link #x}/{@link #y}; false if it is behind the camera. */
	public boolean project(double wx, double wy, double wz) {
		clip.set((float) (wx - camera.pos.x), (float) (wy - camera.pos.y), (float) (wz - camera.pos.z), 1f);
		viewProj.transform(clip);
		if (clip.w <= 0.05f) {
			return false;
		}
		x = (clip.x / clip.w * 0.5f + 0.5f) * guiW;
		y = (0.5f - clip.y / clip.w * 0.5f) * guiH;
		return true;
	}
}
