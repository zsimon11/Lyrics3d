package com.example.lyrics3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches time-synced (LRC) lyrics from https://lrclib.net */
public class LrcLib {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Lyrics3D-Minecraft-Mod/1.0")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    public static List<LyricLine> fetch(String title, String artist, String album, long durationMs) {
        try {
            String url = "https://lrclib.net/api/get?track_name=" + enc(title)
                    + "&artist_name=" + enc(artist)
                    + "&album_name=" + enc(album)
                    + "&duration=" + (durationMs / 1000);
            String body = get(url);
            if (body != null) {
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull())
                    return parse(o.get("syncedLyrics").getAsString());
            }
            // Fallback: fuzzy search
            body = get("https://lrclib.net/api/search?q=" + enc(artist + " " + title));
            if (body != null) {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull())
                        return parse(o.get("syncedLyrics").getAsString());
                }
            }
        } catch (Exception e) {
            Lyrics3DClient.LOG.warn("Lyrics fetch failed: {}", e.toString());
        }
        return List.of();
    }

    static List<LyricLine> parse(String lrc) {
        List<LyricLine> out = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long ms = (long) (Integer.parseInt(m.group(1)) * 60_000L + Double.parseDouble(m.group(2)) * 1000);
                times.add(ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new LyricLine(t, text.isEmpty() ? "♪" : text));
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return out;
    }
}
