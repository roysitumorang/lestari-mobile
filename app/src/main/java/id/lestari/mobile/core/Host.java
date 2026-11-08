package id.lestari.mobile.core;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * What page definitions may ask of the user interface. Implemented by the Android layer;
 * keeping it abstract lets the definitions (and the API client) stay plain Java.
 *
 * <p>All methods may be called from any thread; implementations post to the UI thread.
 */
public interface Host {
    Api api();

    /** The signed-in user (GET /auth/me), or an empty object. */
    JSONObject user();

    void toast(String message);

    void error(Throwable e);

    /** Ask for confirmation; {@code typeToConfirm} (optional) must be typed before OK is enabled. */
    void confirm(String title, String message, String okText, boolean danger, String typeToConfirm, Runnable onOk);

    /** Open a form screen. {@link FormSpec#onSubmit} runs on a background thread. */
    void form(FormSpec spec);

    /** A read-only table loaded from {@code path} (movement history, activity logs…). */
    void table(String title, String path, Map<String, Object> query, List<Field> columns, String emptyText);

    /** A dialog with selectable text (recovery codes, keys). */
    void message(String title, String text);

    void openUrl(String url);

    /** Save a downloaded file (to Downloads) and offer to open it. May be called from a background thread. */
    void saveFile(Api.Download file, String fallbackName);

    /** Navigate to a page id (the same ids as the web client's routes). */
    void go(String pageId);

    void switchBranch(String branchId);

    void refreshUser();

    void refreshBadge();

    void applyDisplay(JSONObject display);

    /** Run {@code work} on a background thread, then {@code done} on the UI thread (errors are shown as toasts). */
    <T> void run(Work<T> work, Done<T> done);

    interface Work<T> {
        T call() throws Exception;
    }

    interface Done<T> {
        void done(T result);
    }

    /** A form to show. */
    final class FormSpec {
        public String title;
        public String submitText = "Save";
        public List<Field> fields;
        public JSONObject values = new JSONObject();
        /** Optional text shown above the fields. */
        public String intro;
        /** Optional link button shown under the intro (label, url). */
        public String linkLabel;
        public String linkUrl;
        /** Runs on a background thread with the form values; throw ApiException to show field errors. */
        public Submit onSubmit;
        /** Runs on the UI thread after a successful submit (the form is already closed). */
        public Runnable onDone;

        public interface Submit {
            void submit(JSONObject values) throws Exception;
        }
    }
}
