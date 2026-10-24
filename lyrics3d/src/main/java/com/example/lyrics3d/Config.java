package com.example.lyrics3d;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    /** Your Spotify app Client ID (https://developer.spotify.com/dashboard). */
    public String clientId = "";
    /** Must match a Redirect URI registered in your Spotify app: http://127.0.0.1:<port>/callback */
    public int port = 8888;
    /** Shift lyric timing (ms). Positive = lyrics appear earlier. */
    public long lyricOffsetMs = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("lyrics3d.json"); }

    public static Config load() {
        try {
            Path p = path();
            if (Files.exists(p)) return GSON.fromJson(Files.readString(p), Config.class);
            Config c = new Config();
            Files.writeString(p, GSON.toJson(c));
            return c;
        } catch (Exception e) {
            Lyrics3DClient.LOG.error("Config load failed", e);
            return new Config();
        }
    }
}
