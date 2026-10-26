package com.wehavephones;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/** Flavor hook for the session side menu: Azeroid adds "Paste into game". */
public final class SessionMenuExtras {
    private static final int PASTE_ID = 0x41500001;

    private SessionMenuExtras() {}

    public static void onCreateMenu(XServerDisplayActivity activity, Menu menu) {
        menu.add(Menu.NONE, PASTE_ID, 1, R.string.azeroid_menu_paste).setIcon(R.drawable.icon_keyboard);
    }

    /** Returns true when the item was handled (the drawer then closes). */
    public static boolean onItemSelected(XServerDisplayActivity activity, MenuItem item) {
        if (item.getItemId() != PASTE_ID) return false;
        // Without a keyboard there is no Ctrl+V: type the phone's clipboard into the text box
        // that has focus in the game (tap the box first, e.g. an addon's import field).
        ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(activity) : null;
        if (text == null || text.length() == 0) {
            Toast.makeText(activity, R.string.azeroid_paste_empty, Toast.LENGTH_SHORT).show();
        }
        else if (activity.pasteIntoGame(text.toString())) {
            Toast.makeText(activity, activity.getString(R.string.azeroid_paste_done, text.length()), Toast.LENGTH_SHORT).show();
        }
        else Toast.makeText(activity, R.string.azeroid_paste_not_ready, Toast.LENGTH_SHORT).show();
        return true;
    }
}
