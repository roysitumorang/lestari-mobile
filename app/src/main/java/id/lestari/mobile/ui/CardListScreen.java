package id.lestari.mobile.ui;

import android.view.View;
import android.widget.LinearLayout;

import id.lestari.mobile.core.ApiException;
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

/** Card grids for integrations (connect/disconnect) and business loans (apply). */
public final class CardListScreen extends Screen {
    interface CardBuilder {
        void build(CardListScreen screen, LinearLayout card, JSONObject row);
    }

    private final Page def;
    private final String endpoint;
    private final CardBuilder builder;
    private LinearLayout list;

    private CardListScreen(MainActivity act, Page def, String endpoint, CardBuilder builder) {
        super(act);
        this.def = def;
        this.endpoint = endpoint;
        this.builder = builder;
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
        load(() -> Json.objects(act.api().get(endpoint).opt("data")), rows -> {
            list.removeAllViews();
            if (rows.isEmpty()) list.addView(Ui.empty(ctx, I18n.t("Nothing available yet")));
            for (JSONObject r : rows) {
                LinearLayout card = Ui.card(ctx);
                LinearLayout top = Ui.hbox(ctx);
                String logo = Json.str(r, "logo_url");
                if (!logo.isEmpty()) {
                    View iv = Ui.thumb(ctx, logo, 40);
                    ((LinearLayout.LayoutParams) iv.getLayoutParams()).setMargins(0, 0, Ui.dp(ctx, 12), 0);
                    top.addView(iv);
                }
                top.addView(Ui.text(ctx, Json.str(r, "name"), 16, Theme.text, true), Ui.weight(1));
                if (r.has("status")) top.addView(Ui.badge(ctx, Json.str(r, "status")), Ui.wrap());
                card.addView(top, Ui.match());
                card.addView(Ui.muted(ctx, Json.str(r, "description")), Ui.margins(Ui.match(), ctx, 0, 4, 0, 4));
                builder.build(this, card, r);
                list.addView(card, Ui.cardParams(ctx));
            }
        }, e -> {
            list.removeAllViews();
            list.addView(Ui.empty(ctx, e.getMessage()));
        });
    }

    /** GET /integrations; POST /integrations/{code}/activate (accept_terms) or /deactivate. */
    static CardListScreen integrations(MainActivity act, Page def) {
        return new CardListScreen(act, def, "/integrations", (s, card, i) -> {
            final String code = Json.str(i, "code");
            LinearLayout buttons = Ui.hbox(s.ctx);
            if ("inactive".equals(Json.str(i, "status"))) {
                buttons.addView(Ui.button(s.ctx, I18n.t("Connect"), Ui.Style.PRIMARY, v -> Dialogs.confirm(s.ctx, I18n.t("Connect"),
                        I18n.t("I have read and accept the terms of service of this integration."), I18n.t("Accept & connect"), false, null,
                        () -> act.run(() -> act.api().post("/integrations/" + code + "/activate", Json.obj("accept_terms", true)).optJSONObject("data"), r -> {
                            if (r != null && !Json.str(r, "redirect_url").isEmpty()) act.openUrl(Json.str(r, "redirect_url"));
                            act.toast(I18n.t("Integration requested"));
                            s.reload();
                        }))), Ui.wrap());
            } else {
                buttons.addView(Ui.button(s.ctx, I18n.t("Disconnect"), Ui.Style.OUTLINE, v -> Dialogs.confirm(s.ctx, I18n.t("Disconnect"), I18n.t("Disconnect this integration?"),
                        I18n.t("Disconnect"), true, null, () -> act.run(() -> act.api().post("/integrations/" + code + "/deactivate", null), r -> {
                            act.toast(I18n.t("Disconnected"));
                            s.reload();
                        }))), Ui.wrap());
            }
            final String terms = Json.str(i, "terms_url");
            if (!terms.isEmpty()) buttons.addView(Ui.button(s.ctx, I18n.t("Terms"), Ui.Style.TEXT, v -> act.openUrl(terms)), Ui.wrap());
            card.addView(buttons, Ui.margins(Ui.match(), s.ctx, 0, 8, 0, 0));
        });
    }

    /** GET /loans/providers; POST /loans/applications. */
    static CardListScreen loans(MainActivity act, Page def) {
        return new CardListScreen(act, def, "/loans/providers", (s, card, p) -> {
            card.addView(Ui.body(s.ctx, Format.money(p.opt("min_amount")) + " – " + Format.money(p.opt("max_amount"))));
            String interest = Json.str(p, "interest_info");
            if (!interest.isEmpty()) card.addView(Ui.muted(s.ctx, interest));
            LinearLayout buttons = Ui.hbox(s.ctx);
            buttons.addView(Ui.button(s.ctx, I18n.t("Apply"), Ui.Style.PRIMARY, v -> apply(act, p)), Ui.wrap());
            final String info = Json.str(p, "info_url");
            if (!info.isEmpty()) buttons.addView(Ui.button(s.ctx, I18n.t("Details"), Ui.Style.TEXT, v -> act.openUrl(info)), Ui.wrap());
            card.addView(buttons, Ui.margins(Ui.match(), s.ctx, 0, 8, 0, 0));
        });
    }

    private static void apply(final MainActivity act, final JSONObject p) {
        List<Opt> tenors = new ArrayList<>();
        for (Object t : Json.values(p.opt("tenors_months"))) tenors.add(new Opt(t, I18n.t("{n} months", "n", FormView.plain(t))));
        final JSONObject[] res = {null};
        Host.FormSpec spec = new Host.FormSpec();
        spec.title = I18n.t("Apply") + " · " + Json.str(p, "name");
        spec.submitText = I18n.t("Submit application");
        Field amount = Field.money("amount", I18n.t("Amount")).req();
        if (p.has("min_amount")) amount.min(Json.num(p.opt("min_amount")));
        if (p.has("max_amount")) amount.max(Json.num(p.opt("max_amount")));
        spec.fields = Arrays.asList(amount, Field.select("tenor_months", I18n.t("Tenor"), tenors).req(),
                Field.textarea("purpose", I18n.t("What is the loan for?")).req(),
                Field.sw("consent", I18n.t("I agree to share my store and sales data with this partner")).req().w(12));
        spec.onSubmit = v -> {
            if (!v.optBoolean("consent")) {
                ApiException e = new ApiException(422, "validation_failed", I18n.t("Please give consent to continue"));
                e.fields.put("consent", I18n.t("Required"));
                throw e;
            }
            JSONObject body = Json.copy(v);
            Json.put(body, "provider_code", Json.str(p, "code"));
            res[0] = act.api().post("/loans/applications", body);
        };
        spec.onDone = () -> {
            JSONObject d = res[0] == null ? null : res[0].optJSONObject("data");
            if (d != null && !Json.str(d, "redirect_url").isEmpty()) act.openUrl(Json.str(d, "redirect_url"));
            act.toast(I18n.t("Application submitted"));
        };
        act.form(spec);
    }
}
