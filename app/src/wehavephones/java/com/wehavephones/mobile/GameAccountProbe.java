package com.wehavephones.mobile;

import com.wehavephones.container.Container;
import com.wehavephones.core.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import java.util.Locale;

/**
 * Diagnostics: where Battle.net and WoW keep the chosen WoW game account (WoW1, WoW2, ...).
 * Blizzard does not document it, so this prints every setting that could hold it: the WoW
 * entries of Battle.net.config, any key there mentioning an account, and the account and
 * portal lines of each installed flavor's Config.wtf. Email addresses are masked.
 */
final class GameAccountProbe {
    private GameAccountProbe() {}

    static String describe(Container container) {
        if (container == null) return "No container\n";
        StringBuilder out = new StringBuilder();
        File config = new File(WeHavePhonesPaths.bnetRoamingDir(container), WeHavePhonesBnetConfig.CONFIG_NAME);
        String text = config.isFile() ? FileUtils.readString(config) : null;
        if (text == null) out.append("Battle.net.config: missing\n");
        else {
            try {
                JSONObject root = new JSONObject(text);
                JSONObject games = root.optJSONObject("Games");
                if (games != null) {
                    for (Iterator<String> it = games.keys(); it.hasNext(); ) {
                        String key = it.next();
                        if (key.toLowerCase(Locale.ROOT).contains("wow")) {
                            out.append("Games.").append(key).append(" = ").append(mask(String.valueOf(games.opt(key)))).append('\n');
                        }
                    }
                }
                walk(root, "", out);
            }
            catch (Exception e) {
                out.append("Battle.net.config: unreadable (").append(e.getClass().getSimpleName()).append(")\n");
            }
        }

        File root = WeHavePhonesPaths.wowRootFor(container);
        File[] flavors = root.listFiles();
        if (flavors != null) {
            for (File flavor : flavors) {
                File wtf = new File(flavor, "WTF/Config.wtf");
                if (!wtf.isFile()) continue;
                String body = FileUtils.readString(wtf);
                if (body == null) continue;
                for (String line : body.split("\n")) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains("account") || lower.contains("portal") || lower.contains("realm")) {
                        out.append(flavor.getName()).append("/Config.wtf: ").append(mask(line.trim())).append('\n');
                    }
                }
            }
        }
        appendRecentFiles(container, out);
        appendLogLines(container, out);
        return out.length() > 0 ? out.toString() : "Nothing found\n";
    }

    private static final long RECENT_MS = 15 * 60 * 1000L;

    /** Battle.net files changed in the last 15 minutes: switching accounts and copying right after shows where the choice lands. */
    private static void appendRecentFiles(Container container, StringBuilder out) {
        File roaming = WeHavePhonesPaths.bnetRoamingDir(container);
        File appData = roaming.getParentFile() != null ? roaming.getParentFile().getParentFile() : null;
        File[] roots = {
                roaming,
                appData != null ? new File(appData, "Local/Battle.net") : null,
                new File(WeHavePhonesPaths.driveC(container), "ProgramData/Battle.net")};
        long now = System.currentTimeMillis();
        java.util.ArrayList<File> recent = new java.util.ArrayList<>();
        for (File root : roots) collectRecent(root, now, recent, 0);
        recent.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        out.append("Changed in the last 15 min:").append(recent.isEmpty() ? " none" : "").append('\n');
        String base = WeHavePhonesPaths.driveC(container).getPath();
        for (int i = 0; i < Math.min(recent.size(), 40); i++) {
            File file = recent.get(i);
            out.append("  ").append(file.getPath().replace(base, "C:"))
                    .append(" (").append(file.length()).append(" B, ")
                    .append((now - file.lastModified()) / 1000).append(" s ago)\n");
        }
    }

    private static void collectRecent(File dir, long now, java.util.List<File> out, int depth) {
        if (dir == null || depth > 8) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectRecent(child, now, out, depth + 1);
            else if (now - child.lastModified() < RECENT_MS && !child.getName().endsWith(".log")) out.add(child);
        }
    }

    /** Lines of the newest Battle.net log that mention an account. */
    private static void appendLogLines(Container container, StringBuilder out) {
        File log = BattleNetLauncherLog.findMainLog(WeHavePhonesPaths.bnetLogsDir(container), "");
        if (log == null) return;
        String body = FileUtils.readString(log);
        if (body == null) return;
        java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>();
        for (String line : body.split("\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("account") || lower.contains("wow1") || lower.contains("wow2")) {
                lines.addLast(mask(line.trim()));
                if (lines.size() > 20) lines.removeFirst();
            }
        }
        out.append("Battle.net log lines about accounts (").append(log.getName()).append("):")
                .append(lines.isEmpty() ? " none" : "").append('\n');
        for (String line : lines) out.append("  ").append(line.length() > 300 ? line.substring(0, 300) + "…" : line).append('\n');
    }

    /** Keys anywhere in the config whose name mentions an account, outside the Games block already printed. */
    private static void walk(Object node, String path, StringBuilder out) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String key = it.next();
                String child = path.isEmpty() ? key : path + "." + key;
                Object value = object.opt(key);
                if (key.toLowerCase(Locale.ROOT).contains("account") && !(value instanceof JSONObject)) {
                    out.append(child).append(" = ").append(mask(String.valueOf(value))).append('\n');
                }
                if (!child.startsWith("Games.") || !child.toLowerCase(Locale.ROOT).contains("wow")) walk(value, child, out);
            }
        }
        else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) walk(array.opt(i), path + "[" + i + "]", out);
        }
    }

    private static String mask(String value) {
        return value.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "<email>");
    }
}
