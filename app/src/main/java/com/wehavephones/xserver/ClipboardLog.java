package com.wehavephones.xserver;

import com.wehavephones.core.SessionLogs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Download/WeHavePhones/clipboard.log: every selection request X clients make and each paste,
 * so a paste that does nothing shows which step never happened.
 */
public final class ClipboardLog {
    private static final File FILE = new File(SessionLogs.CONTAINER_LOG_DIR, "clipboard.log");
    private static final long MAX_BYTES = 256 * 1024;

    private ClipboardLog() {}

    public static synchronized void log(String message) {
        try {
            if (FILE.length() > MAX_BYTES) FILE.delete();
            try (FileWriter out = new FileWriter(FILE, true)) {
                out.write(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()));
                out.write(' ');
                out.write(message);
                out.write('\n');
            }
        }
        catch (IOException ignored) {}
    }

    public static String atom(int id) {
        String name = id > 0 ? Atom.getName(id) : null;
        return name != null ? name : String.valueOf(id);
    }
}
