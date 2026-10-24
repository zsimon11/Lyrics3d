package com.example.lyrics3d;

import java.util.List;

/** Shared playback state, written by the poller thread and read by the render thread. */
public class NowPlaying {
    public volatile String trackId = "";
    public volatile String title = "";
    public volatile String artist = "";
    public volatile String album = "";
    public volatile long durationMs = 0;
    public volatile boolean playing = false;
    public volatile List<LyricLine> lyrics = List.of();
    public volatile String status = "Press the Connect key (H) to link Spotify";

    private volatile long progressAtFetch = 0;
    private volatile long fetchedAtNanos = System.nanoTime();

    public void setProgress(long progressMs) {
        progressAtFetch = progressMs;
        fetchedAtNanos = System.nanoTime();
    }

    /** Interpolated playback position, so lyrics stay smooth between API polls. */
    public long progressMs() {
        if (!playing) return progressAtFetch;
        long elapsed = (System.nanoTime() - fetchedAtNanos) / 1_000_000L;
        return Math.min(progressAtFetch + elapsed, durationMs > 0 ? durationMs : Long.MAX_VALUE);
    }
}
