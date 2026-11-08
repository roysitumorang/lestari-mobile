package id.lestari.mobile.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Select options loaded from a list endpoint (value and label are paths in each row). */
public final class Lookup {
    public final String endpoint;
    public final String value;
    public final String label;

    public Lookup(String endpoint, String value, String label) {
        this.endpoint = endpoint;
        this.value = value;
        this.label = label;
    }

    public Lookup(String endpoint) {
        this(endpoint, "id", "name");
    }

    private static final Map<String, List<Opt>> CACHE = new ConcurrentHashMap<>();

    /** Loads (and caches) the options. Blocking: call from a background thread. */
    public List<Opt> load(Api api) throws ApiException {
        List<Opt> cached = CACHE.get(endpoint);
        if (cached != null) return cached;
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("per_page", 1000);
        JSONObject res = api.get(endpoint, q);
        List<Opt> out = new ArrayList<>();
        for (JSONObject row : Json.objects(res.opt("data"))) {
            out.add(new Opt(Json.get(row, value), Json.str(row, label)));
        }
        CACHE.put(endpoint, out);
        return out;
    }

    /** Forget cached options (after saving a record or switching branch). */
    public static void clearCache() {
        CACHE.clear();
    }
}
