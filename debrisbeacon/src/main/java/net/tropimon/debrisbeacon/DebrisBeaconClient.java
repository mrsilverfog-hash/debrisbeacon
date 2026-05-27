package net.tropimon.debrisbeacon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.tropimon.debrisbeacon.render.DebrisBeaconRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DebrisBeaconClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    public static boolean enabled = false;

    private static String message = "";
    private static long messageExpireTime = 0;
    private static final long MESSAGE_DURATION_MS = 2000;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "DebrisBeacon On/Off",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "DebrisBeacon"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                DebrisBeaconRenderer.resetCache();
                message = enabled ? "§aDebrisBeacon : Activé" : "§cDebrisBeacon : Désactivé";
                messageExpireTime = System.currentTimeMillis() + MESSAGE_DURATION_MS;
            }
        });

        WorldRenderEvents.LAST.register(DebrisBeaconRenderer::onWorldRenderLast);

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            TextRenderer textRenderer = client.textRenderer;
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // Message activation/désactivation
            if (System.currentTimeMillis() < messageExpireTime && !message.isEmpty()) {
                Text text = Text.literal(message);
                int textWidth = textRenderer.getWidth(text);
                drawContext.drawTextWithShadow(textRenderer, text,
                    (screenWidth - textWidth) / 2, screenHeight / 2 + 30, 0xFFFFFF);
            }

            // Distances triées affichées sur le HUD
            if (enabled) {
                List<BlockPos> blocks = DebrisBeaconRenderer.getCachedBlocks();
                if (blocks.isEmpty()) return;

                Vec3d eyes = client.player.getEyePos();

                // Trier par distance croissante
                List<double[]> distances = new ArrayList<>();
                for (BlockPos pos : blocks) {
                    Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double dist = center.distanceTo(eyes);
                    distances.add(new double[]{dist});
                }
                distances.sort(Comparator.comparingDouble(d -> d[0]));

                // Afficher en bas au centre, max 5 distances
                int maxShow = Math.min(5, distances.size());
                int startY = screenHeight - 40 - (maxShow * 12);

                drawContext.drawTextWithShadow(textRenderer,
                    Text.literal("§6Débris :"), screenWidth / 2 - 25, startY - 12, 0xFFFFFF);

                for (int i = 0; i < maxShow; i++) {
                    int dist = (int) distances.get(i)[0];
                    String label = "§f• §e" + dist + "m";
                    int textWidth = textRenderer.getWidth(Text.literal(label));
                    drawContext.drawTextWithShadow(textRenderer,
                        Text.literal(label), screenWidth / 2 - textWidth / 2,
                        startY + i * 12, 0xFFFFFF);
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DebrisBeaconRenderer.resetCache();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DebrisBeaconRenderer.resetCache();
        });
    }
}
