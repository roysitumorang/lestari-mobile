package id.lestari.mobile.ui;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Reward missions: GET /missions, POST /missions/{id}/claim. */
public final class MissionsScreen extends Screen {
    private final Page def;
    private LinearLayout list;

    public MissionsScreen(MainActivity act, Page def) {
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
        View head = Ui.header(ctx, def.description);
        if (head != null) box.addView(head, Ui.match());
        list = Ui.vbox(ctx);
        list.setPadding(0, 0, 0, Ui.dp(ctx, 24));
        box.addView(list, Ui.match());
        reload();
        return Ui.scroll(ctx, box);
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        m.add(new ToolItem("Refresh", false, this::reload));
        return m;
    }

    @Override
    public void reload() {
        if (list == null) return;
        list.removeAllViews();
        list.addView(Ui.spinner(ctx));
        load(() -> Json.objects(act.api().get("/missions").opt("data")), rows -> {
            list.removeAllViews();
            if (rows.isEmpty()) list.addView(Ui.empty(ctx, "No missions right now"));
            for (JSONObject m : rows) list.addView(card(m), Ui.cardParams(ctx));
        }, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private View card(final JSONObject m) {
        LinearLayout c = Ui.card(ctx);
        LinearLayout top = Ui.hbox(ctx);
        top.addView(Ui.text(ctx, Json.str(m, "title"), 16, Theme.text, true), Ui.weight(1));
        String status = Json.str(m, "status");
        top.addView(Ui.badge(ctx, status), Ui.wrap());
        c.addView(top, Ui.match());
        c.addView(Ui.muted(ctx, Json.str(m, "description")), Ui.margins(Ui.match(), ctx, 0, 4, 0, 8));
        int target = Math.max(1, m.optInt("target", 1));
        int progress = m.optInt("progress");
        ProgressBar bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(target);
        bar.setProgress(Math.min(progress, target));
        bar.setProgressTintList(ColorStateList.valueOf(Theme.primary));
        c.addView(bar, Ui.match());
        c.addView(Ui.body(ctx, progress + " / " + target + " · Reward: " + Format.number(m.opt("reward_amount")) + " " + Format.humanize(Json.str(m, "reward_type"))),
                Ui.margins(Ui.match(), ctx, 0, 4, 0, 4));
        if ("completed".equals(status)) {
            final Button claim = Ui.button(ctx, "Claim reward", Ui.Style.PRIMARY, null);
            claim.setOnClickListener(v -> {
                claim.setEnabled(false);
                act.runQuietly(() -> act.api().post("/missions/" + Json.str(m, "id") + "/claim", null), r -> {
                    act.toast("Reward claimed");
                    reload();
                }, e -> {
                    claim.setEnabled(true);
                    act.error(e);
                });
            });
            c.addView(claim, Ui.wrap());
        } else {
            final String route = Json.str(m, "action_route");
            if (!route.isEmpty() && "in_progress".equals(status)) c.addView(Ui.button(ctx, "Go", Ui.Style.OUTLINE, v -> act.open(route)), Ui.wrap());
        }
        return c;
    }
}
