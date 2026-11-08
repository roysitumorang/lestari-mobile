package id.lestari.mobile.ui;

import android.view.View;
import android.widget.LinearLayout;

import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Json;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** Read-only list loaded from an endpoint (stock movements, point history, activity logs). */
public final class TableScreen extends Screen {
    private final String title;
    private final String path;
    private final Map<String, Object> query;
    private final List<Field> columns;
    private final String emptyText;
    private LinearLayout list;

    public TableScreen(MainActivity act, String title, String path, Map<String, Object> query, List<Field> columns, String emptyText) {
        super(act);
        this.title = title;
        this.path = path;
        this.query = query;
        this.columns = columns;
        this.emptyText = emptyText;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    protected View create() {
        list = Ui.vbox(ctx);
        list.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
        reload();
        return Ui.scroll(ctx, list);
    }

    @Override
    public void reload() {
        list.removeAllViews();
        list.addView(Ui.spinner(ctx));
        load(() -> act.api().get(path, query), res -> {
            list.removeAllViews();
            List<JSONObject> rows = Json.objects(res.opt("data"));
            if (rows.isEmpty()) list.addView(Ui.empty(ctx, emptyText));
            for (JSONObject r : rows) list.addView(Ui.rowCard(ctx, columns, r, null), Ui.cardParams(ctx));
        }, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }
}
