package net.minecraft.client.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class CameraRenderState implements FabricRenderState {
	public BlockPos blockPos = BlockPos.ORIGIN;
	public Vec3d pos = new Vec3d(0.0, 0.0, 0.0);
	public boolean initialized;
	public Vec3d entityPos = new Vec3d(0.0, 0.0, 0.0);
	public Quaternionf orientation = new Quaternionf();
}
