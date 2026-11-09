package id.lestari.mobile.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A declarative button on a page toolbar or a row (same shape as the web client's actions):
 * call {@code method path} (with {placeholders} filled from the row), optionally after a form or a confirmation,
 * or download a file, or run a fully custom {@link OnClick}.
 */
public final class Action {
    public final String id;
    public final String label;
    public String method = "POST";
    public String path;
    public List<Field> form;
    public boolean upload;
    public String confirm;
    public String typeToConfirm;
    /** Fallback file name when the action downloads a file (GET). */
    public String download;
    public String success;
    public String title;
    public String submitText;
    public boolean danger;
    public boolean reload = true;
    /** Send the page's current filters (e.g. report date range) along. */
    public boolean useFilters;
    /** Fixed JSON body (merged under form values). */
    public JSONObject body;
    public When when;
    public After after;
    public Values values;
    public OnClick onClick;

    public interface When {
        boolean test(JSONObject row);
    }

    /** Called with the response (on the UI thread). */
    public interface After {
        void after(Host host, JSONObject response, JSONObject row);
    }

    /** Initial form values; runs on a background thread (may call the API). */
    public interface Values {
        JSONObject values(Host host, JSONObject row) throws Exception;
    }

    public interface OnClick {
        void click(Host host, JSONObject row, Ctx ctx);
    }

    /** The page an action runs on. */
    public interface Ctx {
        void reload();

        /** The page's current query (filters, date range …). */
        Map<String, Object> query();
    }

    public Action(String id, String label) {
        this.id = id;
        this.label = I18n.t(label);
    }

    // ---- builders ----

    public static Action call(String id, String label, String method, String path) {
        Action a = new Action(id, label);
        a.method = method;
        a.path = path;
        return a;
    }

    public static Action download(String id, String label, String path, String filename) {
        Action a = call(id, label, "GET", path);
        a.download = filename;
        a.reload = false;
        return a;
    }

    public static Action custom(String id, String label, OnClick onClick) {
        Action a = new Action(id, label);
        a.onClick = onClick;
        return a;
    }

    public Action form(Field... fields) { form = new ArrayList<>(Arrays.asList(fields)); return this; }
    public Action upload() { upload = true; return this; }
    public Action confirm(String text) { confirm = I18n.t(text); return this; }
    public Action typeToConfirm(String word) { typeToConfirm = word; return this; }
    public Action success(String s) { success = I18n.t(s); return this; }
    public Action danger() { danger = true; return this; }
    public Action noReload() { reload = false; return this; }
    public Action useFilters() { useFilters = true; return this; }
    public Action body(JSONObject b) { body = b; return this; }
    public Action when(When w) { when = w; return this; }
    public Action after(After a) { after = a; return this; }
    public Action values(Values v) { values = v; return this; }
    public Action title(String t) { title = I18n.t(t); return this; }

    public boolean appliesTo(JSONObject row) {
        return when == null || when.test(row == null ? new JSONObject() : row);
    }

    // ---- running ----

    /** Runs the action for {@code row} (null for toolbar actions). */
    public void run(Host host, JSONObject row, Ctx ctx) {
        if (onClick != null) {
            onClick.click(host, row, ctx);
            return;
        }
        if (form != null) {
            Host.FormSpec spec = new Host.FormSpec();
            spec.title = title != null ? title : label;
            spec.submitText = submitText != null ? submitText : label;
            spec.fields = form;
            spec.onSubmit = v -> exec(host, row, ctx, v);
            spec.onDone = () -> done(host, ctx);
            if (values != null) {
                host.run(() -> values.values(host, row), v -> {
                    spec.values = v == null ? new JSONObject() : v;
                    host.form(spec);
                });
            } else {
                host.form(spec);
            }
            return;
        }
        Runnable go = () -> host.run(() -> {
            JSONObject b = body;
            if (useFilters && download == null && ctx != null) {
                b = new JSONObject();
                for (Map.Entry<String, Object> e : ctx.query().entrySet()) Json.put(b, e.getKey(), e.getValue());
                Json.merge(b, body);
            }
            exec(host, row, ctx, b);
            return Boolean.TRUE;
        }, ok -> done(host, ctx));
        if (confirm != null) host.confirm(label, confirm, label, danger, typeToConfirm, go);
        else go.run();
    }

    private JSONObject lastResponse;

    /** Performs the HTTP part (background thread). */
    private void exec(Host host, JSONObject row, Ctx ctx, JSONObject formValues) throws Exception {
        String p = fillPath(path, row);
        Api api = host.api();
        if (download != null) {
            Map<String, Object> q = new LinkedHashMap<>();
            if (useFilters && ctx != null) q.putAll(ctx.query());
            if (formValues != null) {
                for (Iterator<String> it = formValues.keys(); it.hasNext(); ) {
                    String k = it.next();
                    q.put(k, formValues.opt(k));
                }
            }
            Api.Download d = api.download(p, q);
            host.saveFile(d, download);
            lastResponse = null;
            return;
        }
        JSONObject body = Json.copy(this.body);
        if (formValues != null) Json.merge(body, formValues);
        if (upload) {
            Api.Multipart mp = new Api.Multipart();
            for (Iterator<String> it = body.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = body.opt(k);
                if (v == null || v == JSONObject.NULL) continue;
                if (v instanceof FileRef) {
                    FileRef f = (FileRef) v;
                    mp.file(k, f.name, f.mimeType, f.bytes);
                } else if (v instanceof JSONObject || v instanceof JSONArray) {
                    mp.field(k, v.toString());
                } else {
                    mp.field(k, String.valueOf(v));
                }
            }
            lastResponse = api.upload(p, mp);
        } else {
            lastResponse = api.request(method, p, null, body);
        }
        final JSONObject res = lastResponse;
        if (after != null) host.run(() -> res, r -> after.after(host, r, row));
    }

    private void done(Host host, Ctx ctx) {
        if (download == null) host.toast(success != null ? success : I18n.t("Done"));
        if (reload && ctx != null) ctx.reload();
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    /** "/staff/{id}/permissions" + row → "/staff/0190…/permissions" (values URL-encoded). */
    public static String fillPath(String path, JSONObject row) {
        Matcher m = PLACEHOLDER.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = Json.str(row, m.group(1));
            try {
                v = URLEncoder.encode(v, "UTF-8").replace("+", "%20");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Open the payment page returned by billing/top-up actions, if any. */
    public static final After OPEN_PAYMENT = (host, res, row) -> {
        JSONObject d = res == null ? null : res.optJSONObject("data");
        String u = d == null ? "" : d.optString("payment_url", "");
        if (!u.isEmpty() && !"null".equals(u)) host.openUrl(u);
    };
}
