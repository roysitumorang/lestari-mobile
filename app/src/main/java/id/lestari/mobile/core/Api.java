package id.lestari.mobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocking HTTP client for the Lestari backend (call it from a background thread).
 * <ul>
 *   <li>Sends {@code Authorization: Bearer <token>} and {@code X-Branch-Id} when a branch is selected.</li>
 *   <li>Expects the JSON envelope {@code { data, meta?, summary?, totals?, chart? }} or {@code { error }}.</li>
 *   <li>On 401 it refreshes the session once, then reports sign-out through {@link Listener}.</li>
 *   <li>Passwords are sent base64-encoded: see {@link #encodePassword(String)}.</li>
 * </ul>
 */
public final class Api {
    /** Persistent key/value storage for tokens and preferences (SharedPreferences on Android). */
    public interface Store {
        String get(String key);

        void set(String key, String value);
    }

    public interface Listener {
        void onSignedOut();
    }

    public static final String K_BASE_URL = "base_url";
    public static final String K_TOKEN = "token";
    public static final String K_REFRESH = "refresh_token";
    public static final String K_USER = "user";
    public static final String K_BRANCH = "branch_id";
    public static final String K_THEME = "display";
    /** "en" or "id"; sent as Accept-Language. */
    public static final String K_LANG = "lang";

    /** Android emulator → host machine. Change it on the sign-in screen. */
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/v1";

    private final Store store;
    private volatile Listener listener;
    private final Object refreshLock = new Object();

    public Api(Store store) {
        this.store = store;
    }

    public Store store() {
        return store;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public String baseUrl() {
        String b = store.get(K_BASE_URL);
        return b == null || b.isEmpty() ? DEFAULT_BASE_URL : b;
    }

    public boolean signedIn() {
        String t = store.get(K_TOKEN);
        return t != null && !t.isEmpty();
    }

    public void clearSession() {
        store.set(K_TOKEN, null);
        store.set(K_REFRESH, null);
        store.set(K_USER, null);
        store.set(K_BRANCH, null);
    }

    /** Base64 of the UTF-8 bytes, standard alphabet — the format the backend expects for every password. */
    public static String encodePassword(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- convenience

    public JSONObject get(String path) throws ApiException {
        return request("GET", path, null, null);
    }

    public JSONObject get(String path, Map<String, Object> query) throws ApiException {
        return request("GET", path, query, null);
    }

    public JSONObject post(String path, Object body) throws ApiException {
        return request("POST", path, null, body == null ? new JSONObject() : body);
    }

    public JSONObject put(String path, Object body) throws ApiException {
        return request("PUT", path, null, body);
    }

    public JSONObject patch(String path, Object body) throws ApiException {
        return request("PATCH", path, null, body);
    }

    public JSONObject delete(String path) throws ApiException {
        return request("DELETE", path, null, null);
    }

    /** {@code body} is a JSONObject/JSONArray, or null for no body. */
    public JSONObject request(String method, String path, Map<String, Object> query, Object body) throws ApiException {
        byte[] payload = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
        Response r = send(method, path, query, payload, payload == null ? null : "application/json", true);
        return r.json();
    }

    /** multipart/form-data upload (files and plain fields). */
    public JSONObject upload(String path, Multipart form) throws ApiException {
        Response r = send("POST", path, null, form.bytes(), form.contentType(), true);
        return r.json();
    }

    /** A file response (PDF, XLSX, PNG …). */
    public Download download(String path, Map<String, Object> query) throws ApiException {
        Response r = send("GET", path, query, null, null, true);
        return new Download(r.body, filename(r.disposition), r.contentType);
    }

    // ---------------------------------------------------------------- core

    public String buildUrl(String path, Map<String, Object> query) {
        StringBuilder sb = new StringBuilder(baseUrl().replaceAll("/+$", "")).append(path);
        if (query != null && !query.isEmpty()) {
            boolean first = !path.contains("?");
            for (Map.Entry<String, Object> e : query.entrySet()) {
                Object v = e.getValue();
                if (v == null || v == JSONObject.NULL) continue;
                List<Object> vals = new ArrayList<>();
                String key = e.getKey();
                if (v instanceof JSONArray) {
                    vals.addAll(Json.values(v));
                    key += "[]";
                } else if (v instanceof Collection) {
                    vals.addAll((Collection<?>) v);
                    key += "[]";
                } else {
                    String s = String.valueOf(v);
                    if (s.isEmpty()) continue;
                    vals.add(s);
                }
                for (Object x : vals) {
                    sb.append(first ? '?' : '&');
                    first = false;
                    sb.append(enc(key)).append('=').append(enc(String.valueOf(x)));
                }
            }
        }
        return sb.toString();
    }

    private Response send(String method, String path, Map<String, Object> query, byte[] body, String contentType, boolean mayRefresh)
            throws ApiException {
        Response r;
        try {
            r = exchange(method, buildUrl(path, query), body, contentType, store.get(K_TOKEN));
        } catch (IOException e) {
            throw new ApiException(0, "network_error", I18n.t("Cannot reach the server at {url}", "url", baseUrl()));
        }
        if (r.status == 401 && mayRefresh && !path.equals("/auth/login") && !path.equals("/auth/refresh")) {
            if (refresh()) return send(method, path, query, body, contentType, false);
            clearSession();
            Listener l = listener;
            if (l != null) l.onSignedOut();
            throw new ApiException(401, "unauthenticated", I18n.t("Session expired, please sign in again."));
        }
        if (r.status >= 400) throw ApiException.from(r.status, r.jsonOrNull());
        return r;
    }

    /** Rotates the refresh token once; concurrent callers wait for the same refresh. */
    private boolean refresh() {
        String before = store.get(K_TOKEN);
        synchronized (refreshLock) {
            String now = store.get(K_TOKEN);
            if (now != null && !now.equals(before)) return true; // another thread refreshed already
            String rt = store.get(K_REFRESH);
            if (rt == null || rt.isEmpty()) return false;
            try {
                byte[] b = Json.obj("refresh_token", rt).toString().getBytes(StandardCharsets.UTF_8);
                Response r = exchange("POST", buildUrl("/auth/refresh", null), b, "application/json", null);
                if (r.status != 200) return false;
                JSONObject d = r.json().optJSONObject("data");
                if (d == null) return false;
                store.set(K_TOKEN, d.optString("access_token"));
                store.set(K_REFRESH, d.optString("refresh_token"));
                return true;
            } catch (IOException | ApiException e) {
                return false;
            }
        }
    }

    private Response exchange(String method, String url, byte[] body, String contentType, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(120000);
            setMethod(c, method);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Accept-Language", I18n.lang());
            if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
            String branch = store.get(K_BRANCH);
            if (branch != null && !branch.isEmpty()) c.setRequestProperty("X-Branch-Id", branch);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
            }
            Response r = new Response();
            r.status = c.getResponseCode();
            r.contentType = c.getContentType();
            r.disposition = c.getHeaderField("Content-Disposition");
            InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
            r.body = in == null ? new byte[0] : readAll(in);
            return r;
        } finally {
            c.disconnect();
        }
    }

    /**
     * Android's HttpURLConnection accepts PATCH; some JVM implementations reject it in setRequestMethod.
     * Fall back to the public (protected) {@code method} field of java.net.HttpURLConnection in that case.
     */
    private static void setMethod(HttpURLConnection c, String method) throws IOException {
        try {
            c.setRequestMethod(method);
        } catch (java.net.ProtocolException e) {
            try {
                java.lang.reflect.Field f = HttpURLConnection.class.getDeclaredField("method");
                f.setAccessible(true);
                f.set(c, method);
            } catch (ReflectiveOperationException | RuntimeException re) {
                throw e;
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern FILENAME = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    static String filename(String disposition) {
        if (disposition == null) return null;
        Matcher m = FILENAME.matcher(disposition);
        if (!m.find()) return null;
        try {
            return URLDecoder.decode(m.group(1), "UTF-8");
        } catch (Exception e) {
            return m.group(1);
        }
    }

    private static final class Response {
        int status;
        byte[] body;
        String contentType;
        String disposition;

        JSONObject json() throws ApiException {
            if (status == 204 || body.length == 0) return Json.obj("data", null);
            JSONObject o = jsonOrNull();
            if (o == null) throw new ApiException(status, "bad_response", I18n.t("The server sent an unexpected response."));
            return o;
        }

        JSONObject jsonOrNull() {
            try {
                return new JSONObject(new String(body, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                return null;
            }
        }
    }

    /** A downloaded file. */
    public static final class Download {
        public final byte[] bytes;
        public final String filename;
        public final String mimeType;

        Download(byte[] bytes, String filename, String mimeType) {
            this.bytes = bytes;
            this.filename = filename;
            this.mimeType = mimeType == null ? "application/octet-stream" : mimeType.split(";")[0].trim();
        }
    }

    /** multipart/form-data builder. */
    public static final class Multipart {
        private final String boundary = "----lestari" + UUID.randomUUID().toString().replace("-", "");
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Map<String, Boolean> names = new LinkedHashMap<>();

        public Multipart field(String name, String value) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
            write(value);
            write("\r\n");
            names.put(name, true);
            return this;
        }

        public Multipart file(String name, String filename, String mime, byte[] data) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\""
                    + filename.replace("\"", "") + "\"\r\nContent-Type: " + (mime == null ? "application/octet-stream" : mime) + "\r\n\r\n");
            out.write(data, 0, data.length);
            write("\r\n");
            names.put(name, true);
            return this;
        }

        public String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        byte[] bytes() {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] b = out.toByteArray();
            all.write(b, 0, b.length);
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            all.write(end, 0, end.length);
            return all.toByteArray();
        }

        private void write(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length);
        }
    }
}
