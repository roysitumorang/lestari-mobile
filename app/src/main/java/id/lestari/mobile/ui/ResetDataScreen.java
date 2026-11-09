package id.lestari.mobile.ui;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.ApiException;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;

import org.json.JSONObject;

import java.time.Month;
import java.time.Year;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Permanently delete selected data: POST /settings/reset-data/otp, then POST /settings/reset-data. */
public final class ResetDataScreen extends Screen {
    private final Page def;
    private FormView form;

    public ResetDataScreen(MainActivity act, Page def) {
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
        View head = Ui.header(ctx, def.description);
        if (head != null) box.addView(head, Ui.match());
        LinearLayout warn = Ui.card(ctx);
        warn.setBackground(Ui.rounded(0xFFFDECEA, 0xFFF5C2C0, 10, ctx));
        warn.addView(Ui.text(ctx, I18n.t("Deleted data cannot be recovered. Download any reports you need first."), 14, Theme.danger, false));
        box.addView(warn, Ui.cardParams(ctx));

        int year = Year.now().getValue();
        List<Opt> years = Opt.pairs(year, String.valueOf(year), year - 1, String.valueOf(year - 1), year - 2, String.valueOf(year - 2),
                year - 3, String.valueOf(year - 3));
        List<Opt> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) months.add(new Opt(m, Month.of(m).getDisplayName(TextStyle.FULL, I18n.ID.equals(I18n.lang()) ? Format.LOCALE : Locale.ENGLISH)));
        List<Field> fields = Arrays.asList(
                Field.multi("data_types", I18n.t("What to delete"), Opt.pairs("images", I18n.t("Product images"), "products", I18n.t("Products"), "sales", I18n.t("Sales & reports"),
                        "purchases", I18n.t("Purchases"), "customers", I18n.t("Customers"), "suppliers", I18n.t("Suppliers"), "staff", I18n.t("Staff"), "taxes", I18n.t("Taxes"),
                        "discounts", I18n.t("Discounts"), "categories", I18n.t("Categories"), "fees", I18n.t("Fee module"), "promotions", I18n.t("Promotions"), "shifts", I18n.t("Cashier shifts"),
                        "points", I18n.t("Points"), "held_transactions", I18n.t("Held (open) bills"), "online_orders", I18n.t("Online-store orders"))).req().w(12),
                Field.select("scope", I18n.t("Period"), Opt.pairs("all", I18n.t("All time"), "year", I18n.t("One year"), "month", I18n.t("One month"))).req().def("all"),
                Field.select("year", I18n.t("Year"), years).showIf("scope", "year", "month"),
                Field.select("month", I18n.t("Month"), months).showIf("scope", "month"),
                Field.text("otp", I18n.t("Verification code")).req().help("Press \"Send code\" to receive it."));
        LinearLayout card = Ui.card(ctx);
        form = new FormView(act, fields, new JSONObject(), false);
        card.addView(form.view(), Ui.match());
        LinearLayout buttons = Ui.hbox(ctx);
        final Button send = Ui.button(ctx, I18n.t("Send code"), Ui.Style.OUTLINE, null);
        send.setOnClickListener(v -> {
            send.setEnabled(false);
            act.runQuietly(() -> act.api().post("/settings/reset-data/otp", Json.obj("channel", "email")).optJSONObject("data"), r -> {
                act.toast(I18n.t("Code sent to {to}", "to", r == null ? I18n.t("your email") : Json.str(r, "sent_to")));
                send.postDelayed(() -> send.setEnabled(true), 30000);
            }, e -> {
                send.setEnabled(true);
                act.error(e);
            });
        });
        Button reset = Ui.button(ctx, I18n.t("Reset now"), Ui.Style.DANGER, v -> reset());
        buttons.addView(send, Ui.margins(Ui.weight(1), ctx, 0, 0, 6, 0));
        buttons.addView(reset, Ui.margins(Ui.weight(1), ctx, 6, 0, 0, 0));
        card.addView(buttons, Ui.margins(Ui.match(), ctx, 0, 12, 0, 0));
        box.addView(card, Ui.cardParams(ctx));
        TextView note = Ui.muted(ctx, I18n.t("The reset runs in the background; you will get a notification when it is done."));
        note.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), 0);
        box.addView(note);
        return Ui.scroll(ctx, box);
    }

    private void reset() {
        if (!form.validate()) return;
        final JSONObject data = form.read();
        int n = Json.values(data.opt("data_types")).size();
        Dialogs.confirm(ctx, I18n.t("Reset data"), I18n.t("Delete {n} kind(s) of data permanently?", "n", n), I18n.t("Reset"), true, "RESET", () ->
                act.runQuietly(() -> act.api().post("/settings/reset-data", data), r -> act.toast(I18n.t("Reset started. You will be notified when it is done.")), e -> {
                    if (e instanceof ApiException) form.setErrors(((ApiException) e).fields);
                    act.error(e);
                }));
    }
}
