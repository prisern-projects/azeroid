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
        return out.length() > 0 ? out.toString() : "Nothing found\n";
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
