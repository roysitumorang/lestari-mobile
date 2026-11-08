package id.lestari.mobile.ui;

import android.app.DatePickerDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Today-at-a-glance: GET /dashboard?date&include_unpaid_revenue&include_unpaid_profit. */
public final class DashboardScreen extends Screen {
    private final Page def;
    private LocalDate date = LocalDate.now();
    private Button dateButton;
    private Switch unpaidRevenue;
    private Switch unpaidProfit;
    private LinearLayout body;

    public DashboardScreen(MainActivity act, Page def) {
        super(act);
        this.def = def;
    }

    @Override
    public String title() {
        return def.title;
    }

    @Override
    protected View create() {
        LinearLayout box = Ui.vbox(ctx);
        box.setPadding(0, 0, 0, Ui.dp(ctx, 24));
        LinearLayout controls = Ui.card(ctx);
        dateButton = Ui.button(ctx, "", Ui.Style.OUTLINE, v -> new DatePickerDialog(ctx, (dp, y, m, d) -> {
            date = LocalDate.of(y, m + 1, d);
            paintDate();
            reload();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show());
        controls.addView(dateButton, Ui.match());
        unpaidRevenue = toggle("Include unpaid revenue");
        unpaidProfit = toggle("Include unpaid profit");
        controls.addView(unpaidRevenue, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        controls.addView(unpaidProfit, Ui.margins(Ui.match(), ctx, 0, 4, 0, 0));
        box.addView(controls, Ui.cardParams(ctx));
        body = Ui.vbox(ctx);
        box.addView(body, Ui.match());
        paintDate();
        reload();
        return Ui.scroll(ctx, box);
    }

    private Switch toggle(String label) {
        Switch s = new Switch(ctx);
        s.setText(label);
        s.setTextColor(Theme.text);
        s.setOnCheckedChangeListener((b, on) -> reload());
        return s;
    }

    private void paintDate() {
        dateButton.setText("Day: " + Format.date(date.toString()));
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        m.add(new ToolItem("Refresh", false, this::reload));
        return m;
    }

    @Override
    public void reload() {
        if (body == null) return;
        body.removeAllViews();
        body.addView(Ui.spinner(ctx));
        final Map<String, Object> q = new LinkedHashMap<>();
        q.put("date", date.toString());
        q.put("include_unpaid_revenue", unpaidRevenue.isChecked());
        q.put("include_unpaid_profit", unpaidProfit.isChecked());
        load(() -> act.api().get("/dashboard", q).optJSONObject("data"), d -> draw(d == null ? new JSONObject() : d), e -> {
            body.removeAllViews();
            body.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private void draw(JSONObject d) {
        body.removeAllViews();
        for (JSONObject a : Json.objects(d.opt("alerts"))) {
            LinearLayout card = Ui.card(ctx);
            card.setBackground(Ui.rounded(0xFFFFF4E0, 0xFFF1D7A6, 10, ctx));
            TextView msg = Ui.text(ctx, Json.str(a, "message"), 14, 0xFF7A4F00, false);
            card.addView(msg);
            final String route = Json.str(a, "action_route");
            if (!route.isEmpty()) {
                String label = Json.str(a, "action_label");
                card.addView(Ui.button(ctx, label.isEmpty() ? "Open" : label, Ui.Style.TEXT, v -> act.open(route)), Ui.wrap());
            }
            body.addView(card, Ui.cardParams(ctx));
        }
        JSONObject s = d.optJSONObject("summary");
        if (s == null) s = new JSONObject();
        JSONObject cmp = s.optJSONObject("compared_to_previous");
        if (cmp == null) cmp = new JSONObject();
        body.addView(stat("Transactions", Format.number(s.opt("transaction_count")), cmp.opt("transaction_count")), Ui.cardParams(ctx));
        body.addView(stat("Revenue", Format.money(s.opt("revenue")), cmp.opt("revenue")), Ui.cardParams(ctx));
        body.addView(stat("Profit", Format.money(s.opt("profit")), cmp.opt("profit")), Ui.cardParams(ctx));

        List<JSONObject> hourly = Json.objects(d.opt("hourly"));
        chart("Transactions", hourly, "transaction_count", "number");
        chart("Revenue", hourly, "revenue", "money");
        chart("Profit", hourly, "profit", "money");

        LinearLayout top = Ui.card(ctx);
        top.addView(Ui.text(ctx, "Top products", 15, Theme.text, true));
        List<JSONObject> tp = Json.objects(d.opt("top_products"));
        if (tp.isEmpty()) top.addView(Ui.muted(ctx, "No sales yet"));
        for (JSONObject p : tp) top.addView(Ui.pair(ctx, Json.str(p, "name"), Format.number(p.opt("quantity")) + " · " + Format.money(p.opt("revenue"))), Ui.match());
        body.addView(top, Ui.cardParams(ctx));

        LinearLayout low = Ui.card(ctx);
        low.addView(Ui.text(ctx, "Low stock", 15, Theme.text, true));
        List<JSONObject> ls = Json.objects(d.opt("low_stock"));
        if (ls.isEmpty()) low.addView(Ui.muted(ctx, "All stock levels are fine"));
        for (JSONObject p : ls) {
            low.addView(Ui.pair(ctx, Json.str(p, "name"), Format.number(p.opt("stock")) + " (min " + Format.number(p.opt("minimum_stock")) + ")"), Ui.match());
        }
        body.addView(low, Ui.cardParams(ctx));
    }

    private View stat(String label, String value, Object delta) {
        LinearLayout c = Ui.card(ctx);
        c.addView(Ui.muted(ctx, label));
        c.addView(Ui.text(ctx, value, 22, Theme.text, true));
        if (delta != null && delta != JSONObject.NULL) {
            double dv = Json.num(delta);
            TextView t = Ui.text(ctx, (dv >= 0 ? "▲ " : "▼ ") + Format.number(Math.abs(dv)) + "% vs previous day", 13, dv >= 0 ? Theme.success : Theme.danger, false);
            c.addView(t);
        }
        return c;
    }

    private void chart(String title, List<JSONObject> hourly, String key, String format) {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.text(ctx, title, 15, Theme.text, true));
        ChartView cv = new ChartView(ctx);
        List<String> labels = new ArrayList<>();
        double[] vals = new double[hourly.size()];
        for (int i = 0; i < hourly.size(); i++) {
            labels.add(Json.str(hourly.get(i), "hour"));
            vals[i] = Json.num(hourly.get(i).opt(key));
        }
        cv.setData(labels, Collections.singletonList(new ChartView.Series(title, vals)), false, format);
        card.addView(cv, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        body.addView(card, Ui.cardParams(ctx));
    }
}
