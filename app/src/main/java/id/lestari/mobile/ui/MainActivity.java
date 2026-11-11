package id.lestari.mobile.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import id.lestari.mobile.LestariApp;
import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.ApiException;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.FileRef;
import id.lestari.mobile.core.Host;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;
import id.lestari.mobile.defs.Defs;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single activity: a toolbar over a stack of {@link Screen}s. Also the {@link Host} that page
 * definitions use to show forms, tables, confirmations, open links and save downloads.
 */
public final class MainActivity extends Activity implements Host {
    private final List<Screen> stack = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout bar;
    private Toolbar toolbar;
    private FrameLayout content;
    private Registry registry;
    private final Map<Integer, FilePicked> pickers = new HashMap<>();
    private int nextRequest = 100;
    private final OnBackInvokedCallback backCallback = this::back;
    private boolean backRegistered;
    private int unread;
    private final Runnable badgeLoop = new Runnable() {
        @Override
        public void run() {
            refreshBadge();
            LestariApp.get().main().postDelayed(this, 60000);
        }
    };

    public interface FilePicked {
        void picked(FileRef file);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        registry = Defs.get();
        JSONObject display = parse(api().store().get(Api.K_THEME));
        Theme.apply(Json.str(display, "color_theme", "emerald"));
        buildChrome();
        api().setListener(() -> LestariApp.get().main().post(() -> {
            toast(I18n.t("Session expired, please sign in again."));
            showLogin();
        }));
        if (api().signedIn()) showHome(true);
        else showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (api().signedIn()) LestariApp.get().main().postDelayed(badgeLoop, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LestariApp.get().main().removeCallbacks(badgeLoop);
    }

    private void buildChrome() {
        root = Ui.vbox(this);
        root.setBackgroundColor(Theme.background);
        bar = Ui.vbox(this);
        bar.setBackgroundColor(Theme.primary);
        toolbar = new Toolbar(new ContextThemeWrapper(this, android.R.style.ThemeOverlay_Material_Dark_ActionBar));
        toolbar.setPopupTheme(Theme.dark ? android.R.style.ThemeOverlay_Material_Dark : android.R.style.ThemeOverlay_Material_Light);
        toolbar.setTitleTextColor(Theme.onPrimary);
        toolbar.setOverflowIcon(Icons.of(Icons.Shape.MORE, Theme.onPrimary, Ui.dp(this, 24)));
        bar.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));
        root.addView(bar, Ui.match());
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        setActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> back());
        edgeToEdge();
    }

    /**
     * Android 15+ always draws edge-to-edge: pad the toolbar below the status bar / cutout and the content above
     * the navigation bar or the keyboard, whichever is taller.
     */
    private void edgeToEdge() {
        recolor();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            bar.setPadding(sys.left, sys.top, sys.right, 0);
            content.setPadding(sys.left, 0, sys.right, Math.max(sys.bottom, ime.bottom));
            return insets;
        });
    }

    // ------------------------------------------------------------------ navigation

    public Registry registry() {
        return registry;
    }

    public void showLogin() {
        clearStack();
        push(new LoginScreen(this));
    }

    /** Menu as the root screen, optionally with the dashboard on top. */
    public void showHome(boolean openDashboard) {
        recolor();
        clearStack();
        push(new MenuScreen(this));
        if (openDashboard) open("dashboard");
        refreshUser();
        LestariApp.get().main().removeCallbacks(badgeLoop);
        LestariApp.get().main().postDelayed(badgeLoop, 500);
    }

    /** Apply the current palette to the activity chrome and the system bar icons. */
    private void recolor() {
        root.setBackgroundColor(Theme.background);
        bar.setBackgroundColor(Theme.primary);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            // status bar sits on the colored toolbar (light icons); navigation bar on the page background
            c.setSystemBarsAppearance(Theme.dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }
    }

    /** Rebuild every screen (after a theme or branch change) and reopen the current page. */
    private void restart() {
        String current = null;
        for (int i = stack.size() - 1; i >= 0 && current == null; i--) current = stack.get(i).pageId;
        showHome(false);
        if (current != null) open(current);
    }

    /** After signing in: follow the account's language, then open the home screen. */
    public void signedInWithLanguage(String lang) {
        if (LestariApp.get().setLanguage(lang)) registry = Defs.get();
        showHome(true);
    }

    /**
     * Switch language from the menu or the sign-in screen: rebuild the definitions and every screen, and (when
     * signed in) store the choice in the account's display settings so the web back office follows.
     */
    public void changeLanguage(String lang) {
        if (!LestariApp.get().setLanguage(lang)) return;
        registry = Defs.get();
        if (!api().signedIn()) {
            showLogin();
            return;
        }
        JSONObject display = parse(api().store().get(Api.K_THEME));
        Json.put(display, "language", lang);
        api().store().set(Api.K_THEME, display.toString());
        runQuietly(() -> api().put("/settings/display", Json.obj("language", lang)), r -> {}, null);
        restart();
    }

    private void clearStack() {
        for (Screen s : stack) {
            s.alive = false;
            s.onDestroy();
        }
        stack.clear();
    }

    public void push(Screen s) {
        stack.add(s);
        render();
    }

    /** Close the top screen. */
    public void pop() {
        if (stack.size() <= 1) return;
        Screen s = stack.remove(stack.size() - 1);
        s.alive = false;
        s.onDestroy();
        render();
    }

    private void back() {
        if (stack.isEmpty()) return;
        if (stack.get(stack.size() - 1).onBack()) return;
        if (stack.size() > 1) pop();
        else finish();
    }

    private void render() {
        Screen top = stack.get(stack.size() - 1);
        content.removeAllViews();
        View v = top.view();
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbar.setTitle(top.title());
        if (stack.size() > 1) {
            toolbar.setNavigationIcon(Icons.of(Icons.Shape.BACK, Theme.onPrimary, Ui.dp(this, 24)));
            toolbar.setNavigationContentDescription(I18n.t("Back"));
        } else {
            toolbar.setNavigationIcon(null);
        }
        invalidateOptionsMenu();
        updateBackCallback();
    }

    /** Predictive back: handle the gesture while a screen can be popped; at the root the system closes the app. */
    private void updateBackCallback() {
        boolean want = stack.size() > 1;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (want && !backRegistered) {
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            backRegistered = true;
        } else if (!want && backRegistered) {
            d.unregisterOnBackInvokedCallback(backCallback);
            backRegistered = false;
        }
    }

    public void setTitleText(String title) {
        toolbar.setTitle(title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (stack.isEmpty()) return true;
        List<Screen.ToolItem> items = stack.get(stack.size() - 1).menu();
        for (int i = 0; i < items.size(); i++) {
            Screen.ToolItem it = items.get(i);
            MenuItem m = menu.add(Menu.NONE, i, i, it.label);
            m.setShowAsAction(it.primary ? MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT : MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (stack.isEmpty()) return false;
        List<Screen.ToolItem> items = stack.get(stack.size() - 1).menu();
        int i = item.getItemId();
        if (i >= 0 && i < items.size()) {
            items.get(i).run.run();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Open a registry page (plugin-gated pages show a lock screen when the plugin is inactive). */
    public void open(String pageId) {
        Page p = registry.page(pageId);
        if (p == null) {
            toast(I18n.t("Unknown page") + ": " + pageId);
            return;
        }
        Screen top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (top != null && pageId.equals(top.pageId)) {
            top.reload();
            return;
        }
        Screen s;
        if (p.plugin != null && !hasPlugin(p.plugin)) s = new PluginGateScreen(this, p);
        else {
            switch (p.kind) {
                case CRUD:
                    s = new CrudScreen(this, p);
                    break;
                case REPORT:
                    s = new ReportScreen(this, p);
                    break;
                case SETTINGS:
                    s = new SettingsScreen(this, p);
                    break;
                default:
                    s = custom(p);
            }
        }
        s.pageId = p.id;
        push(s);
    }

    private Screen custom(Page p) {
        switch (p.id) {
            case "dashboard":
                return new DashboardScreen(this, p);
            case "missions":
                return new MissionsScreen(this, p);
            case "reset-data":
                return new ResetDataScreen(this, p);
            case "plugins":
                return new PluginsScreen(this, p);
            case "integrations":
                return CardListScreen.integrations(this, p);
            case "loans":
                return CardListScreen.loans(this, p);
            default:
                return new PluginGateScreen(this, p);
        }
    }

    public boolean hasPlugin(String code) {
        for (Object x : Json.values(user().opt("active_plugins"))) if (code.equals(x)) return true;
        return false;
    }

    public int unread() {
        return unread;
    }

    // ------------------------------------------------------------------ Host

    @Override
    public Api api() {
        return LestariApp.get().api();
    }

    @Override
    public JSONObject user() {
        return parse(api().store().get(Api.K_USER));
    }

    private static JSONObject parse(String s) {
        if (s == null || s.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public void toast(String message) {
        onMain(() -> Toast.makeText(this, I18n.t(message), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void error(Throwable e) {
        if (e instanceof ApiException && ((ApiException) e).status == 401 && !api().signedIn()) return; // sign-in screen follows
        String msg = e.getMessage() == null ? I18n.t("Something went wrong") : e.getMessage();
        onMain(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    public void confirm(String title, String message, String okText, boolean danger, String typeToConfirm, Runnable onOk) {
        onMain(() -> Dialogs.confirm(this, I18n.t(title), I18n.t(message), I18n.t(okText), danger, typeToConfirm, onOk));
    }

    @Override
    public void form(FormSpec spec) {
        spec.title = I18n.t(spec.title);
        spec.submitText = I18n.t(spec.submitText);
        spec.intro = I18n.t(spec.intro);
        spec.linkLabel = I18n.t(spec.linkLabel);
        onMain(() -> push(new FormScreen(this, spec)));
    }

    @Override
    public void table(String title, String path, Map<String, Object> query, List<Field> columns, String emptyText) {
        onMain(() -> push(new TableScreen(this, I18n.t(title), path, query, columns, I18n.t(emptyText))));
    }

    @Override
    public void message(String title, String text) {
        onMain(() -> {
            TextView t = Ui.body(this, I18n.t(text));
            t.setTextIsSelectable(true);
            int p = Ui.dp(this, 22);
            t.setPadding(p, Ui.dp(this, 8), p, 0);
            Dialogs.alert(this).setTitle(I18n.t(title)).setView(t).setPositiveButton(I18n.t("Close"), null)
                    .setNeutralButton(I18n.t("Copy"), (d, w) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(title, text));
                        toast(I18n.t("Copied"));
                    }).show();
        });
    }

    @Override
    public void openUrl(String url) {
        if (url == null || url.isEmpty() || "null".equals(url)) return;
        onMain(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                toast(I18n.t("No app can open {item}", "item", url));
            }
        });
    }

    /** Saves to Downloads (MediaStore, no storage permission needed) and offers to open the file. */
    @Override
    public void saveFile(Api.Download file, String fallbackName) {
        String name = file.filename != null && !file.filename.isEmpty() ? file.filename : fallbackName;
        try {
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IllegalStateException(I18n.t("Downloads is not available"));
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException(I18n.t("Cannot write the file"));
                os.write(file.bytes);
            }
            cv.clear();
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, cv, null, null);
            onMain(() -> {
                Toast.makeText(this, I18n.t("Saved to Downloads") + ": " + name, Toast.LENGTH_SHORT).show();
                Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(view, I18n.t("Open {item}", "item", name)));
                } catch (ActivityNotFoundException ignored) {
                    // saved; nothing installed to open it
                }
            });
        } catch (Exception e) {
            error(e);
        }
    }

    @Override
    public void go(String pageId) {
        if (pageId == null || pageId.isEmpty() || "null".equals(pageId)) return;
        onMain(() -> open(pageId));
    }

    @Override
    public void switchBranch(String branchId) {
        api().store().set(Api.K_BRANCH, branchId);
        Lookup.clearCache();
        toast(I18n.t("Switched branch"));
        run(() -> api().get("/auth/me").optJSONObject("data"), me -> {
            if (me != null) api().store().set(Api.K_USER, me.toString());
            restart();
        });
    }

    @Override
    public void refreshUser() {
        runQuietly(() -> api().get("/auth/me").optJSONObject("data"), me -> {
            if (me == null) return;
            api().store().set(Api.K_USER, me.toString());
            for (Screen s : stack) if (s instanceof MenuScreen) ((MenuScreen) s).refreshHeader();
        }, null);
    }

    @Override
    public void refreshBadge() {
        if (!api().signedIn()) return;
        runQuietly(() -> api().get("/notifications/unread-count").optJSONObject("data"), d -> {
            if (d == null) return;
            unread = d.optInt("count");
            for (Screen s : stack) if (s instanceof MenuScreen) ((MenuScreen) s).refreshHeader();
        }, null);
    }

    @Override
    public void applyDisplay(JSONObject display) {
        if (display == null) return;
        api().store().set(Api.K_THEME, display.toString());
        onMain(() -> {
            Theme.apply(Json.str(display, "color_theme", "emerald"));
            if (LestariApp.get().setLanguage(Json.str(display, "language"))) registry = Defs.get();
            restart();
        });
    }

    @Override
    public <T> void run(Work<T> work, Done<T> done) {
        runQuietly(work, done, this::error);
    }

    /** Background work → UI-thread callback; {@code err} may be null to ignore failures. */
    public <T> void runQuietly(Work<T> work, Done<T> done, Done<Throwable> err) {
        LestariApp app = LestariApp.get();
        app.pool().execute(() -> {
            try {
                T r = work.call();
                app.main().post(() -> done.done(r));
            } catch (Exception e) {
                if (err != null) app.main().post(() -> err.done(e));
            }
        });
    }

    private void onMain(Runnable r) {
        if (Thread.currentThread() == getMainLooper().getThread()) r.run();
        else LestariApp.get().main().post(r);
    }

    // ------------------------------------------------------------------ files

    /**
     * Let the user pick a file; the bytes are read in the background. Images use the system photo picker,
     * anything else (PDF, spreadsheets, or image-or-PDF) the document picker. Neither needs a storage permission.
     */
    public void pickFile(String[] mimeTypes, FilePicked cb) {
        Intent i;
        if (mimeTypes.length == 1 && "image/*".equals(mimeTypes[0])) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
            if (mimeTypes.length == 1) i.setType(mimeTypes[0]);
            else {
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        }
        int code = nextRequest++;
        pickers.put(code, cb);
        try {
            startActivityForResult(i, code);
        } catch (ActivityNotFoundException e) {
            pickers.remove(code);
            toast(I18n.t("No file picker available"));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        FilePicked cb = pickers.remove(requestCode);
        if (cb == null || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        run(() -> readFile(uri), cb::picked);
    }

    private FileRef readFile(Uri uri) throws Exception {
        ContentResolver cr = getContentResolver();
        String name = "file";
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
                if (!c.isNull(1) && c.getLong(1) > 20L * 1024 * 1024) throw new IllegalArgumentException(I18n.t("The file is larger than 20 MB."));
            }
        }
        String mime = cr.getType(uri);
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IllegalStateException(I18n.t("Cannot read the file"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new FileRef(name, mime, bos.toByteArray());
        }
    }
}
