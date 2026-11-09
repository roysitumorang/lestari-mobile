package id.lestari.mobile.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A screen definition (same kinds as the web client):
 * <ul>
 *   <li>CRUD – list/search/paginate + create/edit/delete (+ bulk delete, import, export)</li>
 *   <li>REPORT – date range + filters + summary cards + chart + rows + export</li>
 *   <li>SETTINGS – one form loaded with GET and saved with PUT</li>
 *   <li>CUSTOM – a hand-written screen in the Android layer, chosen by {@link #id}</li>
 * </ul>
 */
public final class Page {
    public enum Kind { CRUD, REPORT, SETTINGS, CUSTOM }

    public final Kind kind;
    public final String id;
    public final String title;
    public String singular;
    public String description;
    public String endpoint;
    /** Plugin that must be active to use the page ("business", "attendance"). */
    public String plugin;
    public List<Action> toolbar = new ArrayList<>();
    public List<Action> rowActions = new ArrayList<>();
    public List<Field> filters = new ArrayList<>();
    public List<Field> fields = new ArrayList<>();

    // ---- crud ----
    public List<String> ops = new ArrayList<>(Arrays.asList("list", "create", "read", "update", "delete"));
    public List<Opt> importModes;
    public String searchPlaceholder = I18n.t("Search");
    public List<Field> columns;
    public List<Field> detailFields;
    public String tabsKey;
    public List<Opt> tabs;
    public String tabsDefault = "";
    public String idKey = "id";
    public String titleKey = "name";
    public String defaultSort = "";
    public String defaultOrder = "asc";
    public boolean readOnly;
    public String createLabel;

    // ---- report ----
    public boolean dateRange = true;
    public boolean paginated = true;
    public boolean export = true;
    public String defaultPreset = "today";
    /** Search box placeholder; null = no search box. */
    public String search;
    public List<Field> summary = new ArrayList<>();
    public Chart chart;
    public Detail detail;
    public String groupByKey;
    public String groupByLabel = "Group by";
    public List<Opt> groupBy;

    // ---- settings ----
    public String method = "PUT";
    public String savedText = "Settings saved";
    public AfterSave afterSave;
    public Below below;

    public interface AfterSave {
        void after(Host host, JSONObject saved);
    }

    public Page(Kind kind, String id, String title) {
        this.kind = kind;
        this.id = id;
        this.title = I18n.t(title);
    }

    public boolean has(String op) {
        return ops.contains(op);
    }

    public String singularOrTitle() {
        return singular != null ? singular : title;
    }

    /** Table columns: explicit, or the first eight listable fields. */
    public List<Field> listColumns() {
        if (columns != null) return columns;
        List<Field> out = new ArrayList<>();
        for (Field f : fields) {
            if (!f.list || f.type == null) continue;
            if (Arrays.asList("textarea", "items", "password", "heading", "file").contains(f.type)) continue;
            out.add(f);
            if (out.size() == 8) break;
        }
        return out;
    }

    // ---- builder helpers ----

    public Page ops(String... o) { ops = new ArrayList<>(Arrays.asList(o)); return this; }
    public Page desc(String d) { description = I18n.t(d); return this; }
    public Page singular(String s) { singular = I18n.t(s); return this; }
    public Page endpoint(String e) { endpoint = e; return this; }
    public Page plugin(String p) { plugin = p; return this; }
    public Page search(String placeholder) { searchPlaceholder = I18n.t(placeholder); search = searchPlaceholder; return this; }
    public Page fields(Field... f) { fields = new ArrayList<>(Arrays.asList(f)); return this; }
    public Page fields(List<Field> f) { fields = f; return this; }
    public Page columns(Field... c) { columns = new ArrayList<>(Arrays.asList(c)); return this; }
    public Page columns(List<Field> c) { columns = c; return this; }
    public Page detailFields(Field... f) { detailFields = new ArrayList<>(Arrays.asList(f)); return this; }
    public Page detailFields(List<Field> f) { detailFields = f; return this; }
    public Page filters(Field... f) { filters = new ArrayList<>(Arrays.asList(f)); return this; }
    public Page filters(List<Field> f) { filters = f; return this; }
    public Page summary(Field... f) { summary = new ArrayList<>(Arrays.asList(f)); return this; }
    public Page toolbar(Action... a) { toolbar = new ArrayList<>(Arrays.asList(a)); return this; }
    public Page rowActions(Action... a) { rowActions = new ArrayList<>(Arrays.asList(a)); return this; }
    public Page rowActions(List<Action> a) { rowActions = a; return this; }
    public Page tabs(String key, String def, List<Opt> options) { tabsKey = key; tabsDefault = def; tabs = options; return this; }
    public Page idKey(String k) { idKey = k; return this; }
    public Page titleKey(String k) { titleKey = k; return this; }
    public Page sort(String key, String order) { defaultSort = key; defaultOrder = order; return this; }
    public Page readOnly() { readOnly = true; return this; }
    public Page importModes(List<Opt> m) { importModes = m; return this; }
    public Page preset(String p) { defaultPreset = p; return this; }
    public Page noDates() { dateRange = false; return this; }
    public Page notPaginated() { paginated = false; return this; }
    public Page noExport() { export = false; return this; }
    public Page chart(Chart c) { chart = c; return this; }
    public Page detail(Detail d) { detail = d; return this; }
    public Page groupBy(String key, String label, List<Opt> options) { groupByKey = key; groupByLabel = I18n.t(label); groupBy = options; return this; }
    public Page method(String m) { method = m; return this; }
    public Page afterSave(AfterSave a) { afterSave = a; return this; }
    public Page below(Below b) { below = b; return this; }

    /** Report chart: one line/bar per series, x labels from {@link #label}. */
    public static final class Chart {
        public final String title;
        public final String type;
        public final String label;
        /** "data" = response rows, "chart" = response.chart */
        public final String source;
        public final String format;
        public final List<String[]> series = new ArrayList<>();

        public Chart(String title, String type, String label, String source, String format) {
            this.title = I18n.t(title);
            this.type = type;
            this.label = label;
            this.source = source;
            this.format = format;
        }

        public Chart series(String key, String name) {
            series.add(new String[]{key, I18n.t(name)});
            return this;
        }
    }

    /** Report row details: GET endpoint (with {placeholders}) shown as fields or as a table from a list key. */
    public static final class Detail {
        public final String endpoint;
        public final String title;
        public List<Field> fields;
        /** When set, the response's {@code data.<tableKey>} array is shown as a table with {@link #tableColumns}. */
        public String tableKey;
        public List<Field> tableColumns;

        public Detail(String endpoint, String title) {
            this.endpoint = endpoint;
            this.title = I18n.t(title);
        }

        public Detail fields(Field... f) { fields = new ArrayList<>(Arrays.asList(f)); return this; }
        public Detail fields(List<Field> f) { fields = f; return this; }

        public Detail table(String key, List<Field> cols) {
            tableKey = key;
            tableColumns = cols;
            return this;
        }
    }

    /** An extra read-only table under a settings form: rows from the loaded data or from another endpoint. */
    public static final class Below {
        public final String title;
        public final List<Field> columns;
        public String dataKey;
        public String endpoint;
        public String emptyText = I18n.t("Nothing yet");
        public boolean numbered;

        public Below(String title, List<Field> columns) {
            this.title = I18n.t(title);
            this.columns = columns;
        }

        public static Below fromData(String title, String key, String empty, Field... cols) {
            Below b = new Below(title, Arrays.asList(cols));
            b.dataKey = key;
            b.emptyText = I18n.t(empty);
            return b;
        }

        public static Below fromEndpoint(String title, String endpoint, String empty, Field... cols) {
            Below b = new Below(title, Arrays.asList(cols));
            b.endpoint = endpoint;
            b.emptyText = I18n.t(empty);
            return b;
        }

        public Below numbered() { numbered = true; return this; }
    }
}
