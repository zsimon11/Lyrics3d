package com.example.lyrics3d;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class Lyrics3DClient implements ClientModInitializer {
    public static final String MOD_ID = "lyrics3d";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Config config;
    public static SpotifyAuth auth;
    public static final NowPlaying NOW = new NowPlaying();
    public static final Stage STAGE = new Stage();
    public static volatile boolean enabled = true;

    private static KeyMapping stageKey, loginKey, toggleKey;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        auth = new SpotifyAuth(config);
        if (auth.isLinked()) NOW.status = "Waiting for Spotify...";
        new SpotifyPoller(auth, NOW).start();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        stageKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.lyrics3d.stage", InputConstants.Type.KEYSYM, InputConstants.KEY_G, cat));
        loginKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.lyrics3d.login", InputConstants.Type.KEYSYM, InputConstants.KEY_H, cat));
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.lyrics3d.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_J, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            STAGE.tick(client);
            while (stageKey.consumeClick()) {
                STAGE.togglePin();
                say(STAGE.pinned ? "Lyrics pinned here. Press G to make them follow you again."
                                 : "Lyrics will follow you.");
            }
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                say(enabled ? "Lyrics on." : "Lyrics off.");
            }
            while (loginKey.consumeClick()) login();
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(LyricsRenderer::render);
    }

    private static void login() {
        if (config.clientId == null || config.clientId.isBlank()) {
            say("Set \"clientId\" in config/lyrics3d.json (Spotify dashboard app), then restart.");
            return;
        }
        try {
            URI uri = auth.beginLogin(err -> say(err == null ? "Spotify connected!" : "Spotify login failed: " + err));
            Minecraft mc = Minecraft.getInstance();
            try { net.minecraft.Util.getPlatform().openUri(uri); } catch (Throwable t) { LOG.warn("Could not open browser", t); }
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("[Lyrics3D] Click here to log in to Spotify")
                        .withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(uri)).withUnderlined(true)));
            }
        } catch (Exception e) {
            say("Could not start login: " + e.getMessage() + " (is port " + config.port + " free?)");
        }
    }

    public static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Lyrics3D] " + text));
        });
    }
}
