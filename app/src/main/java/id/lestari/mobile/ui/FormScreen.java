package id.lestari.mobile.ui;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.ApiException;
import id.lestari.mobile.core.Host;

import java.util.ArrayList;
import java.util.List;

/** A full-screen form (create/edit records, action forms). Submits in the background; field errors are shown inline. */
public final class FormScreen extends Screen {
    private final Host.FormSpec spec;
    private FormView form;
    private Button submit;
    private boolean busy;

    public FormScreen(MainActivity act, Host.FormSpec spec) {
        super(act);
        this.spec = spec;
    }

    @Override
    public String title() {
        return spec.title;
    }

    @Override
    protected View create() {
        LinearLayout box = Ui.vbox(ctx);
        int p = Ui.dp(ctx, 16);
        box.setPadding(p, Ui.dp(ctx, 8), p, Ui.dp(ctx, 24));
        if (spec.intro != null) {
            TextView intro = Ui.body(ctx, spec.intro);
            intro.setTextIsSelectable(true);
            intro.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
            box.addView(intro, Ui.match());
        }
        if (spec.linkUrl != null && !spec.linkUrl.isEmpty()) {
            box.addView(Ui.button(ctx, spec.linkLabel == null ? "Open link" : spec.linkLabel, Ui.Style.OUTLINE, v -> act.openUrl(spec.linkUrl)), Ui.wrap());
        }
        form = new FormView(act, spec.fields, spec.values, false);
        box.addView(form.view(), Ui.match());
        submit = Ui.button(ctx, spec.submitText, Ui.Style.PRIMARY, v -> submit());
        box.addView(submit, Ui.margins(Ui.match(), ctx, 0, 16, 0, 0));
        return Ui.scroll(ctx, box);
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        m.add(new ToolItem(spec.submitText, true, this::submit));
        return m;
    }

    private void submit() {
        if (busy || !form.validate()) return;
        busy = true;
        submit.setEnabled(false);
        final org.json.JSONObject values = form.read();
        load(() -> {
            spec.onSubmit.submit(values);
            return Boolean.TRUE;
        }, ok -> {
            busy = false;
            act.pop();
            if (spec.onDone != null) spec.onDone.run();
        }, e -> {
            busy = false;
            submit.setEnabled(true);
            if (e instanceof ApiException && !((ApiException) e).fields.isEmpty()) form.setErrors(((ApiException) e).fields);
            act.error(e);
        });
    }
}
