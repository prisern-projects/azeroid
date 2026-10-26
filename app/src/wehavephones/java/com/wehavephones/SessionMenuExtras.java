package com.wehavephones;

import android.view.Menu;
import android.view.MenuItem;

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
        // No keyboard means no Ctrl+V: send it once the drawer is gone and the game has focus again.
        activity.getWindow().getDecorView().postDelayed(activity::pasteIntoGame, 400);
        return true;
    }
}
