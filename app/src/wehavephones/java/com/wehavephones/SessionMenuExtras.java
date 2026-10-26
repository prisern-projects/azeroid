package com.wehavephones;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.wehavephones.container.Container;
import com.wehavephones.core.FileUtils;
import com.wehavephones.mobile.WeHavePhonesLaunchPlan;
import com.wehavephones.mobile.WeHavePhonesPaths;
import com.wehavephones.winhandler.WinHandler;
import com.wehavephones.xserver.ClipboardLog;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** Flavor hook for the session side menu: Azeroid adds "Paste into game". */
public final class SessionMenuExtras {
    private static final int PASTE_ID = 0x41500001;
    private static final String CLIP_FILE = "azeroid-clip.txt";
    private static final String CLIP_BAT = "azeroid-paste.bat";

    private SessionMenuExtras() {}

    public static void onCreateMenu(XServerDisplayActivity activity, Menu menu) {
        menu.add(Menu.NONE, PASTE_ID, 1, R.string.azeroid_menu_paste).setIcon(R.drawable.icon_keyboard);
    }

    /** Returns true when the item was handled (the drawer then closes). */
    public static boolean onItemSelected(XServerDisplayActivity activity, MenuItem item) {
        if (item.getItemId() != PASTE_ID) return false;
        paste(activity);
        return true;
    }

    /**
     * Wine's own clip.exe puts the phone's clipboard on the Windows clipboard inside Wine (as copying
     * on a PC would), then one Ctrl+V pastes it into the focused game text box. Android only lets the
     * focused app read the clipboard, so this runs on the UI thread first.
     */
    private static void paste(XServerDisplayActivity activity) {
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(activity) : null;
        Container container = activity.getContainer();
        WinHandler winHandler = activity.getWinHandler();
        if (text == null || text.length() == 0) {
            Toast.makeText(activity, R.string.azeroid_paste_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (container == null || winHandler == null) return;
        final String value = text.toString();
        Toast.makeText(activity, R.string.azeroid_paste_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File driveC = WeHavePhonesPaths.driveC(container);
            boolean written = FileUtils.write(new File(driveC, CLIP_FILE), value.getBytes(StandardCharsets.UTF_8))
                    && FileUtils.write(new File(driveC, CLIP_BAT),
                    ("@echo off\r\nclip < C:\\" + CLIP_FILE + "\r\n").getBytes(StandardCharsets.UTF_8));
            ClipboardLog.log("Paste via clip.exe: " + value.length() + " chars, files written=" + written);
            if (!written) return;
            winHandler.exec(WeHavePhonesLaunchPlan.winHandlerCommand("C:\\" + CLIP_BAT));
            // clip.exe needs a moment to start and read a large string before Ctrl+V can find it.
            long waitMs = 1500 + Math.min(4000, value.length() / 200);
            activity.runOnUiThread(() -> activity.getWindow().getDecorView().postDelayed(activity::pressPasteKeys, waitMs));
        }, "AzeroidPaste").start();
    }
}
