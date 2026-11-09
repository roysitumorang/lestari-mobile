package id.lestari.mobile.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.ApiException;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;

import org.json.JSONObject;

/** Sign in (owner or staff). The password is sent base64-encoded; a second step asks for the 2FA code. */
public final class LoginScreen extends Screen {
    private EditText server;
    private EditText email;
    private EditText password;
    private EditText otp;
    private Switch remember;
    private TextView error;
    private LinearLayout otpBox;
    private Button go;

    /** Language picked on this screen (kept across the rebuild the switch causes). */
    static String chosenLanguage;

    public LoginScreen(MainActivity act) {
        super(act);
    }

    @Override
    public String title() {
        return I18n.t("Sign in");
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.muted);
        e.setBackground(Ui.rounded(Theme.surface, Theme.border, 8, ctx));
        int p = Ui.dp(ctx, 12);
        e.setPadding(p, p, p, p);
        return e;
    }

    private void label(LinearLayout box, String s) {
        TextView t = Ui.text(ctx, s, 13, Theme.muted, false);
        t.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 4));
        box.addView(t);
    }

    @Override
    protected View create() {
        LinearLayout box = Ui.vbox(ctx);
        int p = Ui.dp(ctx, 24);
        box.setPadding(p, p, p, p);
        TextView brand = Ui.text(ctx, I18n.t("Lestari Back Office"), 24, Theme.primary, true);
        brand.setGravity(Gravity.CENTER);
        box.addView(brand, Ui.match());
        TextView sub = Ui.muted(ctx, I18n.t("Sign in with your owner or staff account."));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 12));
        box.addView(sub, Ui.match());

        // EN | ID switcher; a choice made here is pushed to the account after signing in
        LinearLayout langs = Ui.hbox(ctx);
        langs.setGravity(Gravity.CENTER);
        for (String[] l : new String[][]{{I18n.EN, "English"}, {I18n.ID, "Bahasa Indonesia"}}) {
            boolean on = l[0].equals(I18n.lang());
            Button b = Ui.button(ctx, l[1], on ? Ui.Style.PRIMARY : Ui.Style.OUTLINE, v -> {
                if (l[0].equals(I18n.lang())) return;
                chosenLanguage = l[0];
                act.changeLanguage(l[0]);
            });
            langs.addView(b, Ui.margins(Ui.wrap(), ctx, 4, 0, 4, 0));
        }
        box.addView(langs, Ui.margins(Ui.match(), ctx, 0, 0, 0, 12));

        LinearLayout card = Ui.card(ctx);
        label(card, I18n.t("Email"));
        email = input("you@store.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(email, Ui.match());
        label(card, I18n.t("Password"));
        password = input("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(password, Ui.match());
        otpBox = Ui.vbox(ctx);
        label(otpBox, I18n.t("Code from your authenticator app"));
        otp = input("000000", InputType.TYPE_CLASS_NUMBER);
        otpBox.addView(otp, Ui.match());
        otpBox.setVisibility(View.GONE);
        card.addView(otpBox, Ui.match());
        remember = new Switch(ctx);
        remember.setText(I18n.t("Remember me"));
        remember.setTextColor(Theme.text);
        remember.setChecked(true);
        card.addView(remember, Ui.margins(Ui.match(), ctx, 0, 12, 0, 0));
        error = Ui.text(ctx, "", 14, Theme.danger, false);
        error.setVisibility(View.GONE);
        card.addView(error, Ui.margins(Ui.match(), ctx, 0, 8, 0, 0));
        go = Ui.button(ctx, I18n.t("Sign in"), Ui.Style.PRIMARY, v -> signIn());
        card.addView(go, Ui.margins(Ui.match(), ctx, 0, 16, 0, 0));
        box.addView(card, Ui.match());

        LinearLayout srv = Ui.card(ctx);
        srv.addView(Ui.text(ctx, I18n.t("Server"), 13, Theme.muted, false));
        server = input(Api.DEFAULT_BASE_URL, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(act.api().baseUrl());
        srv.addView(server, Ui.margins(Ui.match(), ctx, 0, 4, 0, 0));
        TextView hint = Ui.muted(ctx, I18n.t("Your Lestari API address, e.g. https://backoffice.example.com/api/v1.") + " "
                + I18n.t("The Android emulator reaches a backend on your computer at http://10.0.2.2:8080/api/v1."));
        hint.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        srv.addView(hint);
        box.addView(srv, Ui.margins(Ui.match(), ctx, 0, 16, 0, 0));
        return Ui.scroll(ctx, box);
    }

    private void signIn() {
        String url = server.getText().toString().trim().replaceAll("/+$", "");
        if (url.isEmpty()) url = Api.DEFAULT_BASE_URL;
        act.api().store().set(Api.K_BASE_URL, url);
        String mail = email.getText().toString().trim();
        String pw = password.getText().toString();
        if (mail.isEmpty() || pw.isEmpty()) {
            showError(I18n.t("Enter your email and password."));
            return;
        }
        final JSONObject body = Json.obj("email", mail, "password", Api.encodePassword(pw), "remember_me", remember.isChecked());
        if (otpBox.getVisibility() == View.VISIBLE) Json.put(body, "otp", otp.getText().toString().trim());
        go.setEnabled(false);
        go.setText(I18n.t("Signing in…"));
        error.setVisibility(View.GONE);
        load(() -> {
            JSONObject d = act.api().post("/auth/login", body).getJSONObject("data");
            Api.Store s = act.api().store();
            s.set(Api.K_TOKEN, d.getString("access_token"));
            s.set(Api.K_REFRESH, d.getString("refresh_token"));
            s.set(Api.K_BRANCH, null);
            JSONObject user = d.optJSONObject("user");
            s.set(Api.K_USER, user == null ? "{}" : user.toString());
            try {
                JSONObject display = act.api().get("/settings/display").optJSONObject("data");
                if (display != null && chosenLanguage != null) {
                    // the language picked on the sign-in screen wins over the account's stored one
                    act.api().put("/settings/display", Json.obj("language", chosenLanguage));
                    Json.put(display, "language", chosenLanguage);
                }
                if (display != null) s.set(Api.K_THEME, display.toString());
            } catch (ApiException ignored) {
                // appearance is optional
            }
            return Boolean.TRUE;
        }, ok -> {
            JSONObject display = Json.obj();
            try {
                display = new JSONObject(act.api().store().get(Api.K_THEME));
            } catch (Exception ignored) {
                // default theme
            }
            Theme.apply(display.optString("color_theme", "emerald"));
            chosenLanguage = null;
            act.signedInWithLanguage(display.optString("language"));
        }, e -> {
            go.setEnabled(true);
            go.setText(I18n.t("Sign in"));
            if (e instanceof ApiException && "two_factor_required".equals(((ApiException) e).code)) {
                otpBox.setVisibility(View.VISIBLE);
                otp.requestFocus();
                showError(I18n.t("Enter the code from your authenticator app."));
            } else {
                showError(e.getMessage());
            }
        });
    }

    private void showError(String s) {
        error.setText(s);
        error.setVisibility(View.VISIBLE);
    }
}
