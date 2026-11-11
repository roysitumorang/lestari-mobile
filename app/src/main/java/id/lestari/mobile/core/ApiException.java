package id.lestari.mobile.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** An API error: {"error":{"code","message","fields"}} or a network failure (status 0). */
public final class ApiException extends Exception {
    private static final long serialVersionUID = 1L;

    public final int status;
    public final String code;
    public final transient Map<String, String> fields = new LinkedHashMap<>();

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static ApiException from(int status, JSONObject payload) {
        // envelope: the message is top-level, the code and field messages are in data
        JSONObject err = payload == null ? null : payload.optJSONObject("data");
        String code = err != null ? Json.str(err, "code", "http_" + status) : "http_" + status;
        String msg = payload != null ? Json.str(payload, "message", "") : "";
        if (msg.isEmpty()) msg = I18n.t("Request failed ({status})", "status", status);
        ApiException e = new ApiException(status, code, msg);
        JSONObject f = err == null ? null : err.optJSONObject("fields");
        if (f != null) {
            for (Iterator<String> it = f.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = f.optJSONArray(k);
                StringBuilder sb = new StringBuilder();
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(a.optString(i));
                    }
                } else {
                    sb.append(f.optString(k));
                }
                e.fields.put(k, sb.toString());
            }
        }
        return e;
    }
}
