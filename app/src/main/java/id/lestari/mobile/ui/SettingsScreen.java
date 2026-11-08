package id.lestari.mobile.ui;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.ApiException;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One settings form: GET to load, PUT (nested from dotted keys) to save, plus toolbar actions and an optional table below. */
public final class SettingsScreen extends Screen implements Action.Ctx {
    private final Page def;
    private LinearLayout box;
    private FormView form;
    private Button save;
    private JSONObject data = new JSONObject();

    public SettingsScreen(MainActivity act, Page def) {
        super(act);
        this.def = def;
    }

    @Override
    public String title() {
        return def.title;
    }

    @Override
    protected View create() {
        LinearLayout outer = Ui.vbox(ctx);
        View head = Ui.header(ctx, def.description);
        if (head != null) outer.addView(head, Ui.match());
        box = Ui.vbox(ctx);
        box.setPadding(0, 0, 0, Ui.dp(ctx, 24));
        outer.addView(box, Ui.match());
        reload();
        return Ui.scroll(ctx, outer);
    }

    @Override
    public void reload() {
        if (box == null) return;
        box.removeAllViews();
        box.addView(Ui.spinner(ctx));
        load(() -> act.api().get(def.endpoint).optJSONObject("data"), d -> {
            data = d == null ? new JSONObject() : d;
            draw();
            refreshMenu();
        }, e -> {
            box.removeAllViews();
            box.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private void draw() {
        box.removeAllViews();
        // dotted keys ("notifications.app.billing") read from nested objects
        JSONObject values = Json.copy(data);
        List<Field> fields = new ArrayList<>();
        for (Field f : def.fields) {
            if (f.key != null && f.key.contains(".")) Json.put(values, f.key, Json.get(data, f.key));
            if (def.readOnly && !"heading".equals(f.type)) {
                Field copy = copyReadonly(f);
                fields.add(copy);
            } else {
                fields.add(f);
            }
        }
        LinearLayout card = Ui.card(ctx);
        form = new FormView(act, fields, values, false);
        card.addView(form.view(), Ui.match());
        if (!def.readOnly) {
            save = Ui.button(ctx, "Save changes", Ui.Style.PRIMARY, v -> save());
            card.addView(save, Ui.margins(Ui.match(), ctx, 0, 12, 0, 0));
        }
        box.addView(card, Ui.cardParams(ctx));
        if (def.below != null) drawBelow();
    }

    private static Field copyReadonly(Field f) {
        Field c = new Field(f.key, f.label, f.type);
        c.options = f.options;
        c.lookup = f.lookup;
        c.fields = f.fields;
        c.format = f.format;
        c.help = f.help;
        c.readonly = true;
        return c;
    }

    private void drawBelow() {
        final Page.Below b = def.below;
        final LinearLayout section = Ui.vbox(ctx);
        section.addView(Ui.text(ctx, b.title, 16, Theme.text, true), Ui.margins(Ui.match(), ctx, 16, 12, 16, 4));
        box.addView(section, Ui.match());
        if (b.dataKey != null) {
            fill(section, Json.objects(Json.get(data, b.dataKey)));
            return;
        }
        section.addView(Ui.spinner(ctx));
        load(() -> {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("per_page", 50);
            return Json.objects(act.api().get(b.endpoint, q).opt("data"));
        }, rows -> {
            section.removeViewAt(section.getChildCount() - 1);
            fill(section, rows);
        }, e -> {
            section.removeViewAt(section.getChildCount() - 1);
            section.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private void fill(LinearLayout section, List<JSONObject> rows) {
        if (rows.isEmpty()) section.addView(Ui.empty(ctx, def.below.emptyText));
        List<Field> cols = new ArrayList<>(def.below.columns);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject r = rows.get(i);
            if (def.below.numbered) {
                r = Json.copy(r);
                Json.put(r, "#", i + 1);
            }
            List<Field> c = cols;
            if (def.below.numbered) {
                c = new ArrayList<>();
                c.add(Field.col("#", "#"));
                c.addAll(cols);
            }
            section.addView(Ui.rowCard(ctx, c, r, def.below.numbered ? "#" : null), Ui.cardParams(ctx));
        }
    }

    private void save() {
        if (form == null || !form.validate()) return;
        save.setEnabled(false);
        final JSONObject body = Json.nest(form.read());
        load(() -> act.api().request(def.method, def.endpoint, null, body).optJSONObject("data"), saved -> {
            save.setEnabled(true);
            if (saved != null) data = saved;
            act.toast(def.savedText);
            if (def.afterSave != null) def.afterSave.after(act, data);
        }, e -> {
            save.setEnabled(true);
            if (e instanceof ApiException) form.setErrors(((ApiException) e).fields);
            act.error(e);
        });
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        if (!def.readOnly) m.add(new ToolItem("Save", true, this::save));
        for (final Action a : def.toolbar) {
            if (!a.appliesTo(data)) continue;
            m.add(new ToolItem(a.label, false, () -> a.run(act, data, this)));
        }
        m.add(new ToolItem("Refresh", false, this::reload));
        return m;
    }

    @Override
    public Map<String, Object> query() {
        return new LinkedHashMap<>();
    }
}
