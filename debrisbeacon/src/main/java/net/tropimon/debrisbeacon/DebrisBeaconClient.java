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
import net.tropimon.debrisbeacon.render.DebrisBeaconRenderer;
import org.lwjgl.glfw.GLFW;

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
            if (System.currentTimeMillis() < messageExpireTime && !message.isEmpty()) {
                MinecraftClient client = MinecraftClient.getInstance();
                TextRenderer textRenderer = client.textRenderer;
                int screenWidth = client.getWindow().getScaledWidth();
                int screenHeight = client.getWindow().getScaledHeight();
                Text text = Text.literal(message);
                int textWidth = textRenderer.getWidth(text);
                drawContext.drawTextWithShadow(textRenderer, text,
                    (screenWidth - textWidth) / 2, screenHeight / 2 + 30, 0xFFFFFF);
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
