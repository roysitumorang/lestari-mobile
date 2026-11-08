package id.lestari.mobile.ui;

import android.app.DatePickerDialog;
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
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic report screen: period + filters → summary cards, chart, rows, totals, paging and export. */
public final class ReportScreen extends Screen implements Action.Ctx {
    private static final List<Opt> PRESETS = Opt.pairs("today", "Today", "yesterday", "Yesterday", "last_7_days", "Last 7 days",
            "last_30_days", "Last 30 days", "this_month", "This month", "last_month", "Last month", "this_year", "This year", "custom", "Custom");

    private final Page def;
    private String preset;
    private LocalDate start;
    private LocalDate end;
    private Object group;
    private String q = "";
    private int page = 1;
    private JSONObject filters = new JSONObject();
    private FormView filterForm;
    private Button presetButton;
    private Button startButton;
    private Button endButton;
    private LinearLayout summaryBox;
    private ChartView chart;
    private LinearLayout chartCard;
    private LinearLayout list;
    private LinearLayout pager;
    private final List<JSONObject> rows = new ArrayList<>();
    private final Handler debounce = new Handler(Looper.getMainLooper());

    public ReportScreen(MainActivity act, Page def) {
        super(act);
        this.def = def;
        preset = def.defaultPreset;
        LocalDate[] r = range(preset);
        start = r[0];
        end = r[1];
        if (def.groupBy != null) group = def.groupBy.get(0).value;
    }

    /** Same presets as the web client. */
    static LocalDate[] range(String preset) {
        LocalDate d = LocalDate.now();
        switch (preset) {
            case "yesterday":
                return new LocalDate[]{d.minusDays(1), d.minusDays(1)};
            case "last_7_days":
                return new LocalDate[]{d.minusDays(6), d};
            case "last_30_days":
                return new LocalDate[]{d.minusDays(29), d};
            case "this_month":
                return new LocalDate[]{d.withDayOfMonth(1), d};
            case "last_month": {
                LocalDate first = d.withDayOfMonth(1).minusMonths(1);
                return new LocalDate[]{first, d.withDayOfMonth(1).minusDays(1)};
            }
            case "this_year":
                return new LocalDate[]{d.withDayOfYear(1), d};
            default:
                return new LocalDate[]{d, d};
        }
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

        LinearLayout controls = Ui.card(ctx);
        if (def.dateRange) {
            presetButton = Ui.button(ctx, "", Ui.Style.OUTLINE, v -> Dialogs.pick(ctx, "Period", PRESETS, false, o -> {
                preset = String.valueOf(o.value);
                if (!"custom".equals(preset)) {
                    LocalDate[] r = range(preset);
                    start = r[0];
                    end = r[1];
                    page = 1;
                    reload();
                }
                paintDates();
            }));
            controls.addView(presetButton, Ui.match());
            LinearLayout dates = Ui.hbox(ctx);
            startButton = Ui.button(ctx, "", Ui.Style.OUTLINE, v -> pickDate(true));
            endButton = Ui.button(ctx, "", Ui.Style.OUTLINE, v -> pickDate(false));
            dates.addView(startButton, Ui.margins(Ui.weight(1), ctx, 0, 0, 4, 0));
            dates.addView(endButton, Ui.margins(Ui.weight(1), ctx, 4, 0, 0, 0));
            controls.addView(dates, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
            paintDates();
        }
        if (def.groupBy != null) {
            Field g = Field.select(def.groupByKey, def.groupByLabel, def.groupBy).def(group).req();
            final FormView gv = new FormView(act, java.util.Collections.singletonList(g), new JSONObject(), true);
            gv.setOnChange(() -> {
                group = gv.read().opt(def.groupByKey);
                page = 1;
                reload();
            });
            controls.addView(gv.view(), Ui.match());
        }
        if (!def.filters.isEmpty()) {
            filterForm = new FormView(act, def.filters, new JSONObject(), true);
            controls.addView(filterForm.view(), Ui.match());
        }
        if (def.search != null) {
            EditText search = new EditText(ctx);
            search.setHint(def.search);
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
            controls.addView(search, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        }
        Button apply = Ui.button(ctx, "Apply", Ui.Style.PRIMARY, v -> {
            if (filterForm != null) filters = filterForm.read();
            page = 1;
            reload();
        });
        controls.addView(apply, Ui.margins(Ui.match(), ctx, 0, 10, 0, 0));
        box.addView(controls, Ui.cardParams(ctx));

        summaryBox = Ui.vbox(ctx);
        box.addView(summaryBox, Ui.match());
        if (def.chart != null) {
            chartCard = Ui.card(ctx);
            chartCard.addView(Ui.text(ctx, def.chart.title, 15, Theme.text, true));
            chart = new ChartView(ctx);
            chartCard.addView(chart, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
            box.addView(chartCard, Ui.cardParams(ctx));
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

    private void paintDates() {
        presetButton.setText("Period: " + Format.optionLabel(PRESETS, preset));
        startButton.setText("From " + Format.date(start.toString()));
        endButton.setText("To " + Format.date(end.toString()));
    }

    private void pickDate(final boolean isStart) {
        LocalDate d = isStart ? start : end;
        new DatePickerDialog(ctx, (v, y, m, day) -> {
            LocalDate picked = LocalDate.of(y, m + 1, day);
            if (isStart) start = picked;
            else end = picked;
            if (end.isBefore(start)) {
                LocalDate t = start;
                start = end;
                end = t;
            }
            preset = "custom";
            paintDates();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    @Override
    public Map<String, Object> query() {
        return query(false);
    }

    private Map<String, Object> query(boolean withPage) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Iterator<String> it = filters.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = filters.opt(k);
            if (v != null && v != JSONObject.NULL) m.put(k, v);
        }
        if (def.dateRange) {
            m.put("start_date", start.toString());
            m.put("end_date", end.toString());
        }
        if (def.groupBy != null) m.put(def.groupByKey, group);
        if (!q.isEmpty()) m.put("q", q);
        if (withPage && def.paginated) {
            m.put("page", page);
            m.put("per_page", 50);
        }
        return m;
    }

    @Override
    public void reload() {
        if (list == null) return;
        list.removeAllViews();
        list.addView(Ui.spinner(ctx));
        pager.removeAllViews();
        final Map<String, Object> qy = query(true);
        load(() -> act.api().get(def.endpoint, qy), this::draw, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private void draw(JSONObject res) {
        rows.clear();
        rows.addAll(Json.objects(res.opt("data")));
        drawSummary(res.optJSONObject("summary"));
        drawChart(res);
        list.removeAllViews();
        if (rows.isEmpty()) list.addView(Ui.empty(ctx, "No data for this period"));
        for (int i = 0; i < rows.size(); i++) {
            final JSONObject row = rows.get(i);
            LinearLayout card = Ui.rowCard(ctx, def.columns, row, null);
            card.setOnClickListener(v -> rowMenu(row));
            list.addView(card, Ui.cardParams(ctx));
        }
        JSONObject totals = res.optJSONObject("totals");
        if (totals != null && !rows.isEmpty()) {
            LinearLayout card = Ui.card(ctx);
            card.addView(Ui.text(ctx, "Total", 16, Theme.text, true));
            for (Field c : def.columns) {
                if (c.key != null && totals.has(c.key)) card.addView(Ui.pair(ctx, c.label, Format.cell(c, totals)), Ui.match());
            }
            card.setBackground(Ui.rounded(Theme.chipBg, Theme.border, 10, ctx));
            list.addView(card, Ui.cardParams(ctx));
        }
        JSONObject m = res.optJSONObject("meta");
        pager.removeAllViews();
        if (def.paginated && m != null && m.optInt("total_pages", 1) > 1) {
            final int pg = m.optInt("page", 1);
            final int pages = m.optInt("total_pages", 1);
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
            TextView info = Ui.muted(ctx, "Page " + pg + " of " + pages + " · " + Format.number(m.opt("total")) + " rows");
            info.setGravity(Gravity.CENTER);
            pager.addView(prev, Ui.wrap());
            pager.addView(info, Ui.weight(1));
            pager.addView(next, Ui.wrap());
        }
    }

    private void drawSummary(JSONObject sum) {
        summaryBox.removeAllViews();
        if (def.summary.isEmpty()) return;
        JSONObject s = sum == null ? new JSONObject() : sum;
        LinearLayout row = null;
        for (int i = 0; i < def.summary.size(); i++) {
            if (i % 2 == 0) {
                row = Ui.hbox(ctx);
                row.setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8), 0);
                summaryBox.addView(row, Ui.match());
            }
            Field f = def.summary.get(i);
            row.addView(stat(f.label, Format.cell(f, s)), Ui.margins(Ui.weight(1), ctx, 4, 4, 4, 4));
        }
        if (def.summary.size() % 2 == 1 && row != null) row.addView(new View(ctx), Ui.margins(Ui.weight(1), ctx, 4, 4, 4, 4));
    }

    View stat(String label, String value) {
        LinearLayout c = Ui.card(ctx);
        c.addView(Ui.muted(ctx, label));
        TextView v = Ui.text(ctx, value, 18, Theme.text, true);
        v.setPadding(0, Ui.dp(ctx, 4), 0, 0);
        c.addView(v);
        return c;
    }

    private void drawChart(JSONObject res) {
        if (chart == null) return;
        List<JSONObject> src = Json.objects("data".equals(def.chart.source) ? res.opt("data") : res.opt("chart"));
        List<String> labels = new ArrayList<>();
        for (JSONObject r : src) labels.add(def.chart.label.equals("date") ? Format.date(Json.get(r, "date")) : Json.str(r, def.chart.label));
        List<ChartView.Series> series = new ArrayList<>();
        for (String[] s : def.chart.series) {
            double[] vals = new double[src.size()];
            for (int i = 0; i < src.size(); i++) vals[i] = Json.num(Json.get(src.get(i), s[0]));
            series.add(new ChartView.Series(s[1], vals));
        }
        chart.setData(labels, series, "bar".equals(def.chart.type), def.chart.format);
    }

    // ------------------------------------------------------------------ rows & toolbar

    private void rowMenu(final JSONObject row) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> runs = new ArrayList<>();
        if (def.detail != null) {
            labels.add("Details");
            runs.add(() -> detail(row));
        }
        for (final Action a : def.rowActions) {
            if (!a.appliesTo(row)) continue;
            labels.add(a.label);
            runs.add(() -> a.run(act, row, this));
        }
        if (labels.isEmpty()) return;
        if (labels.size() == 1 && def.detail != null) {
            runs.get(0).run();
            return;
        }
        Dialogs.sheet(ctx, def.title, labels, w -> runs.get(w).run());
    }

    private void detail(final JSONObject row) {
        final Page.Detail d = def.detail;
        final String path = Action.fillPath(d.endpoint, row);
        act.push(new DetailScreen(act, d.title, () -> act.api().get(path).optJSONObject("data"), d.fields, d.tableKey, d.tableColumns));
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        if (def.export) m.add(new ToolItem("Download", def.toolbar.isEmpty(), this::export));
        for (final Action a : def.toolbar) m.add(new ToolItem(a.label, false, () -> a.run(act, null, this)));
        m.add(new ToolItem("Refresh", false, this::reload));
        return m;
    }

    private void export() {
        final Map<String, Object> qy = query(false);
        qy.put("format", "xlsx");
        act.toast("Preparing report…");
        act.run(() -> {
            act.saveFile(act.api().download(def.endpoint + "/export", qy), def.id + ".xlsx");
            return Boolean.TRUE;
        }, ok -> {});
    }
}
