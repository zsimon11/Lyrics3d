package com.example.lyrics3d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Draws the lyric lines as billboarded, extruded 3D text around the stage position. */
public final class LyricsRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int GREEN = 0x1DB954;
    private static final int GRAY = 0x9A9A9A;
    private static final double MAX_WIDTH_BLOCKS = 9.0;
    /** Flip to -1f if the extrusion layers appear in front of the text instead of behind it. */
    private static final float EXTRUDE_DIR = -1f;

    private LyricsRenderer() {}

    public static void render(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Lyrics3DClient.enabled) return;

        NowPlaying np = Lyrics3DClient.NOW;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        Vec3 anchor = Lyrics3DClient.STAGE.pos;
        if (anchor == Vec3.ZERO) return;

        float yaw = (float) Math.atan2(cam.x - anchor.x, cam.z - anchor.z);
        PoseStack ps = ctx.poseStack();
        SubmitNodeCollector sc = ctx.submitNodeCollector();
        Font font = mc.font;
        double time = System.nanoTime() / 1.0e9;
        double bob = Math.sin(time * 1.5) * 0.04;

        // Track header
        if (!np.title.isEmpty()) {
            draw(ps, sc, font, anchor, cam, yaw, 2.1 + bob, 0.028f, np.title + " - " + np.artist, GRAY, 0xE0, false);
        }

        List<LyricLine> lines = np.lyrics;
        if (lines.isEmpty()) {
            if (!np.status.isEmpty())
                draw(ps, sc, font, anchor, cam, yaw, 1.0 + bob, 0.045f, np.status, GREEN, 0xFF, true);
            return;
        }

        long progress = np.progressMs() + Lyrics3DClient.config.lyricOffsetMs;
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= progress) idx = i; else break;
        }

        // Smooth scroll: glide from the previous line to the current one over 350ms.
        double smooth = idx;
        if (idx >= 0) {
            double since = progress - lines.get(idx).timeMs();
            double t = Math.min(1.0, since / 350.0);
            double ease = 1.0 - Math.pow(1.0 - t, 3);
            smooth = (idx - 1) + ease;
        } else {
            smooth = -1;
        }

        for (int i = Math.max(0, idx - 2); i <= Math.min(lines.size() - 1, idx + 3); i++) {
            double rel = i - smooth;
            double w = Math.max(0.0, 1.0 - Math.abs(rel));
            float scale = (float) (0.028 + 0.030 * w);
            int alpha = (int) (255 * Math.max(0.15, 1.0 - Math.abs(rel) * 0.28));
            int rgb = lerpColor(GRAY, GREEN, w);
            draw(ps, sc, font, anchor, cam, yaw, 1.0 - rel * 0.75 + bob, scale,
                    lines.get(i).text(), rgb, alpha, w > 0.5);
        }
    }

    private static void draw(PoseStack ps, SubmitNodeCollector sc, Font font, Vec3 anchor, Vec3 cam,
                             float yaw, double yOff, float scale, String text, int rgb, int alpha, boolean extrude) {
        float width = font.width(text);
        if (width * scale > MAX_WIDTH_BLOCKS) scale = (float) (MAX_WIDTH_BLOCKS / width);
        FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
        float x = -width / 2f;

        ps.pushPose();
        ps.translate(anchor.x - cam.x, anchor.y - cam.y + yOff, anchor.z - cam.z);
        ps.mulPose(Axis.YP.rotation(yaw));
        ps.scale(scale, -scale, scale);

        if (extrude) {
            int dark = ((alpha & 0xFF) << 24) | (darken(rgb, 0.35f) & 0xFFFFFF);
            for (int d = 4; d >= 1; d--) {
                ps.pushPose();
                ps.translate(0f, 0f, EXTRUDE_DIR * d * 0.5f);
                sc.submitText(ps, x, 0f, seq, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, dark, 0, 0);
                ps.popPose();
            }
        }
        int argb = ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
        sc.submitText(ps, x, 0f, seq, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, argb, 0, 0);
        ps.popPose();
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int darken(int rgb, float f) {
        return (((int) (((rgb >> 16) & 0xFF) * f)) << 16)
             | (((int) (((rgb >> 8) & 0xFF) * f)) << 8)
             | ((int) ((rgb & 0xFF) * f));
    }
}
