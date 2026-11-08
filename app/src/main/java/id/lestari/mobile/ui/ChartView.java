package id.lestari.mobile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import id.lestari.mobile.core.Format;

import java.util.ArrayList;
import java.util.List;

/** Line / bar chart drawn on a Canvas (multiple series, axes, legend, tap for values). */
public final class ChartView extends View {
    public static final class Series {
        final String name;
        final double[] values;

        public Series(String name, double[] values) {
            this.name = name;
            this.values = values;
        }
    }

    private final List<String> labels = new ArrayList<>();
    private final List<Series> series = new ArrayList<>();
    private boolean bar;
    private String format = "number";
    private int selected = -1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public ChartView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
    }

    public void setData(List<String> labels, List<Series> series, boolean bar, String format) {
        this.labels.clear();
        this.labels.addAll(labels);
        this.series.clear();
        this.series.addAll(series);
        this.bar = bar;
        this.format = format == null ? "number" : format;
        selected = -1;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int legend = series.size() > 1 ? (int) (22 * d) : 0;
        setMeasuredDimension(w, (int) (220 * d) + legend);
    }

    private static double niceMax(double v) {
        if (v <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        double n = v / p;
        return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float W = getWidth();
        float top = 8 * d;
        if (series.size() > 1) {
            float x = 4 * d;
            paint.setTextSize(12 * d);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < series.size(); i++) {
                paint.setColor(Theme.CHART[i % 4]);
                canvas.drawCircle(x + 5 * d, 11 * d, 5 * d, paint);
                paint.setColor(Theme.text);
                canvas.drawText(series.get(i).name, x + 14 * d, 15 * d, paint);
                x += paint.measureText(series.get(i).name) + 30 * d;
            }
            top += 22 * d;
        }
        if (labels.isEmpty()) {
            paint.setColor(Theme.muted);
            paint.setTextSize(14 * d);
            canvas.drawText("No data for this period", 8 * d, top + 40 * d, paint);
            return;
        }
        float L = 44 * d, R = 8 * d, B = 24 * d;
        float H = getHeight();
        float iw = W - L - R, ih = H - top - B;
        double max = 0, min = 0;
        for (Series s : series) for (double v : s.values) { max = Math.max(max, v); min = Math.min(min, v); }
        max = niceMax(max);
        double span = (max - min) == 0 ? 1 : (max - min);
        int n = Math.max(labels.size(), 1);

        paint.setTextSize(10 * d);
        for (int k = 0; k <= 4; k++) {
            double v = min + span * k / 4;
            float y = (float) (top + ih - ((v - min) / span) * ih);
            paint.setColor(Theme.border);
            paint.setStrokeWidth(1 * d);
            canvas.drawLine(L, y, W - R, y, paint);
            paint.setColor(Theme.muted);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(Format.compact(v), L - 4 * d, y + 3 * d, paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        int step = (int) Math.ceil(n / 6.0);
        for (int i = 0; i < n; i += step) {
            canvas.drawText(shorten(labels.get(i)), x(i, n, L, iw), H - 8 * d, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);

        for (int si = 0; si < series.size(); si++) {
            Series s = series.get(si);
            paint.setColor(Theme.CHART[si % 4]);
            if (bar) {
                paint.setStyle(Paint.Style.FILL);
                float bw = Math.max(2 * d, (iw / n) * 0.7f / series.size());
                for (int i = 0; i < s.values.length && i < n; i++) {
                    float cx = x(i, n, L, iw);
                    float bx = cx - (bw * series.size()) / 2f + si * bw;
                    float yTop = (float) (top + ih - ((Math.max(0, s.values[i]) - min) / span) * ih);
                    float yBot = (float) (top + ih - ((Math.min(0, s.values[i]) - min) / span) * ih);
                    canvas.drawRoundRect(new RectF(bx, yTop, bx + bw, yBot), 2 * d, 2 * d, paint);
                }
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                Path p = new Path();
                for (int i = 0; i < s.values.length && i < n; i++) {
                    float xx = x(i, n, L, iw);
                    float yy = (float) (top + ih - ((s.values[i] - min) / span) * ih);
                    if (i == 0) p.moveTo(xx, yy);
                    else p.lineTo(xx, yy);
                }
                canvas.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < s.values.length && i < n; i++) {
                    canvas.drawCircle(x(i, n, L, iw), (float) (top + ih - ((s.values[i] - min) / span) * ih), 2.5f * d, paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        if (selected >= 0 && selected < n) drawTip(canvas, selected, x(selected, n, L, iw), top, W);
    }

    private void drawTip(Canvas canvas, int i, float x, float top, float W) {
        paint.setColor(Theme.muted);
        paint.setStrokeWidth(1 * d);
        canvas.drawLine(x, top, x, getHeight() - 24 * d, paint);
        List<String> lines = new ArrayList<>();
        lines.add(labels.get(i));
        for (Series s : series) lines.add(s.name + ": " + Format.by(format, i < s.values.length ? s.values[i] : 0));
        paint.setTextSize(12 * d);
        float w = 0;
        for (String l : lines) w = Math.max(w, paint.measureText(l));
        float bw = w + 16 * d, bh = lines.size() * 16 * d + 8 * d;
        float bx = Math.min(Math.max(x + 8 * d, 0), W - bw);
        paint.setColor(Theme.dark ? 0xEE2A322F : 0xEE1F2A26);
        canvas.drawRoundRect(new RectF(bx, top, bx + bw, top + bh), 6 * d, 6 * d, paint);
        paint.setColor(0xFFFFFFFF);
        for (int k = 0; k < lines.size(); k++) canvas.drawText(lines.get(k), bx + 8 * d, top + 18 * d + k * 16 * d, paint);
    }

    private float x(int i, int n, float L, float iw) {
        if (bar) return L + (i + 0.5f) * iw / n;
        return n == 1 ? L + iw / 2 : L + i * iw / (n - 1);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 10 ? s.substring(0, 9) + "…" : s;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (labels.isEmpty()) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float L = 44 * d, R = 8 * d;
            float iw = getWidth() - L - R;
            int n = labels.size();
            int idx = bar ? (int) ((e.getX() - L) / (iw / n)) : Math.round((e.getX() - L) / (n == 1 ? 1 : iw / (n - 1)));
            selected = Math.max(0, Math.min(n - 1, idx));
            invalidate();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
