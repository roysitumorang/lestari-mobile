package id.lestari.mobile.ui;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Host;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Add-ons: GET /plugins, POST /plugins/{code}/subscribe, POST /plugins/{code}/trial. */
public final class PluginsScreen extends Screen {
    private final Page def;
    private LinearLayout list;

    public PluginsScreen(MainActivity act, Page def) {
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
    public void reload() {
        if (list == null) return;
        list.removeAllViews();
        list.addView(Ui.spinner(ctx));
        load(() -> Json.objects(act.api().get("/plugins").opt("data")), rows -> {
            list.removeAllViews();
            if (rows.isEmpty()) list.addView(Ui.empty(ctx, I18n.t("No plugins available")));
            for (JSONObject p : rows) list.addView(card(p), Ui.cardParams(ctx));
        }, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    private View card(final JSONObject p) {
        LinearLayout c = Ui.card(ctx);
        LinearLayout top = Ui.hbox(ctx);
        top.addView(Ui.text(ctx, Json.str(p, "name"), 16, Theme.text, true), Ui.weight(1));
        top.addView(Ui.badge(ctx, Json.str(p, "status")), Ui.wrap());
        c.addView(top, Ui.match());
        c.addView(Ui.muted(ctx, Json.str(p, "description")), Ui.margins(Ui.match(), ctx, 0, 4, 0, 4));
        for (Object f : Json.values(p.opt("features"))) c.addView(Ui.body(ctx, "• " + f));
        c.addView(Ui.text(ctx, Format.money(p.opt("price_monthly")) + " / month", 15, Theme.text, true), Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        if (!Format.blank(p.opt("expires_at"))) c.addView(Ui.muted(ctx, I18n.t("Until {date}", "date", Format.date(p.opt("expires_at")))));
        final String code = Json.str(p, "code");
        LinearLayout buttons = Ui.hbox(ctx);
        buttons.addView(Ui.button(ctx, I18n.t("active".equals(Json.str(p, "status")) ? "Renew" : "Subscribe"), Ui.Style.PRIMARY, v -> subscribe(code)),
                Ui.margins(Ui.wrap(), ctx, 0, 0, 8, 0));
        if (p.optBoolean("trial_available")) {
            final Button trial = Ui.button(ctx, I18n.t("Try 7 days"), Ui.Style.OUTLINE, null);
            trial.setOnClickListener(v -> {
                trial.setEnabled(false);
                act.runQuietly(() -> act.api().post("/plugins/" + code + "/trial", null), r -> {
                    act.toast(I18n.t("Trial started"));
                    act.refreshUser();
                    reload();
                }, e -> {
                    trial.setEnabled(true);
                    act.error(e);
                });
            });
            buttons.addView(trial, Ui.wrap());
        }
        c.addView(buttons, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        return c;
    }

    private void subscribe(final String code) {
        final JSONObject[] res = {null};
        Host.FormSpec spec = new Host.FormSpec();
        spec.title = I18n.t("Subscribe");
        spec.submitText = I18n.t("Continue");
        List<Opt> pay = new ArrayList<>(Opt.pairs("coin", I18n.t("Premium coin balance")));
        pay.addAll(Opt.pairs("virtual_account", I18n.t("Virtual account"), "qris", "QRIS", "e_wallet", I18n.t("E-wallet"), "credit_card", I18n.t("Credit card"),
                "convenience_store", I18n.t("Convenience store"), "manual_transfer", I18n.t("Manual bank transfer")));
        spec.fields = Arrays.asList(Field.select("period", I18n.t("Period"), Opt.pairs("monthly", I18n.t("Monthly"), "yearly", I18n.t("Yearly"))).req().def("monthly"),
                Field.select("payment_method", I18n.t("Pay with"), pay).req().def("coin"));
        spec.onSubmit = v -> res[0] = act.api().post("/plugins/" + code + "/subscribe", v);
        spec.onDone = () -> {
            Action.OPEN_PAYMENT.after(act, res[0], null);
            act.toast(I18n.t("Invoice created"));
            act.refreshUser();
            reload();
        };
        act.form(spec);
    }
}
