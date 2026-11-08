package id.lestari.mobile.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.Page;

/** Shown instead of a page whose plugin is not active. */
public final class PluginGateScreen extends Screen {
    private final Page def;

    public PluginGateScreen(MainActivity act, Page def) {
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
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(ctx, 24);
        box.setPadding(p, Ui.dp(ctx, 48), p, p);
        TextView lock = Ui.text(ctx, "🔒", 40, Theme.muted, false);
        lock.setGravity(Gravity.CENTER);
        box.addView(lock, Ui.match());
        TextView t = Ui.title(ctx, def.plugin == null ? "This page is not available" : "This feature needs the " + def.plugin + " plugin");
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.margins(Ui.match(), ctx, 0, 12, 0, 4));
        TextView s = Ui.muted(ctx, "Subscribe or start a 7-day trial to unlock it.");
        s.setGravity(Gravity.CENTER);
        box.addView(s, Ui.match());
        box.addView(Ui.button(ctx, "See plugins", Ui.Style.PRIMARY, v -> act.open("plugins")), Ui.margins(Ui.wrap(), ctx, 0, 16, 0, 0));
        return Ui.scroll(ctx, box);
    }
}
