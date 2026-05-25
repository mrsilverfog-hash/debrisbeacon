package net.tropimon.debrisbeacon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.tropimon.debrisbeacon.DebrisBeaconClient;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class DebrisBeaconRenderer {

    private static final float R = 1.0f;
    private static final float G = 0.65f;
    private static final float B = 0.0f;
    private static final float A = 1.0f;

    // Très fin : 0.02 bloc de rayon
    private static final float BEAM_RADIUS = 0.05f;
    private static final int BEAM_SIDES = 4;

    private static final int SEARCH_RADIUS = 64;
    private static final int SCAN_INTERVAL = 100;

    private static List<BlockPos> cachedBlocks = new ArrayList<>();
    private static long lastScanTick = -1;

    public static void resetCache() {
        cachedBlocks = new ArrayList<>();
        lastScanTick = -1;
    }

    public static void onWorldRenderLast(WorldRenderContext context) {
        if (!DebrisBeaconClient.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null || client.player == null) return;

        long currentTick = world.getTime();

        if (currentTick - lastScanTick >= SCAN_INTERVAL) {
            cachedBlocks = findDebris(world, client.player.getBlockPos());
            lastScanTick = currentTick;
        }

        if (cachedBlocks.isEmpty()) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        Vec3d playerEyes = client.player.getEyePos();

        float tickDelta = context.tickCounter().getTickDelta(true);
        float angle = ((currentTick % 360) + tickDelta) * 3.0f;

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        Tessellator tessellator = Tessellator.getInstance();

        for (BlockPos pos : cachedBlocks) {
            Vec3d debrisCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3d direction = playerEyes.subtract(debrisCenter).normalize();
            double distance = debrisCenter.distanceTo(playerEyes); // max 3 blocs de long

            drawBeam(tessellator, viewMatrix, camPos, debrisCenter, direction, distance, angle);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drawBeam(Tessellator tessellator, Matrix4f viewMatrix,
                                  Vec3d camPos, Vec3d origin, Vec3d direction,
                                  double length, float angle) {
        float ox = (float)(origin.x - camPos.x);
        float oy = (float)(origin.y - camPos.y);
        float oz = (float)(origin.z - camPos.z);

        float dx = (float) direction.x;
        float dy = (float) direction.y;
        float dz = (float) direction.z;

        Vec3d up = Math.abs(dy) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d right = direction.crossProduct(up).normalize();
        Vec3d upPerp = direction.crossProduct(right).normalize();

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        double angleOffset = Math.toRadians(angle);

        for (int i = 0; i < BEAM_SIDES; i++) {
            double a1 = angleOffset + (2 * Math.PI * i / BEAM_SIDES);
            double a2 = angleOffset + (2 * Math.PI * (i + 1) / BEAM_SIDES);

            float rx1 = (float)(Math.cos(a1) * BEAM_RADIUS);
            float ry1 = (float)(Math.sin(a1) * BEAM_RADIUS);
            float rx2 = (float)(Math.cos(a2) * BEAM_RADIUS);
            float ry2 = (float)(Math.sin(a2) * BEAM_RADIUS);

            // Base au débris — opaque
            float bx1 = ox + (float)(right.x * rx1 + upPerp.x * ry1);
            float by1 = oy + (float)(right.y * rx1 + upPerp.y * ry1);
            float bz1 = oz + (float)(right.z * rx1 + upPerp.z * ry1);
            float bx2 = ox + (float)(right.x * rx2 + upPerp.x * ry2);
            float by2 = oy + (float)(right.y * rx2 + upPerp.y * ry2);
            float bz2 = oz + (float)(right.z * rx2 + upPerp.z * ry2);

            // Pointe vers le joueur — transparent
            float px1 = ox + (float)(dx * length + right.x * rx1 + upPerp.x * ry1);
            float py1 = oy + (float)(dy * length + right.y * rx1 + upPerp.y * ry1);
            float pz1 = oz + (float)(dz * length + right.z * rx1 + upPerp.z * ry1);
            float px2 = ox + (float)(dx * length + right.x * rx2 + upPerp.x * ry2);
            float py2 = oy + (float)(dy * length + right.y * rx2 + upPerp.y * ry2);
            float pz2 = oz + (float)(dz * length + right.z * rx2 + upPerp.z * ry2);

            buffer.vertex(viewMatrix, bx1, by1, bz1).color(R, G, B, A);
            buffer.vertex(viewMatrix, bx2, by2, bz2).color(R, G, B, A);
            buffer.vertex(viewMatrix, px2, py2, pz2).color(R, G, B, 0f);
            buffer.vertex(viewMatrix, px1, py1, pz1).color(R, G, B, 0f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static List<BlockPos> findDebris(World world, BlockPos center) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos.iterate(
            center.add(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
            center.add(SEARCH_RADIUS,  SEARCH_RADIUS,  SEARCH_RADIUS)
        ).forEach(pos -> {
            if (world.getBlockState(pos).getBlock() == Blocks.ANCIENT_DEBRIS) {
                result.add(pos.toImmutable());
            }
        });
        return result;
    }
}
