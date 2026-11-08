package id.lestari.mobile.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import id.lestari.mobile.LestariApp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal image loader: background download, downsampling and an in-memory LRU cache. */
public final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public static void load(final ImageView view, final String url, final int targetPx) {
        view.setTag(url);
        view.setImageDrawable(null);
        if (url == null || url.isEmpty() || "null".equals(url)) return;
        Bitmap hit = CACHE.get(url + "@" + targetPx);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        LestariApp.get().pool().execute(() -> {
            final Bitmap bmp = fetch(url, targetPx);
            if (bmp == null) return;
            CACHE.put(url + "@" + targetPx, bmp);
            LestariApp.get().main().post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    private static Bitmap fetch(String url, int targetPx) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) return null;
            byte[] data;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                data = bos.toByteArray();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            int target = Math.max(targetPx, 64);
            while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, d);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
