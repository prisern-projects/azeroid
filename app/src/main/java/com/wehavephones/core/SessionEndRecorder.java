package com.wehavephones.core;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Records how a Wine session ended so a sudden "back to the app" can be told
 * apart: a kill for memory (SIGKILL, low swap) versus a crash (SIGSEGV,
 * SIGABRT) versus a clean exit. Wine runs with WINEDEBUG=-all, so this is the
 * only trace a crash leaves.
 */
public final class SessionEndRecorder {
    private static final String PREFS = "azeroid_session_end";
    private static final String KEY_LAST = "last";

    private SessionEndRecorder() {}

    /** One line describing the exit status and memory state at the moment of exit. */
    public static String describe(int status) {
        StringBuilder line = new StringBuilder();
        line.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        line.append(" status=").append(status);
        if (status > 128 && status < 128 + 65) {
            int signal = status - 128;
            line.append(" (signal ").append(signal).append(' ').append(signalName(signal)).append(')');
        }
        else if (status == 0) line.append(" (clean exit)");
        line.append(' ').append(memorySummary());
        return line.toString();
    }

    public static void record(Context context, int status) {
        if (context == null) return;
        String line = describe(status);
        // commit(): the app restarts itself with Runtime.exit right after a session ends, which drops apply().
        prefs(context).edit().putString(KEY_LAST, line).commit();
    }

    public static String lastSummary(Context context) {
        String last = prefs(context).getString(KEY_LAST, "");
        return last.isEmpty() ? "No session end recorded yet" : last;
    }

    /** Android's own record of why this app's processes died (low memory, crash, ...). */
    @android.annotation.SuppressLint("NewApi")
    public static String androidExitReasons(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return "unavailable before Android 11";
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return "unavailable";
        List<ApplicationExitInfo> infos;
        try {
            infos = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5);
        }
        catch (RuntimeException e) {
            return "unavailable: " + e.getMessage();
        }
        if (infos.isEmpty()) return "None recorded";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder builder = new StringBuilder();
        for (ApplicationExitInfo info : infos) {
            builder.append(format.format(new Date(info.getTimestamp())))
                    .append(' ').append(reasonName(info.getReason()))
                    .append(" status=").append(info.getStatus())
                    .append(" importance=").append(info.getImportance())
                    .append(" pss=").append(info.getPss() / 1024).append("MB")
                    .append(" rss=").append(info.getRss() / 1024).append("MB");
            String description = info.getDescription();
            if (description != null && !description.isEmpty()) builder.append(" \"").append(description).append('"');
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String memorySummary() {
        long available = -1, swapFree = -1, swapTotal = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) available = kb(line);
                else if (line.startsWith("SwapFree:")) swapFree = kb(line);
                else if (line.startsWith("SwapTotal:")) swapTotal = kb(line);
            }
        }
        catch (IOException ignored) {}
        return "MemAvailable=" + available / 1024 + "MB SwapFree=" + swapFree / 1024 + "/" + swapTotal / 1024 + "MB";
    }

    private static long kb(String line) {
        try {
            return Long.parseLong(line.replaceAll("[^0-9]", ""));
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String signalName(int signal) {
        switch (signal) {
            case 6: return "SIGABRT";
            case 7: return "SIGBUS";
            case 9: return "SIGKILL";
            case 11: return "SIGSEGV";
            case 15: return "SIGTERM";
            default: return "";
        }
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.R)
    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_FREEZER: return "FREEZER";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            default: return "reason" + reason;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
