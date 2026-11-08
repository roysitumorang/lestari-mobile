package id.lestari.mobile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import id.lestari.mobile.core.Api;

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
