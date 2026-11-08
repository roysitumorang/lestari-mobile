package id.lestari.mobile.ui;

import android.content.Context;
import android.view.View;

import id.lestari.mobile.LestariApp;
import id.lestari.mobile.core.Host;

import java.util.ArrayList;
import java.util.List;

/** One full-screen page on the navigation stack of {@link MainActivity}. Views are built lazily in code. */
public abstract class Screen {
    protected final MainActivity act;
    protected final Context ctx;
    private View view;
    boolean alive = true;
    /** Registry page id when the screen shows a page (used by navigation to avoid duplicates). */
    public String pageId;

    protected Screen(MainActivity act) {
        this.act = act;
        this.ctx = act;
    }

    public abstract String title();

    protected abstract View create();

    public final View view() {
        if (view == null) view = create();
        return view;
    }

    /** Toolbar items for this screen. */
    public List<ToolItem> menu() {
        return new ArrayList<>();
    }

    /** Return true to consume the back gesture (e.g. to leave selection mode). */
    public boolean onBack() {
        return false;
    }

    /** Reload data (pull to refresh, or after navigating back to the same page). */
    public void reload() {}

    public void onDestroy() {}

    public boolean isAlive() {
        return alive;
    }

    /** Background work whose result is delivered only while the screen is still open. */
    protected <T> void load(Host.Work<T> work, Host.Done<T> ok, Host.Done<Throwable> err) {
        LestariApp app = LestariApp.get();
        app.pool().execute(() -> {
            try {
                final T r = work.call();
                app.main().post(() -> {
                    if (alive) ok.done(r);
                });
            } catch (final Exception e) {
                app.main().post(() -> {
                    if (!alive) return;
                    if (err != null) err.done(e);
                    else act.error(e);
                });
            }
        });
    }

    protected void refreshMenu() {
        act.invalidateOptionsMenu();
    }

    /** A toolbar entry: primary items show as text buttons, the rest go to the overflow menu. */
    public static final class ToolItem {
        public final String label;
        public final boolean primary;
        public final Runnable run;

        public ToolItem(String label, boolean primary, Runnable run) {
            this.label = label;
            this.primary = primary;
            this.run = run;
        }
    }
}
