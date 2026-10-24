package com.example.lyrics3d;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Spotify Authorization Code flow with PKCE (no client secret needed). */
public class SpotifyAuth {
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final SecureRandom RNG = new SecureRandom();

    private final Config cfg;
    private final Path tokenFile = FabricLoader.getInstance().getConfigDir().resolve("lyrics3d_token.json");
    private volatile String accessToken;
    private volatile long expiresAtMs;
    private volatile String refreshToken;
    private HttpServer server;
    private String verifier;

    public SpotifyAuth(Config cfg) {
        this.cfg = cfg;
        try {
            if (Files.exists(tokenFile)) {
                JsonObject o = JsonParser.parseString(Files.readString(tokenFile)).getAsJsonObject();
                if (o.has("refresh_token")) refreshToken = o.get("refresh_token").getAsString();
            }
        } catch (Exception e) {
            Lyrics3DClient.LOG.warn("Could not read saved token: {}", e.toString());
        }
    }

    public boolean isLinked() { return refreshToken != null; }

    private String redirectUri() { return "http://127.0.0.1:" + cfg.port + "/callback"; }

    /** Returns a valid access token (refreshing if needed), or null if not logged in. */
    public synchronized String token() {
        if (refreshToken == null) return null;
        try {
            if (accessToken == null || System.currentTimeMillis() > expiresAtMs - 30_000) refresh();
        } catch (Exception e) {
            Lyrics3DClient.LOG.warn("Token refresh failed: {}", e.toString());
            return null;
        }
        return accessToken;
    }

    public synchronized void invalidate() { expiresAtMs = 0; }

    /** Starts the local callback server and returns the URL the user must open. */
    public synchronized URI beginLogin(Consumer<String> onResult) throws Exception {
        stopServer();
        verifier = randomString(64);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = randomString(16);

        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), cfg.port), 0);
        server.createContext("/callback", ex -> {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String msg;
            try {
                if (!state.equals(q.get("state"))) throw new IllegalStateException("state mismatch");
                if (q.get("code") == null) throw new IllegalStateException(q.getOrDefault("error", "no code"));
                exchange(q.get("code"));
                msg = "Spotify connected! You can close this tab and return to Minecraft.";
                onResult.accept(null);
            } catch (Exception e) {
                msg = "Login failed: " + String.valueOf(e.getMessage()).replace("<", "&lt;");
                onResult.accept(e.getMessage());
            }
            byte[] body = ("<html><body style='font-family:sans-serif;background:#121212;color:#1DB954;"
                    + "text-align:center;padding-top:20vh'><h2>" + msg + "</h2></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
            new Thread(this::stopServer).start();
        });
        server.start();

        Map<String, String> p = new LinkedHashMap<>();
        p.put("client_id", cfg.clientId);
        p.put("response_type", "code");
        p.put("redirect_uri", redirectUri());
        p.put("scope", SCOPES);
        p.put("state", state);
        p.put("code_challenge_method", "S256");
        p.put("code_challenge", challenge);
        return URI.create("https://accounts.spotify.com/authorize?" + form(p));
    }

    private synchronized void stopServer() {
        if (server != null) { server.stop(0); server = null; }
    }

    private void exchange(String code) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("client_id", cfg.clientId);
        p.put("grant_type", "authorization_code");
        p.put("code", code);
        p.put("redirect_uri", redirectUri());
        p.put("code_verifier", verifier);
        apply(postToken(form(p)));
    }

    private void refresh() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("client_id", cfg.clientId);
        p.put("grant_type", "refresh_token");
        p.put("refresh_token", refreshToken);
        try {
            apply(postToken(form(p)));
        } catch (IOException e) {
            if (String.valueOf(e.getMessage()).contains("invalid_grant")) {
                refreshToken = null;
                Files.deleteIfExists(tokenFile);
            }
            throw e;
        }
    }

    private JsonObject postToken(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private void apply(JsonObject o) throws IOException {
        accessToken = o.get("access_token").getAsString();
        expiresAtMs = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L;
        if (o.has("refresh_token")) {
            refreshToken = o.get("refresh_token").getAsString();
            JsonObject save = new JsonObject();
            save.addProperty("refresh_token", refreshToken);
            Files.writeString(tokenFile, save.toString());
        }
    }

    private static String randomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(RNG.nextInt(chars.length())));
        return sb.toString();
    }

    private static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }
}
