package id.lestari.mobile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.defs.Defs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide singletons: the API client (tokens in SharedPreferences), a worker pool and the main-thread handler. */
public final class LestariApp extends Application {
    private static LestariApp instance;

    private Api api;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        final SharedPreferences prefs = getSharedPreferences("lestari", Context.MODE_PRIVATE);
        api = new Api(new Api.Store() {
            @Override
            public String get(String key) {
                return prefs.getString(key, null);
            }

            @Override
            public void set(String key, String value) {
                if (value == null) prefs.edit().remove(key).apply();
                else prefs.edit().putString(key, value).apply();
            }
        });
        String lang = api.store().get(Api.K_LANG);
        if (!I18n.supported(lang)) {
            String sys = Locale.getDefault().getLanguage(); // Indonesian is "in" on older JDKs, "id" on newer ones
            lang = "in".equals(sys) || "id".equals(sys) ? I18n.ID : I18n.EN;
        }
        I18n.set(lang, I18n.ID.equals(lang) ? dictionary() : null);
    }

    /**
     * Switch the language of the whole app: persists the choice, loads the dictionary and drops the page
     * definitions so they are rebuilt with translated labels. Returns false when nothing changed.
     */
    public boolean setLanguage(String lang) {
        if (!I18n.supported(lang) || lang.equals(I18n.lang())) return false;
        api.store().set(Api.K_LANG, lang);
        I18n.set(lang, I18n.ID.equals(lang) ? dictionary() : null);
        Defs.reset();
        return true;
    }

    private Map<String, String> dictionary() {
        try (InputStream in = getAssets().open("i18n/id.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return I18n.parse(out.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static LestariApp get() {
        return instance;
    }

    public Api api() {
        return api;
    }

    public ExecutorService pool() {
        return pool;
    }

    public Handler main() {
        return main;
    }
}
