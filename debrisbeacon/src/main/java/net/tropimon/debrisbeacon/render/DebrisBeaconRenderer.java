package net.tropimon.debrisbeacon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.tropimon.debrisbeacon.DebrisBeaconClient;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.List;

public class DebrisBeaconRenderer {

    private static final float R = 1.0f;
    private static final float G = 0.55f;
    private static final float B = 0.0f;
    private static final float A = 1.0f;

    private static final int SEARCH_RADIUS = 64;
    private static final int SCAN_INTERVAL = 40;

    private static List<BlockPos> cachedBlocks = new ArrayList<>();
    private static long lastScanTick = -1;
    private static BlockPos lastCenter = null; // Pour détecter le changement de serveur/dimension

    public static List<BlockPos> getCachedBlocks() {
        return cachedBlocks;
    }

    public static int getDebrisCount() {
        return cachedBlocks.size();
    }

    public static void resetCache() {
        cachedBlocks = new ArrayList<>();
        lastScanTick = -1;
        lastCenter = null;
    }

    public static void onWorldRenderLast(WorldRenderContext context) {
        if (!DebrisBeaconClient.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null || client.player == null) return;

        long currentTick = world.getTime();
        BlockPos currentCenter = client.player.getBlockPos();

        // Forcer un rescan si on a changé de dimension ou si la position a beaucoup changé
        if (lastCenter == null || currentCenter.getManhattanDistance(lastCenter) > SEARCH_RADIUS) {
            lastScanTick = -1;
        }

        if (currentTick - lastScanTick >= SCAN_INTERVAL) {
            cachedBlocks = findDebris(world, currentCenter);
            lastScanTick = currentTick;
            lastCenter = currentCenter;
        }

        if (cachedBlocks.isEmpty()) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        Vec3d playerEyes = client.player.getEyePos();

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();

        // --- Lignes ---
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (BlockPos pos : cachedBlocks) {
            Vec3d debrisCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3d direction = playerEyes.subtract(debrisCenter).normalize();
            // Le laser s'arrête 1 bloc avant les yeux du joueur (ne le traverse pas)
            double distance = Math.max(0, debrisCenter.distanceTo(playerEyes) - 1.0);

            float sx = (float)(debrisCenter.x - camPos.x);
            float sy = (float)(debrisCenter.y - camPos.y);
            float sz = (float)(debrisCenter.z - camPos.z);

            float ex = (float)(debrisCenter.x - camPos.x + direction.x * distance);
            float ey = (float)(debrisCenter.y - camPos.y + direction.y * distance);
            float ez = (float)(debrisCenter.z - camPos.z + direction.z * distance);

            buffer.vertex(viewMatrix, sx, sy, sz).color(R, G, B, A);
            buffer.vertex(viewMatrix, ex, ey, ez).color(R, G, B, A);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // --- Texte flottant avec la distance ---
        TextRenderer textRenderer = client.textRenderer;
        MatrixStack matrices = context.matrixStack();

        for (BlockPos pos : cachedBlocks) {
            Vec3d debrisCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dist = debrisCenter.distanceTo(playerEyes);
            String label = (int) dist + "m";

            double dx = debrisCenter.x - camPos.x;
            double dy = debrisCenter.y - camPos.y;
            double dz = debrisCenter.z - camPos.z;

            matrices.push();
            matrices.translate(dx, dy + 0.6, dz);
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, -0.025f);

            Matrix4f textMatrix = matrices.peek().getPositionMatrix();
            int textWidth = textRenderer.getWidth(label);

            RenderSystem.enableDepthTest();
            textRenderer.draw(label, -textWidth / 2f, 0, 0xFFAA44, false,
                textMatrix, client.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            client.getBufferBuilders().getEntityVertexConsumers().draw();
            RenderSystem.disableDepthTest();

            matrices.pop();
        }

        RenderSystem.enableDepthTest();
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
