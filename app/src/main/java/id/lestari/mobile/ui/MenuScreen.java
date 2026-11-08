package id.lestari.mobile.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;
import id.lestari.mobile.defs.Defs;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Home: store/user header, branch switcher, notifications and the navigation menu (the web client's sidebar). */
public final class MenuScreen extends Screen {
    private TextView storeName;
    private TextView userLine;
    private Button branchButton;
    private Button bellButton;
    private LinearLayout list;
    private String query = "";
    private final Set<String> open = new HashSet<>();
    private final List<JSONObject> branches = new ArrayList<>();

    public MenuScreen(MainActivity act) {
        super(act);
    }

    @Override
    public String title() {
        return "Lestari";
    }

    @Override
    protected View create() {
        LinearLayout box = Ui.vbox(ctx);
        box.setPadding(0, 0, 0, Ui.dp(ctx, 24));

        LinearLayout head = Ui.card(ctx);
        storeName = Ui.text(ctx, "", 18, Theme.text, true);
        userLine = Ui.muted(ctx, "");
        head.addView(storeName);
        head.addView(userLine, Ui.margins(Ui.match(), ctx, 0, 2, 0, 8));
        LinearLayout buttons = Ui.hbox(ctx);
        branchButton = Ui.button(ctx, "Branch", Ui.Style.OUTLINE, v -> chooseBranch());
        branchButton.setVisibility(View.GONE);
        bellButton = Ui.button(ctx, "Notifications", Ui.Style.OUTLINE, v -> act.open("notifications"));
        buttons.addView(bellButton, Ui.margins(Ui.wrap(), ctx, 0, 0, 8, 0));
        buttons.addView(branchButton, Ui.wrap());
        head.addView(Ui.hscroll(ctx, buttons));
        box.addView(head, Ui.margins(Ui.match(), ctx, 12, 12, 12, 6));

        EditText search = new EditText(ctx);
        search.setHint("Search menu");
        search.setSingleLine(true);
        search.setTextColor(Theme.text);
        search.setHintTextColor(Theme.muted);
        search.setBackground(Ui.rounded(Theme.surface, Theme.border, 8, ctx));
        int p = Ui.dp(ctx, 12);
        search.setPadding(p, p, p, p);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                drawMenu();
            }
        });
        box.addView(search, Ui.margins(Ui.match(), ctx, 12, 6, 12, 6));

        list = Ui.vbox(ctx);
        box.addView(list, Ui.match());
        refreshHeader();
        drawMenu();
        loadBranches();
        return Ui.scroll(ctx, box);
    }

    @Override
    public List<ToolItem> menu() {
        List<ToolItem> m = new ArrayList<>();
        m.add(new ToolItem("Profile", false, () -> act.open("profile")));
        m.add(new ToolItem("Change password", false, () -> Defs.changePassword().run(act, null, null)));
        m.add(new ToolItem("Sign out", false, this::signOut));
        return m;
    }

    @Override
    public void reload() {
        refreshHeader();
        loadBranches();
    }

    void refreshHeader() {
        if (storeName == null) return;
        JSONObject me = act.user();
        JSONObject store = me.optJSONObject("store");
        JSONObject branch = me.optJSONObject("branch");
        storeName.setText(store != null ? Json.str(store, "name") : "Lestari");
        String name = Json.str(me, "name").isEmpty() ? Json.str(me, "email") : Json.str(me, "name");
        String line = name + " · " + Json.str(me, "role") + (branch != null ? " · " + Json.str(branch, "name") : "");
        userLine.setText(line);
        int n = act.unread();
        bellButton.setText(n > 0 ? "Notifications (" + (n > 99 ? "99+" : n) + ")" : "Notifications");
        drawMenu();
    }

    private void loadBranches() {
        load(() -> {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("per_page", 100);
            return Json.objects(act.api().get("/branches", q).opt("data"));
        }, rows -> {
            branches.clear();
            branches.addAll(rows);
            if (rows.size() < 2) {
                branchButton.setVisibility(View.GONE);
                return;
            }
            String cur = act.api().store().get(Api.K_BRANCH);
            JSONObject b = act.user().optJSONObject("branch");
            if (cur == null && b != null) cur = Json.str(b, "id");
            String label = "Branch";
            for (JSONObject r : rows) if (Json.str(r, "id").equals(cur)) label = Json.str(r, "name");
            branchButton.setText(label + " ▾");
            branchButton.setVisibility(View.VISIBLE);
        }, e -> branchButton.setVisibility(View.GONE));
    }

    private void chooseBranch() {
        List<Opt> opts = new ArrayList<>();
        for (JSONObject r : branches) opts.add(new Opt(Json.str(r, "id"), Json.str(r, "name") + (r.optBoolean("is_head_office") ? " (head office)" : "")));
        Dialogs.pick(ctx, "Switch branch", opts, false, o -> act.switchBranch(String.valueOf(o.value)));
    }

    private void signOut() {
        Dialogs.confirm(ctx, "Sign out", "Sign out of this device?", "Sign out", false, null, () -> load(() -> {
            try {
                act.api().post("/auth/logout", null);
            } catch (Exception ignored) {
                // token already invalid — sign out locally anyway
            }
            return Boolean.TRUE;
        }, ok -> {
            act.api().clearSession();
            act.showLogin();
        }, e -> {
            act.api().clearSession();
            act.showLogin();
        }));
    }

    // ------------------------------------------------------------------ menu tree

    private void drawMenu() {
        if (list == null) return;
        list.removeAllViews();
        Registry r = act.registry();
        for (Registry.Group g : r.menu()) {
            if (g.items.size() == 1 && g.items.get(0) instanceof String) {
                View link = link((String) g.items.get(0), 0);
                if (link != null) list.addView(link, Ui.match());
            } else {
                group(g, 0, list);
            }
        }
    }

    private boolean matches(Object item) {
        if (query.isEmpty()) return true;
        if (item instanceof String) {
            Page p = act.registry().page((String) item);
            return p != null && p.title.toLowerCase(Locale.ROOT).contains(query);
        }
        for (Object x : ((Registry.Group) item).items) if (matches(x)) return true;
        return false;
    }

    private void group(final Registry.Group g, final int depth, LinearLayout into) {
        if (!matches(g)) return;
        final String id = depth + ":" + g.label;
        final boolean expanded = !query.isEmpty() || open.contains(id);
        TextView head = row((expanded ? "▾  " : "▸  ") + g.label, depth, true);
        head.setOnClickListener(v -> {
            if (!open.remove(id)) open.add(id);
            drawMenu();
        });
        into.addView(head, Ui.match());
        if (!expanded) return;
        for (Object it : g.items) {
            if (it instanceof String) {
                View l = link((String) it, depth + 1);
                if (l != null) into.addView(l, Ui.match());
            } else {
                group((Registry.Group) it, depth + 1, into);
            }
        }
    }

    private View link(final String pageId, int depth) {
        final Page p = act.registry().page(pageId);
        if (p == null || !matches(pageId)) return null;
        String label = p.title;
        if (p.plugin != null && !act.hasPlugin(p.plugin)) label += "  ★";
        TextView t = row(label, depth, false);
        t.setOnClickListener(v -> act.open(pageId));
        return t;
    }

    private TextView row(String text, int depth, boolean groupRow) {
        TextView t = Ui.text(ctx, text, 16, Theme.text, groupRow);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(Ui.dp(ctx, 48));
        t.setPadding(Ui.dp(ctx, 20 + depth * 18), 0, Ui.dp(ctx, 16), 0);
        t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),
                new android.graphics.drawable.ColorDrawable(Theme.surface), null));
        return t;
    }
}
