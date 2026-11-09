package id.lestari.mobile.core;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Dual language: English (the source text, also the dictionary key) and Bahasa Indonesia.
 *
 * <pre>
 *   I18n.t("Save")                                → "Simpan" when the language is "id"
 *   I18n.t("Page {page} of {pages}", "page", 2, "pages", 5)
 * </pre>
 *
 * The dictionary is {@code assets/i18n/id.json} (shared with lestari-client js/i18n/id.js); a missing entry falls
 * back to English. Page definitions translate their labels when they are built, so changing the language rebuilds
 * them (see {@code Defs.reset()}). Every API request sends {@code Accept-Language: <lang>}.
 */
public final class I18n {
    public static final String EN = "en";
    public static final String ID = "id";

    private static volatile String lang = EN;
    private static volatile Map<String, String> dict = Collections.emptyMap();

    private I18n() {}

    /** Switch the active language; {@code dictionary} is the Indonesian table (ignored for English). */
    public static void set(String language, Map<String, String> dictionary) {
        lang = ID.equals(language) ? ID : EN;
        if (dictionary != null) dict = dictionary;
    }

    public static String lang() {
        return lang;
    }

    public static boolean supported(String language) {
        return EN.equals(language) || ID.equals(language);
    }

    /** Translate a source string. */
    public static String t(String s) {
        if (s == null || EN.equals(lang)) return s;
        String out = dict.get(s);
        return out != null ? out : s;
    }

    /** Translate, then fill {name} placeholders from name/value pairs. */
    public static String t(String s, Object... pairs) {
        String out = t(s);
        if (out == null) return null;
        for (int i = 0; i + 1 < pairs.length; i += 2) out = out.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        return out;
    }

    /** Parse a {"English": "Indonesia", …} JSON object. */
    public static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(json);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                out.put(k, o.optString(k, k));
            }
        } catch (Exception ignored) {
            // a broken dictionary leaves the app in English
        }
        return out;
    }
}
