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

    // Taille du point lumineux (billboard)
    private static final float POINT_SIZE = 0.15f;

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

        // Vecteurs de la caméra pour le billboard (toujours face au joueur)
        org.joml.Vector3f camRight = new org.joml.Vector3f();
        org.joml.Vector3f camUp = new org.joml.Vector3f();
        context.matrixStack().peek().getPositionMatrix().positiveX(camRight);
        context.matrixStack().peek().getPositionMatrix().positiveY(camUp);

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (BlockPos pos : cachedBlocks) {
            float x = (float)(pos.getX() + 0.5 - camPos.x);
            float y = (float)(pos.getY() + 0.5 - camPos.y);
            float z = (float)(pos.getZ() + 0.5 - camPos.z);

            float s = POINT_SIZE;

            // Billboard : carré qui fait toujours face à la caméra
            // Coin bas-gauche
            float x0 = x - camRight.x * s - camUp.x * s;
            float y0 = y - camRight.y * s - camUp.y * s;
            float z0 = z - camRight.z * s - camUp.z * s;
            // Coin bas-droit
            float x1 = x + camRight.x * s - camUp.x * s;
            float y1 = y + camRight.y * s - camUp.y * s;
            float z1 = z + camRight.z * s - camUp.z * s;
            // Coin haut-droit
            float x2 = x + camRight.x * s + camUp.x * s;
            float y2 = y + camRight.y * s + camUp.y * s;
            float z2 = z + camRight.z * s + camUp.z * s;
            // Coin haut-gauche
            float x3 = x - camRight.x * s + camUp.x * s;
            float y3 = y - camRight.y * s + camUp.y * s;
            float z3 = z - camRight.z * s + camUp.z * s;

            // Centre très lumineux, bords transparents
            buffer.vertex(viewMatrix, x0, y0, z0).color(R, G, B, 0.0f);
            buffer.vertex(viewMatrix, x1, y1, z1).color(R, G, B, 0.0f);
            buffer.vertex(viewMatrix, x2, y2, z2).color(R, G, B, 0.0f);
            buffer.vertex(viewMatrix, x3, y3, z3).color(R, G, B, 0.0f);

            // Carré intérieur lumineux
            float si = s * 0.3f;
            float xi0 = x - camRight.x * si - camUp.x * si;
            float yi0 = y - camRight.y * si - camUp.y * si;
            float zi0 = z - camRight.z * si - camUp.z * si;
            float xi1 = x + camRight.x * si - camUp.x * si;
            float yi1 = y + camRight.y * si - camUp.y * si;
            float zi1 = z + camRight.z * si - camUp.z * si;
            float xi2 = x + camRight.x * si + camUp.x * si;
            float yi2 = y + camRight.y * si + camUp.y * si;
            float zi2 = z + camRight.z * si + camUp.z * si;
            float xi3 = x - camRight.x * si + camUp.x * si;
            float yi3 = y - camRight.y * si + camUp.y * si;
            float zi3 = z - camRight.z * si + camUp.z * si;

            buffer.vertex(viewMatrix, xi0, yi0, zi0).color(R, G, B, 1.0f);
            buffer.vertex(viewMatrix, xi1, yi1, zi1).color(R, G, B, 1.0f);
            buffer.vertex(viewMatrix, xi2, yi2, zi2).color(R, G, B, 1.0f);
            buffer.vertex(viewMatrix, xi3, yi3, zi3).color(R, G, B, 1.0f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
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
