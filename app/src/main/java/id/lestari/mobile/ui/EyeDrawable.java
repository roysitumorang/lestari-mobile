package id.lestari.mobile.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** The icon of a password toggle: an eye, struck through while the password is hidden. Drawn in code. */
final class EyeDrawable extends Drawable {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path lids = new Path();
    private final int size;
    private boolean struck = true;

    EyeDrawable(int color, int sizePx, float strokePx) {
        size = sizePx;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    /** true draws the line through the eye (password hidden). */
    void setStruck(boolean value) {
        if (struck != value) {
            struck = value;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float w = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float half = w * 0.42f, lift = w * 0.30f;
        lids.rewind();
        lids.moveTo(cx - half, cy);
        lids.quadTo(cx, cy - lift * 2, cx + half, cy);
        lids.quadTo(cx, cy + lift * 2, cx - half, cy);
        lids.close();
        canvas.drawPath(lids, stroke);
        canvas.drawCircle(cx, cy, w * 0.12f, fill);
        if (struck) {
            float d = w * 0.36f;
            canvas.drawLine(cx - d, cy + d, cx + d, cy - d, stroke);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        stroke.setColorFilter(filter);
        fill.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
