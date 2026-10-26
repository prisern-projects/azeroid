package com.wehavephones.core;

import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Logs frame pacing so performance changes can be measured instead of guessed.
 * Every 30 seconds it appends one line to frametimes.log: average FPS, 1% and
 * 0.1% lows (the FPS of the slowest frames, which is what stutter feels like),
 * the worst frame and how many frames took longer than 50 ms. When the session
 * ends it appends a summary over the whole session. Idle windows (under 5 FPS,
 * e.g. Battle.net sitting still) are skipped so they don't drag the numbers down.
 *
 * onFrame() runs on the render thread and only stores one interval.
 */
public final class FrameTimeLogger {
    public static final String FILE_NAME = "frametimes.log";
    /**
     * Frames the game handed to the X server (Present requests). Compared with
     * the frames drawn on screen it shows where the limit is: game far above
     * screen means the display path drops frames, equal means the game itself
     * (CPU/GPU) is the limit.
     */
    public static final java.util.concurrent.atomic.AtomicInteger GAME_PRESENTS = new java.util.concurrent.atomic.AtomicInteger();
    private static final long WINDOW_MS = 30_000;
    private static final int MAX_FRAMES = 30 * 240;
    private static final int SESSION_BUCKETS = 1000;

    private static volatile String tag = "";
    private static volatile String extraTag = "";

    /** A second label for settings that are not CPU placement (e.g. "tex=native"). */
    public static void setExtraTag(String value) {
        extraTag = value != null ? value : "";
    }

    private static String tags() {
        return extraTag.isEmpty() ? tag : tag + " " + extraTag;
    }

    /** A short label (e.g. "cpu=prime") added to every line, for A/B comparisons. */
    public static void setTag(String value) {
        tag = value != null ? value : "";
    }

    private final File file;
    private final Executor writer = Executors.newSingleThreadExecutor();
    private final String label;

    private int[] intervals = new int[MAX_FRAMES];
    private int count = 0;
    private long lastFrameNanos = 0;
    private long windowStartMs = 0;

    // Whole-session histogram of frame times in 0.1 ms buckets (last bucket: 100 ms and more).
    private final long[] sessionHistogram = new long[SESSION_BUCKETS];
    private long sessionFrames = 0;
    private long sessionNanos = 0;

    public FrameTimeLogger(File logDir, String label) {
        this.file = new File(logDir, FILE_NAME);
        this.label = label != null ? label : "";
    }

    public void onFrame() {
        long now = System.nanoTime();
        long nowMs = SystemClock.elapsedRealtime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            windowStartMs = nowMs;
            return;
        }
        long interval = now - lastFrameNanos;
        lastFrameNanos = now;
        if (count < MAX_FRAMES) intervals[count++] = (int)Math.min(interval, Integer.MAX_VALUE);

        if (nowMs - windowStartMs >= WINDOW_MS) {
            final int[] window = Arrays.copyOf(intervals, count);
            final long windowMs = nowMs - windowStartMs;
            final int presents = GAME_PRESENTS.getAndSet(0);
            count = 0;
            windowStartMs = nowMs;
            writer.execute(() -> flushWindow(window, windowMs, presents));
        }
    }

    /** Appends the session summary; call once when the session ends. */
    public void finish() {
        final int[] window = Arrays.copyOf(intervals, count);
        final long windowMs = SystemClock.elapsedRealtime() - windowStartMs;
        final int presents = GAME_PRESENTS.getAndSet(0);
        count = 0;
        writer.execute(() -> {
            if (window.length > 0) flushWindow(window, windowMs, presents);
            if (sessionFrames == 0) return;
            append("SESSION " + label + " " + tags() + " " + timestamp()
                    + String.format(Locale.US, " frames=%d avg=%.1ffps 1%%low=%.1ffps 0.1%%low=%.1ffps",
                            sessionFrames,
                            sessionFrames * 1e9 / sessionNanos,
                            1000.0 / sessionPercentileMs(0.99),
                            1000.0 / sessionPercentileMs(0.999)));
        });
    }

    private void flushWindow(int[] window, long windowMs, int presents) {
        if (window.length == 0) return;
        double fps = window.length * 1000.0 / windowMs;
        if (fps < 5) return;

        long total = 0;
        int spikes = 0;
        int worst = 0;
        for (int interval : window) {
            total += interval;
            if (interval > 50_000_000) spikes++;
            worst = Math.max(worst, interval);
            int bucket = Math.min(interval / 100_000, SESSION_BUCKETS - 1);
            sessionHistogram[bucket]++;
        }
        sessionFrames += window.length;
        sessionNanos += total;

        int[] sorted = window.clone();
        Arrays.sort(sorted);
        double p50 = sorted[(int)(sorted.length * 0.50)] / 1e6;
        double p99 = sorted[Math.min(sorted.length - 1, (int)(sorted.length * 0.99))] / 1e6;
        double p999 = sorted[Math.min(sorted.length - 1, (int)(sorted.length * 0.999))] / 1e6;
        append(String.format(Locale.US,
                "%s %s %s avg=%.1ffps game=%.1ffps median=%.1fms 1%%low=%.1ffps 0.1%%low=%.1ffps worst=%.0fms spikes50ms=%d mem=%s",
                timestamp(), label, tags(), window.length * 1e9 / total, presents * 1000.0 / windowMs, p50, 1000 / p99, 1000 / p999,
                worst / 1e6, spikes, memAvailable()));
    }

    private double sessionPercentileMs(double percentile) {
        long target = (long)(sessionFrames * percentile);
        long seen = 0;
        for (int i = 0; i < SESSION_BUCKETS; i++) {
            seen += sessionHistogram[i];
            if (seen > target) return (i + 1) / 10.0;
        }
        return SESSION_BUCKETS / 10.0;
    }

    private void append(String line) {
        try (FileWriter out = new FileWriter(file, true)) {
            out.write(line);
            out.write('\n');
        }
        catch (IOException ignored) {}
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String memAvailable() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024 + "MB";
                }
            }
        }
        catch (IOException | NumberFormatException ignored) {}
        return "?";
    }
}
