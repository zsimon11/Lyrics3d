package com.example.lyrics3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls Spotify's "currently playing" endpoint and triggers lyric lookups on track change. */
public class SpotifyPoller {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final SpotifyAuth auth;
    private final NowPlaying now;
    private volatile long skipUntilMs = 0;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Lyrics3D-Poller");
        t.setDaemon(true);
        return t;
    });

    public SpotifyPoller(SpotifyAuth auth, NowPlaying now) {
        this.auth = auth;
        this.now = now;
    }

    public void start() {
        exec.scheduleWithFixedDelay(this::poll, 0, 2, TimeUnit.SECONDS);
    }

    private void poll() {
        try {
            if (System.currentTimeMillis() < skipUntilMs) return;
            String token = auth.token();
            if (token == null) return;

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            switch (res.statusCode()) {
                case 204 -> { now.playing = false; now.status = "Nothing playing on Spotify"; return; }
                case 401 -> { auth.invalidate(); return; }
                case 429 -> {
                    long wait = res.headers().firstValueAsLong("Retry-After").orElse(5);
                    skipUntilMs = System.currentTimeMillis() + wait * 1000;
                    return;
                }
                case 200 -> { }
                default -> { return; }
            }

            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonElement itemEl = o.get("item");
            if (itemEl == null || itemEl.isJsonNull()) { now.playing = false; return; }
            JsonObject item = itemEl.getAsJsonObject();
            if (!item.has("artists")) { now.playing = false; now.status = "Podcast/episode - no lyrics"; return; }

            String id = item.get("id").getAsString();
            now.durationMs = item.get("duration_ms").getAsLong();
            now.playing = o.get("is_playing").getAsBoolean();
            now.setProgress(o.get("progress_ms").getAsLong());

            if (!id.equals(now.trackId)) {
                String title = item.get("name").getAsString();
                List<String> names = new ArrayList<>();
                JsonArray arts = item.getAsJsonArray("artists");
                for (JsonElement a : arts) names.add(a.getAsJsonObject().get("name").getAsString());
                String artist = String.join(", ", names);
                String album = item.getAsJsonObject("album").get("name").getAsString();
                long dur = now.durationMs;

                now.title = title;
                now.artist = artist;
                now.album = album;
                now.lyrics = List.of();
                now.status = "Fetching lyrics...";
                now.trackId = id;

                String firstArtist = names.isEmpty() ? "" : names.get(0);
                CompletableFuture.runAsync(() -> {
                    List<LyricLine> lines = LrcLib.fetch(title, firstArtist, album, dur);
                    if (id.equals(now.trackId)) {
                        now.lyrics = lines;
                        now.status = lines.isEmpty() ? "No synced lyrics found" : "";
                    }
                });
            }
        } catch (Exception e) {
            Lyrics3DClient.LOG.warn("Spotify poll failed: {}", e.toString());
        }
    }
}
