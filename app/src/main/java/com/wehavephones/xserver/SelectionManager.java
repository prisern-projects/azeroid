package com.wehavephones.xserver;

import android.util.SparseArray;

import com.wehavephones.xserver.events.SelectionClear;

public class SelectionManager implements XResourceManager.OnResourceLifecycleListener {
    private final SparseArray<Selection> selections = new SparseArray<>();

    public SelectionManager(WindowManager windowManager) {
        windowManager.addOnResourceLifecycleListener(this);
    }

    public static class Selection {
        public Window owner;
        private XClient client;
        /** Text served by the X server itself (the phone's clipboard); client is then null. */
        public String serverText;

        public boolean isServerOwned() {
            return owner != null && client == null && serverText != null;
        }

        public XClient getClient() {
            return client;
        }
    }

    public void setSelection(int atom, Window owner, XClient client, int timestamp) {
        Selection selection = getSelection(atom);
        // SelectionClear names the window that lost the selection.
        if (selection.owner != null && selection.client != null && (owner == null || selection.client != client)) {
            selection.client.sendEvent(new SelectionClear(timestamp, selection.owner, atom));
        }
        selection.owner = owner;
        selection.client = client;
        selection.serverText = null;
    }

    /** The X server takes CLIPBOARD with this text, so X clients (Wine) can paste the phone's clipboard. */
    public void setServerText(int atom, Window root, String text, int timestamp) {
        setSelection(atom, root, null, timestamp);
        getSelection(atom).serverText = text;
    }

    public Selection getSelection(int atom) {
        Selection selection = selections.get(atom);
        if (selection != null) return selection;
        selection = new Selection();
        selections.put(atom, selection);
        return selection;
    }

    @Override
    public void onFreeResource(XResource resource) {
        for (int i = 0; i < selections.size(); i++) {
            Selection selection = selections.valueAt(i);
            if (selection.owner == resource) selection.owner = null;
        }
    }
}
