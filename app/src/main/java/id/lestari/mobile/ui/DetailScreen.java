package id.lestari.mobile.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Host;
import id.lestari.mobile.core.Json;

import org.json.JSONObject;

import java.util.List;

/** Read-only key/value view of one record; "items" fields become nested row cards. Loads its data in the background. */
public final class DetailScreen extends Screen {
    private final String title;
    private final Host.Work<JSONObject> loader;
    private final List<Field> fields;
    private final String tableKey;
    private final List<Field> tableColumns;
    private LinearLayout box;

    public DetailScreen(MainActivity act, String title, Host.Work<JSONObject> loader, List<Field> fields, String tableKey, List<Field> tableColumns) {
        super(act);
        this.title = title;
        this.loader = loader;
        this.fields = fields;
        this.tableKey = tableKey;
        this.tableColumns = tableColumns;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    protected View create() {
        box = Ui.vbox(ctx);
        box.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 24));
        box.addView(Ui.spinner(ctx));
        load(loader, data -> {
            box.removeAllViews();
            if (tableKey != null) {
                List<JSONObject> rows = Json.objects(Json.get(data, tableKey));
                if (rows.isEmpty()) box.addView(Ui.empty(ctx, "No receipts"));
                for (JSONObject r : rows) box.addView(Ui.rowCard(ctx, tableColumns, r, null), Ui.cardParams(ctx));
            } else {
                box.addView(build(ctx, fields, data == null ? new JSONObject() : data), Ui.cardParams(ctx));
            }
        }, e -> {
            box.removeAllViews();
            box.addView(Ui.empty(ctx, e.getMessage()));
        });
        return Ui.scroll(ctx, box);
    }

    /** Builds the key/value card (also used for settings "below" sections). */
    static View build(Context c, List<Field> fields, JSONObject row) {
        LinearLayout card = Ui.card(c);
        for (Field f : fields) {
            if ("heading".equals(f.type)) {
                TextView h = Ui.text(c, f.label, 15, Theme.primary, true);
                h.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
                card.addView(h);
                continue;
            }
            if ("password".equals(f.type) || "file".equals(f.type) || f.key == null) continue;
            if ("items".equals(f.type)) {
                card.addView(Ui.text(c, f.label, 14, Theme.text, true), Ui.margins(Ui.match(), c, 0, 10, 0, 4));
                List<JSONObject> rows = Json.objects(Json.get(row, f.key));
                if (rows.isEmpty()) card.addView(Ui.muted(c, "No rows"));
                for (JSONObject r : rows) {
                    LinearLayout sub = Ui.vbox(c);
                    sub.setBackground(Ui.rounded(Theme.background, Theme.border, 8, c));
                    int p = Ui.dp(c, 10);
                    sub.setPadding(p, Ui.dp(c, 6), p, Ui.dp(c, 6));
                    for (Field s : f.fields) sub.addView(Ui.pair(c, s.label, Format.cell(s, r)), Ui.match());
                    card.addView(sub, Ui.margins(Ui.match(), c, 0, 3, 0, 3));
                }
                continue;
            }
            if ("image".equals(Format.effectiveFormat(f))) {
                String url = Json.str(row, f.key);
                card.addView(Ui.muted(c, f.label), Ui.margins(Ui.match(), c, 0, 6, 0, 2));
                if (url.isEmpty()) card.addView(Ui.body(c, "-"));
                else card.addView(Ui.thumb(c, url, 120));
                continue;
            }
            card.addView(Ui.pair(c, f.label, Format.cell(f, row)), Ui.match());
        }
        return card;
    }
}
