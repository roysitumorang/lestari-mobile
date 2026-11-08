package id.lestari.mobile.ui;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.FileRef;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Host;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Generic list/create/edit/delete screen driven by a CRUD page definition. */
public final class CrudScreen extends Screen implements Action.Ctx {
    private final Page def;
    private int page = 1;
    private int perPage = 25;
    private String q = "";
    private String sort;
    private String order;
    private String tab;
    private JSONObject filters = new JSONObject();
    private final List<JSONObject> rows = new ArrayList<>();
    private final TreeSet<Integer> selected = new TreeSet<>();
    private boolean selecting;
    private LinearLayout list;
    private LinearLayout pager;
    private LinearLayout tabBar;
    private View filterBox;
    private final Handler debounce = new Handler(Looper.getMainLooper());

    public CrudScreen(MainActivity act, Page def) {
        super(act);
        this.def = def;
        sort = def.defaultSort;
        order = def.defaultOrder;
        tab = def.tabsDefault;
    }

    @Override
    public String title() {
        return def.title;
    }

    @Override
    protected View create() {
        LinearLayout box = Ui.vbox(ctx);
        box.setPadding(0, 0, 0, Ui.dp(ctx, 24));
        View head = Ui.header(ctx, def.description);
        if (head != null) box.addView(head, Ui.match());

        if (def.tabs != null) {
            tabBar = Ui.hbox(ctx);
            tabBar.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), 0);
            box.addView(Ui.hscroll(ctx, tabBar), Ui.match());
            drawTabs();
        }

        LinearLayout tools = Ui.hbox(ctx);
        tools.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 4));
        EditText search = new EditText(ctx);
        search.setHint(def.searchPlaceholder);
        search.setSingleLine(true);
        search.setTextColor(Theme.text);
        search.setHintTextColor(Theme.muted);
        search.setBackground(Ui.rounded(Theme.surface, Theme.border, 8, ctx));
        int p = Ui.dp(ctx, 10);
        search.setPadding(p, p, p, p);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                debounce.removeCallbacksAndMessages(null);
                debounce.postDelayed(() -> {
                    q = e.toString().trim();
                    page = 1;
                    reload();
                }, 350);
            }
        });
        tools.addView(search, Ui.weight(1));
        if (!def.filters.isEmpty()) {
            Button toggle = Ui.button(ctx, "Filters", Ui.Style.OUTLINE, v ->
                    filterBox.setVisibility(filterBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            tools.addView(toggle, Ui.margins(Ui.wrap(), ctx, 8, 0, 0, 0));
        }
        box.addView(tools, Ui.match());

        if (!def.filters.isEmpty()) {
            final FormView fv = new FormView(act, def.filters, new JSONObject(), true);
            fv.setOnChange(() -> {
                filters = fv.read();
                page = 1;
                reload();
            });
            LinearLayout card = Ui.card(ctx);
            card.addView(fv.view(), Ui.match());
            filterBox = card;
            filterBox.setVisibility(View.GONE);
            box.addView(filterBox, Ui.cardParams(ctx));
        }

        list = Ui.vbox(ctx);
        box.addView(list, Ui.match());
        pager = Ui.hbox(ctx);
        pager.setGravity(Gravity.CENTER);
        pager.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), 0);
        box.addView(pager, Ui.match());
        reload();
        return Ui.scroll(ctx, box);
    }

    private void drawTabs() {
        tabBar.removeAllViews();
        for (final Opt o : def.tabs) {
            boolean on = String.valueOf(o.value).equals(tab);
            Button b = Ui.button(ctx, o.label, on ? Ui.Style.PRIMARY : Ui.Style.OUTLINE, v -> {
                tab = String.valueOf(o.value);
                page = 1;
                drawTabs();
                reload();
            });
            tabBar.addView(b, Ui.margins(Ui.wrap(), ctx, 0, 0, 8, 0));
        }
    }

    // ------------------------------------------------------------------ data

    @Override
    public Map<String, Object> query() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", page);
        m.put("per_page", perPage);
        for (Iterator<String> it = filters.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = filters.opt(k);
            if (v != null && v != JSONObject.NULL) m.put(k, v);
        }
        if (!q.isEmpty()) m.put("q", q);
        if (sort != null && !sort.isEmpty()) {
            m.put("sort", sort);
            m.put("order", order);
        }
        if (def.tabsKey != null && !tab.isEmpty()) m.put(def.tabsKey, tab);
        return m;
    }

    @Override
    public void reload() {
        if (list == null) return;
        list.removeAllViews();
        list.addView(Ui.spinner(ctx));
        pager.removeAllViews();
        final Map<String, Object> query = query();
        load(() -> act.api().get(def.endpoint, query), res -> {
            rows.clear();
            rows.addAll(Json.objects(res.opt("data")));
            selected.clear();
            selecting = false;
            drawRows();
            JSONObject meta = res.optJSONObject("meta");
            drawPager(meta);
            refreshMenu();
        }, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private void drawRows() {
        list.removeAllViews();
        if (rows.isEmpty()) {
            list.addView(Ui.empty(ctx, "No " + def.title.toLowerCase() + " yet"));
            return;
        }
        List<Field> cols = def.listColumns();
        for (int i = 0; i < rows.size(); i++) {
            final int idx = i;
            LinearLayout card = Ui.rowCard(ctx, cols, rows.get(i), def.titleKey);
            if (selected.contains(i)) card.setBackground(Ui.rounded((Theme.primary & 0x00FFFFFF) | 0x22000000, Theme.primary, 10, ctx));
            card.setOnClickListener(v -> {
                if (selecting) toggle(idx);
                else rowMenu(idx);
            });
            if (def.has("bulk_delete") && !def.readOnly) {
                card.setOnLongClickListener(v -> {
                    selecting = true;
                    toggle(idx);
                    return true;
                });
            }
            list.addView(card, Ui.cardParams(ctx));
        }
    }

    private void drawPager(JSONObject m) {
        pager.removeAllViews();
        if (m == null) return;
        final int pg = m.optInt("page", 1);
        final int pages = Math.max(1, m.optInt("total_pages", 1));
        int total = m.optInt("total", rows.size());
        Button prev = Ui.button(ctx, "‹ Prev", Ui.Style.TEXT, v -> {
            page = pg - 1;
            reload();
        });
        prev.setEnabled(pg > 1);
        Button next = Ui.button(ctx, "Next ›", Ui.Style.TEXT, v -> {
            page = pg + 1;
            reload();
        });
        next.setEnabled(pg < pages);
        TextView info = Ui.muted(ctx, "Page " + pg + " of " + pages + " · " + Format.number(total));
        info.setGravity(Gravity.CENTER);
        pager.addView(prev, Ui.wrap());
        pager.addView(info, Ui.weight(1));
        pager.addView(next, Ui.wrap());
    }

    private void toggle(int i) {
        if (!selected.remove(i)) selected.add(i);
        if (selected.isEmpty()) selecting = false;
        drawRows();
        refreshMenu();
        act.setTitleText(selecting ? selected.size() + " selected" : def.title);
    }

    @Override
    public boolean onBack() {
        if (!selecting) return false;
        selecting = false;
        selected.clear();
        drawRows();
        refreshMenu();
        act.setTitleText(def.title);
        return true;
    }

    // ------------------------------------------------------------------ toolbar

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        if (selecting) {
            m.add(new ToolItem("Delete (" + selected.size() + ")", true, this::bulkDelete));
            return m;
        }
        if (def.has("create")) m.add(new ToolItem(def.createLabel != null ? def.createLabel : "Add", true, () -> openForm(null)));
        for (final Action a : def.toolbar) m.add(new ToolItem(a.label, false, () -> a.run(act, null, this)));
        if (def.has("import")) m.add(new ToolItem("Import", false, this::importDialog));
        if (def.has("export")) m.add(new ToolItem("Export", false, this::export));
        m.add(new ToolItem("Sort…", false, this::chooseSort));
        m.add(new ToolItem("Rows per page…", false, () -> Dialogs.pick(ctx, "Rows per page", Opt.pairs(10, "10", 25, "25", 50, "50", 100, "100"), false, o -> {
            perPage = ((Number) o.value).intValue();
            page = 1;
            reload();
        })));
        if (def.has("bulk_delete") && !def.readOnly) m.add(new ToolItem("Select…", false, () -> {
            selecting = true;
            act.toast("Tap rows to select them");
            refreshMenu();
        }));
        m.add(new ToolItem("Refresh", false, this::reload));
        return m;
    }

    private void chooseSort() {
        List<Opt> opts = new ArrayList<>();
        for (Field c : def.listColumns()) {
            if (!c.sortable || c.key == null || c.label == null || c.label.isEmpty()) continue;
            opts.add(new Opt(c.key + "|asc", c.label + " ↑"));
            opts.add(new Opt(c.key + "|desc", c.label + " ↓"));
        }
        Dialogs.pick(ctx, "Sort by", opts, true, o -> {
            if (o.value == null) {
                sort = def.defaultSort;
                order = def.defaultOrder;
            } else {
                String[] p = String.valueOf(o.value).split("\\|");
                sort = p[0];
                order = p[1];
            }
            page = 1;
            reload();
        });
    }

    // ------------------------------------------------------------------ rows

    private String id(JSONObject row) {
        return Json.str(row, def.idKey);
    }

    private String path(JSONObject row) {
        return def.endpoint + "/" + Action.fillPath("{" + def.idKey + "}", row);
    }

    private void rowMenu(final int i) {
        final JSONObject row = rows.get(i);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> runs = new ArrayList<>();
        if (def.has("read")) {
            labels.add("View details");
            runs.add(() -> view(row));
        }
        if (def.has("update") && !def.readOnly) {
            labels.add("Edit");
            runs.add(() -> edit(row));
        }
        for (final Action a : def.rowActions) {
            if (!a.appliesTo(row)) continue;
            labels.add(a.label);
            runs.add(() -> a.run(act, row, this));
        }
        if (def.has("delete") && !def.readOnly) {
            labels.add("Delete");
            runs.add(() -> delete(row));
        }
        if (labels.isEmpty()) {
            act.toast("No actions for this row");
            return;
        }
        if (labels.size() == 1 && def.has("read")) {
            runs.get(0).run();
            return;
        }
        String title = Json.str(row, def.titleKey);
        Dialogs.sheet(ctx, title.isEmpty() ? def.singularOrTitle() : title, labels, w -> runs.get(w).run());
    }

    private void view(final JSONObject row) {
        List<Field> fields = def.detailFields != null ? def.detailFields : def.fields;
        act.push(new DetailScreen(act, def.singularOrTitle(), () -> {
            if (!def.has("read")) return row;
            return act.api().get(path(row)).optJSONObject("data");
        }, fields, null, null));
    }

    private void edit(final JSONObject row) {
        if (!def.has("read")) {
            openForm(row);
            return;
        }
        act.run(() -> act.api().get(path(row)).optJSONObject("data"), this::openForm);
    }

    private void openForm(final JSONObject row) {
        Host.FormSpec spec = new Host.FormSpec();
        spec.title = (row != null ? "Edit " : "Add ") + def.singularOrTitle();
        spec.fields = def.fields;
        spec.values = row != null ? row : new JSONObject();
        spec.onSubmit = values -> {
            if (row != null) act.api().put(path(row), values);
            else act.api().post(def.endpoint, values);
        };
        spec.onDone = () -> {
            act.toast(def.singularOrTitle() + (row != null ? " updated" : " created"));
            Lookup.clearCache();
            reload();
        };
        act.form(spec);
    }

    private void delete(final JSONObject row) {
        String name = Json.str(row, def.titleKey);
        if (name.isEmpty()) name = "#" + id(row);
        Dialogs.confirm(ctx, "Delete", "Delete \"" + name + "\"? This cannot be undone.", "Delete", true, null, () ->
                act.run(() -> act.api().delete(path(row)), r -> {
                    act.toast("Deleted");
                    Lookup.clearCache();
                    reload();
                }));
    }

    private void bulkDelete() {
        final JSONArray ids = new JSONArray();
        for (int i : selected) ids.put(rows.get(i).opt(def.idKey));
        Dialogs.confirm(ctx, "Delete", "Delete " + ids.length() + " selected item(s)? This cannot be undone.", "Delete", true, null, () ->
                act.run(() -> act.api().post(def.endpoint + "/bulk-delete", Json.obj("ids", ids)), r -> {
                    act.toast(ids.length() + " item(s) deleted");
                    act.setTitleText(def.title);
                    Lookup.clearCache();
                    reload();
                }));
    }

    // ------------------------------------------------------------------ import / export

    private void export() {
        final Map<String, Object> qy = query();
        qy.remove("page");
        qy.remove("per_page");
        qy.put("format", "xlsx");
        act.toast("Preparing export…");
        act.run(() -> {
            act.saveFile(act.api().download(def.endpoint + "/export", qy), def.id + ".xlsx");
            return Boolean.TRUE;
        }, ok -> {});
    }

    private void importDialog() {
        List<String> items = new ArrayList<>();
        items.add("Download template");
        items.add("Upload a filled-in file…");
        Dialogs.sheet(ctx, "Import " + def.title, items, w -> {
            if (w == 0) {
                act.run(() -> {
                    act.saveFile(act.api().download(def.endpoint + "/import-template", new LinkedHashMap<>()), def.id + "-template.xlsx");
                    return Boolean.TRUE;
                }, ok -> {});
                return;
            }
            final JSONObject[] result = {null};
            Host.FormSpec spec = new Host.FormSpec();
            spec.title = "Import " + def.title;
            spec.submitText = "Upload";
            spec.intro = "Upload an .xlsx or .csv file that follows the template.";
            List<Field> fs = new ArrayList<>();
            if (def.importModes != null) fs.add(Field.select("mode", "Mode", def.importModes).def(def.importModes.get(0).value).req().w(12));
            fs.add(Field.file("file", "File").accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv,text/comma-separated-values").req().w(12));
            spec.fields = fs;
            spec.onSubmit = values -> {
                Api.Multipart mp = new Api.Multipart();
                FileRef f = (FileRef) values.opt("file");
                mp.file("file", f.name, f.mimeType, f.bytes);
                if (values.has("mode") && !values.isNull("mode")) mp.field("mode", String.valueOf(values.opt("mode")));
                result[0] = act.api().upload(def.endpoint + "/import", mp).optJSONObject("data");
            };
            spec.onDone = () -> {
                JSONObject r = result[0] == null ? new JSONObject() : result[0];
                StringBuilder sb = new StringBuilder("Created " + r.optInt("created") + ", updated " + r.optInt("updated") + ", failed " + r.optInt("failed") + ".");
                List<JSONObject> errs = Json.objects(r.opt("errors"));
                for (int k = 0; k < Math.min(20, errs.size()); k++) sb.append("\nRow ").append(errs.get(k).optInt("row")).append(": ").append(Json.str(errs.get(k), "message"));
                act.message("Import finished", sb.toString());
                Lookup.clearCache();
                reload();
            };
            act.form(spec);
        });
    }
}
