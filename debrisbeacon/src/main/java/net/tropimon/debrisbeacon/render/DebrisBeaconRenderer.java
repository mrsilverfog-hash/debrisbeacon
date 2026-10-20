package net.tropimon.debrisbeacon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.tropimon.debrisbeacon.DebrisBeaconClient;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class DebrisBeaconRenderer {

    private static final float R = 1.0f;
    private static final float G = 0.55f;
    private static final float B = 0.0f;
    private static final float A = 1.0f;

    // Couleur du faisceau des blocs d'habitat (violet)
    private static final float HAB_R = 0.7f;
    private static final float HAB_G = 0.0f;
    private static final float HAB_B = 1.0f;
    private static final int BEAM_HEIGHT = 256;

    private static final int SEARCH_RADIUS = 64;
    private static final int SCAN_INTERVAL = 40;

    // Détection des crystal cores dans l'overworld : ACTIVÉE.
    // Pour la désactiver, il suffit de passer cette valeur à false.
    private static final boolean CRYSTAL_CORE_ENABLED = true;

    // Bloc recherché hors Nether (Tropimon / Cobblemon)
    private static final Identifier CRYSTAL_CORE_ID =
        Identifier.of("cobblemon", "deepslate_crystal_core");

    // Bloc d'habitat camouflé (cherché dans toutes les dimensions)
    private static final Identifier HABITAT_ID =
        Identifier.of("cobblemon", "habitat_block");

    private static List<BlockPos> cachedBlocks = new ArrayList<>();
    private static List<BlockPos> cachedHabitats = new ArrayList<>();
    private static long lastScanTick = -1;
    private static BlockPos lastCenter = null;

    public static List<BlockPos> getCachedBlocks() {
        return cachedBlocks;
    }

    public static int getDebrisCount() {
        return cachedBlocks.size();
    }

    public static void resetCache() {
        cachedBlocks = new ArrayList<>();
        cachedHabitats = new ArrayList<>();
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

        if (lastCenter == null || currentCenter.getManhattanDistance(lastCenter) > SEARCH_RADIUS) {
            lastScanTick = -1;
        }

        if (currentTick - lastScanTick >= SCAN_INTERVAL) {
            scan(world, currentCenter);
            lastScanTick = currentTick;
            lastCenter = currentCenter;
        }

        // Faisceaux violets des habitats (indépendants des débris)
        renderHabitats(context, world);

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

        // --- Texte flottant au milieu du laser, visible à travers les blocs ---
        TextRenderer textRenderer = client.textRenderer;
        MatrixStack matrices = context.matrixStack();

        for (BlockPos pos : cachedBlocks) {
            Vec3d debrisCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dist = debrisCenter.distanceTo(playerEyes);
            String label = (int) dist + "m";

            // Position à 1,5 bloc devant le joueur sur la direction du laser
            Vec3d direction2 = debrisCenter.subtract(playerEyes).normalize();
            Vec3d labelPos = playerEyes.add(direction2.multiply(1.5));

            double dx = labelPos.x - camPos.x;
            double dy = labelPos.y - camPos.y;
            double dz = labelPos.z - camPos.z;

            matrices.push();
            matrices.translate(dx, dy, dz);
            matrices.multiply(camera.getRotation());
            float scale = 0.008f;
            matrices.scale(scale, -scale, scale);

            Matrix4f textMatrix = matrices.peek().getPositionMatrix();
            int textWidth = textRenderer.getWidth(label);

            // SEE_THROUGH = visible à travers les blocs
            textRenderer.draw(label, -textWidth / 2f, 0, 0xFFAA00, false,
                textMatrix, client.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            client.getBufferBuilders().getEntityVertexConsumers().draw();

            matrices.pop();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Faisceaux violets façon ChestTracker : contour du bloc + laser vertical (cœur + aura).
     */
    private static void renderHabitats(WorldRenderContext context, World world) {
        if (cachedHabitats.isEmpty()) return;

        Block habitat = getHabitatBlock();
        if (habitat == null) return;

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();
        Vec3d camPos = context.camera().getPos();
        VertexConsumer lineBuffer = context.consumers().getBuffer(RenderLayer.getLines());

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        List<BlockPos> toRender = new ArrayList<>();

        for (BlockPos pos : cachedHabitats) {
            // Bloc cassé depuis le dernier scan : on n'affiche plus rien
            if (world.getBlockState(pos).getBlock() != habitat) continue;

            context.matrixStack().push();
            context.matrixStack().translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
            WorldRenderer.drawBox(context.matrixStack(), lineBuffer, 0, 0, 0, 1, 1, 1, HAB_R, HAB_G, HAB_B, 1.0f);
            context.matrixStack().pop();

            toRender.add(pos);
        }

        if (!toRender.isEmpty()) {
            Tessellator tessellator = Tessellator.getInstance();

            BufferBuilder innerBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (BlockPos pos : toRender) {
                Matrix4f matrix = new Matrix4f(viewMatrix);
                matrix.translate((float) (pos.getX() - camPos.x), (float) (pos.getY() + 1.0 - camPos.y), (float) (pos.getZ() - camPos.z));
                addFace3D(innerBuilder, matrix, 0.4f, 0.6f, 0, BEAM_HEIGHT, HAB_R, HAB_G, HAB_B, 0.4f);
            }
            BufferRenderer.drawWithGlobalProgram(innerBuilder.end());

            BufferBuilder outerBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (BlockPos pos : toRender) {
                Matrix4f matrix = new Matrix4f(viewMatrix);
                matrix.translate((float) (pos.getX() - camPos.x), (float) (pos.getY() + 1.0 - camPos.y), (float) (pos.getZ() - camPos.z));
                addFace3D(outerBuilder, matrix, 0.3f, 0.7f, 0, BEAM_HEIGHT, HAB_R, HAB_G, HAB_B, 0.15f);
            }
            BufferRenderer.drawWithGlobalProgram(outerBuilder.end());
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void addFace3D(BufferBuilder b, Matrix4f m, float min, float max, float hMin, float hMax, float r, float g, float bl, float a) {
        b.vertex(m, min, hMin, min).color(r, g, bl, a);
        b.vertex(m, max, hMin, min).color(r, g, bl, a);
        b.vertex(m, max, hMax, min).color(r, g, bl, a);
        b.vertex(m, min, hMax, min).color(r, g, bl, a);
        b.vertex(m, min, hMin, max).color(r, g, bl, a);
        b.vertex(m, max, hMin, max).color(r, g, bl, a);
        b.vertex(m, max, hMax, max).color(r, g, bl, a);
        b.vertex(m, min, hMax, max).color(r, g, bl, a);
        b.vertex(m, max, hMin, min).color(r, g, bl, a);
        b.vertex(m, max, hMin, max).color(r, g, bl, a);
        b.vertex(m, max, hMax, max).color(r, g, bl, a);
        b.vertex(m, max, hMax, min).color(r, g, bl, a);
        b.vertex(m, min, hMin, min).color(r, g, bl, a);
        b.vertex(m, min, hMin, max).color(r, g, bl, a);
        b.vertex(m, min, hMax, max).color(r, g, bl, a);
        b.vertex(m, min, hMax, min).color(r, g, bl, a);
    }

    /**
     * Retourne le bloc à chercher selon la dimension :
     * - Nether  -> Ancient Debris (comportement d'origine, inchangé)
     * - ailleurs -> cobblemon:deepslate_crystal_core (seulement si CRYSTAL_CORE_ENABLED)
     * Renvoie null s'il n'y a rien à chercher dans cette dimension.
     */
    private static Block getTargetBlock(World world) {
        if (world.getRegistryKey() == World.NETHER) {
            return Blocks.ANCIENT_DEBRIS;
        }
        if (!CRYSTAL_CORE_ENABLED) return null;
        Block block = Registries.BLOCK.get(CRYSTAL_CORE_ID);
        return block == Blocks.AIR ? null : block;
    }

    private static Block getHabitatBlock() {
        Block block = Registries.BLOCK.get(HABITAT_ID);
        return block == Blocks.AIR ? null : block;
    }

    // Un seul passage sur la zone pour trouver à la fois les débris/cristaux et les habitats
    private static void scan(World world, BlockPos center) {
        List<BlockPos> targets = new ArrayList<>();
        List<BlockPos> habitats = new ArrayList<>();
        Block target = getTargetBlock(world);
        Block habitat = getHabitatBlock();

        if (target != null || habitat != null) {
            BlockPos.iterate(
                center.add(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                center.add(SEARCH_RADIUS,  SEARCH_RADIUS,  SEARCH_RADIUS)
            ).forEach(pos -> {
                Block block = world.getBlockState(pos).getBlock();
                if (target != null && block == target) {
                    targets.add(pos.toImmutable());
                } else if (habitat != null && block == habitat) {
                    habitats.add(pos.toImmutable());
                }
            });
        }

        cachedBlocks = targets;
        cachedHabitats = habitats;
    }
}
