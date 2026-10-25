package com.wehavephones.mobile;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * Azeroid 0.1.x ran on a different engine (Winlator Ludashi) under the same package name. Its files sit where
 * this engine keeps its own (imagefs, containers, preferences), so the first start after updating from 0.1.x
 * wipes the app's private data once. No game files are lost: 0.1.x never got as far as installing WoW.
 */
final class LegacyAzeroidCleanup {
    private static final String TAG = "AzeroidCleanup";

    private LegacyAzeroidCleanup() {}

    static void runIfNeeded(Context context) {
        File dataDir = context.getApplicationInfo().dataDir != null ? new File(context.getApplicationInfo().dataDir) : null;
        if (dataDir == null) return;
        File legacyPrefs = new File(dataDir, "shared_prefs/azeroid.xml");
        if (!legacyPrefs.exists()) return;

        Log.i(TAG, "Removing data from Azeroid 0.1.x");
        clear(context.getFilesDir());
        clear(context.getCacheDir());
        clear(new File(dataDir, "shared_prefs"));
        clear(new File(dataDir, "app_webview"));
    }

    private static void clear(File dir) {
        File[] children = dir != null ? dir.listFiles() : null;
        if (children == null) return;
        for (File child : children) delete(child);
    }

    /** Deletes without following symbolic links (the old environments link to shared storage). */
    private static void delete(File file) {
        if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) clear(file);
        if (!file.delete()) Log.w(TAG, "Could not delete " + file);
    }
}
