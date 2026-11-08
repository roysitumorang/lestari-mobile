package id.lestari.mobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small helpers around org.json (bundled with Android). */
public final class Json {
    private Json() {}

    /** Reads a dotted path such as "customer.name"; returns null when any part is missing or JSON null. */
    public static Object get(JSONObject o, String path) {
        if (o == null || path == null) return null;
        Object cur = o;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof JSONObject)) return null;
            JSONObject j = (JSONObject) cur;
            if (!j.has(part) || j.isNull(part)) return null;
            cur = j.opt(part);
        }
        return cur;
    }

    public static String str(JSONObject o, String path) {
        Object v = get(o, path);
        return v == null ? "" : String.valueOf(v);
    }

    public static double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        return 0;
    }

    public static boolean truthy(Object v) {
        if (v == null || v == JSONObject.NULL) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) return !((String) v).isEmpty();
        if (v instanceof JSONArray) return ((JSONArray) v).length() > 0;
        return true;
    }

    public static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        for (int i = 0; i + 1 < kv.length; i += 2) put(o, String.valueOf(kv[i]), kv[i + 1]);
        return o;
    }

    public static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v == null ? JSONObject.NULL : v);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static JSONObject copy(JSONObject o) {
        JSONObject c = new JSONObject();
        if (o == null) return c;
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            put(c, k, o.opt(k));
        }
        return c;
    }

    /** Copies every key of {@code src} into {@code dst} (shallow). */
    public static JSONObject merge(JSONObject dst, JSONObject src) {
        if (src == null) return dst;
        for (Iterator<String> it = src.keys(); it.hasNext(); ) {
            String k = it.next();
            put(dst, k, src.opt(k));
        }
        return dst;
    }

    public static List<JSONObject> objects(Object arr) {
        List<JSONObject> out = new ArrayList<>();
        if (!(arr instanceof JSONArray)) return out;
        JSONArray a = (JSONArray) arr;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    public static List<Object> values(Object arr) {
        List<Object> out = new ArrayList<>();
        if (!(arr instanceof JSONArray)) return out;
        JSONArray a = (JSONArray) arr;
        for (int i = 0; i < a.length(); i++) out.add(a.opt(i));
        return out;
    }

    /** { "a.b": 1, c: 2 } → { a: { b: 1 }, c: 2 } — settings forms use dotted keys for nested objects. */
    public static JSONObject nest(JSONObject flat) {
        JSONObject out = new JSONObject();
        for (Iterator<String> it = flat.keys(); it.hasNext(); ) {
            String k = it.next();
            String[] parts = k.split("\\.");
            JSONObject o = out;
            for (int i = 0; i < parts.length - 1; i++) {
                JSONObject next = o.optJSONObject(parts[i]);
                if (next == null) {
                    next = new JSONObject();
                    put(o, parts[i], next);
                }
                o = next;
            }
            put(o, parts[parts.length - 1], flat.opt(k));
        }
        return out;
    }
}
