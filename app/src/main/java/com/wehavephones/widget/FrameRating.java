package com.wehavephones.widget;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.wehavephones.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    private long lastTime = 0;
    private int frameCount = 0;
    private float lastFPS = 0;
    private long lastFrameTime = 0;
    private long maxFrameInterval = 0;
    private long lastMaxFrameInterval = 0;
    private boolean showDetails = false;
    private final TextView textView;
    private final TextView detailsView;

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        textView = view.findViewById(R.id.TVFPS);
        detailsView = view.findViewById(R.id.TVPerfDetails);
        addView(view);
    }

    /** Stacks frame time, free memory and battery temperature under the FPS. */
    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
        detailsView.setVisibility(showDetails ? View.VISIBLE : View.GONE);
    }

    public void update() {
        if (lastTime == 0) lastTime = SystemClock.elapsedRealtime();
        long time = SystemClock.elapsedRealtime();
        if (lastFrameTime != 0) maxFrameInterval = Math.max(maxFrameInterval, time - lastFrameTime);
        lastFrameTime = time;
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            lastMaxFrameInterval = maxFrameInterval;
            post(this);
            lastTime = time;
            frameCount = 0;
            maxFrameInterval = 0;
        }

        frameCount++;
    }

    @Override
    public void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        textView.setText(String.format(Locale.ENGLISH, "%.0f", lastFPS));
        if (showDetails) detailsView.setText(formatDetails());
    }

    /** One line: frame time (worst), battery % (⚡ while charging), battery temperature, free RAM. */
    private String formatDetails() {
        StringBuilder sb = new StringBuilder();
        if (lastFPS > 0) {
            sb.append(String.format(Locale.ENGLISH, "%.1f ms (max %d)", 1000f / lastFPS, lastMaxFrameInterval));
        }
        Intent battery = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            if (level >= 0 && scale > 0) {
                separator(sb);
                if (charging) sb.append('\u26A1');
                sb.append(Math.round(level * 100f / scale)).append('%');
            }
            int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temperature != Integer.MIN_VALUE) {
                separator(sb);
                sb.append(String.format(Locale.ENGLISH, "%.0f\u00B0C", temperature / 10f));
            }
        }
        long availableMB = readMemAvailableMB();
        if (availableMB >= 0) {
            separator(sb);
            sb.append(String.format(Locale.ENGLISH, "RAM %.1f GB", availableMB / 1024f));
        }
        return sb.toString();
    }

    private static void separator(StringBuilder sb) {
        if (sb.length() > 0) sb.append("  \u00B7  ");
    }

    private static long readMemAvailableMB() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024;
                }
            }
        }
        catch (Exception e) {}
        return -1;
    }
}