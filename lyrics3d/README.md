# Lyrics3D (Fabric, Minecraft 26.2)

Floating, extruded 3D Spotify lyrics in your world. Time-synced lyrics come from LRCLIB (lrclib.net).

## Requirements
- JDK 25, Gradle 9.5.1 (run `gradle wrapper --gradle-version 9.5.1` once, or open in IntelliJ)
- Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API 0.152+

## Spotify setup (one time)
1. https://developer.spotify.com/dashboard -> Create app.
2. Add Redirect URI exactly: `http://127.0.0.1:8888/callback`
3. Copy the Client ID into `config/lyrics3d.json` (created on first launch) -> `"clientId"`.
4. In game press **H**, approve in the browser. The refresh token is saved to `config/lyrics3d_token.json`.
(Spotify requires a Premium account for API access on new apps in development mode.)

## Keys
- H: connect Spotify
- G: pin lyrics where they are / make them follow you
- J: toggle lyrics

## Build
`./gradlew build` -> `build/libs/lyrics3d-1.0.0.jar`

## Tweaks
- `lyricOffsetMs` in the config shifts timing.
- If extrusion layers appear in front of the text, set EXTRUDE_DIR to 1f in LyricsRenderer.
- If text is mirrored/upside-down, adjust `ps.scale(scale, -scale, scale)` in LyricsRenderer.draw.

## No local setup? Build on GitHub
1. Create a new GitHub repo and upload this folder's contents (including the hidden `.github` folder).
2. Open the repo's **Actions** tab, wait for "Build mod" to finish (~2-3 min).
3. Open the run, download the **lyrics3d-jar** artifact, unzip it, and drop the `.jar` into your `mods` folder (with Fabric API).
