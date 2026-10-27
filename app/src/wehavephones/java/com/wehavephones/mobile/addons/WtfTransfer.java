package com.wehavephones.mobile.addons;

import com.wehavephones.mobile.addons.install.FileOps;

import java.io.File;
import java.io.IOException;

/**
 * Moves the game's WTF folder (add-on settings, keybinds, macros) between the game and the
 * phone's Download folder, so settings can be carried over from a PC.
 */
public final class WtfTransfer {
    /** Graphics and window settings: the phone keeps its own, a PC's would break the display. */
    static final String CONFIG_FILE = "Config.wtf";

    private WtfTransfer() {}

    /**
     * Backs up {@code wtf} to {@code backup}, then merges {@code source} into it. Folder names
     * are matched ignoring case, since Wine treats {@code Account} and {@code ACCOUNT} as one.
     * Returns the number of files copied.
     */
    public static int importInto(File source, File wtf, File backup) throws IOException {
        if (!source.isDirectory()) throw new IOException("No WTF folder at " + source);
        if (wtf.isDirectory()) {
            if (!FileOps.deleteRecursively(backup)) throw new IOException("Cannot replace " + backup);
            FileOps.copyRecursively(wtf, backup);
        }
        else if (!wtf.mkdirs()) throw new IOException("Cannot create " + wtf);
        return merge(source, wtf, true);
    }

    /** Replaces {@code target} with a copy of the game's WTF folder. */
    public static void exportTo(File wtf, File target) throws IOException {
        if (!wtf.isDirectory()) throw new IOException("The game has no WTF folder yet. Start the game once first");
        if (!FileOps.deleteRecursively(target)) throw new IOException("Cannot replace " + target);
        FileOps.copyRecursively(wtf, target);
    }

    private static int merge(File source, File target, boolean top) throws IOException {
        File[] children = source.listFiles();
        if (children == null) return 0;
        int copied = 0;
        for (File child : children) {
            if (top && child.isFile() && child.getName().equalsIgnoreCase(CONFIG_FILE)) continue;
            File existing = FileOps.findCaseInsensitive(target, child.getName());
            File destination = existing != null ? existing : new File(target, child.getName());
            if (child.isDirectory()) {
                if (destination.isFile() && !destination.delete()) throw new IOException("Cannot replace " + destination);
                if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Cannot create " + destination);
                copied += merge(child, destination, false);
            }
            else {
                if (destination.isDirectory() && !FileOps.deleteRecursively(destination)) {
                    throw new IOException("Cannot replace " + destination);
                }
                FileOps.copyRecursively(child, destination);
                copied++;
            }
        }
        return copied;
    }
}
