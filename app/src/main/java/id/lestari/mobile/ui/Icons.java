package id.lestari.mobile.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Tiny stroke icons drawn in code (no image resources needed). */
public final class Icons {
    private Icons() {}

    public enum Shape { BACK, MORE, MENU }

    public static Drawable of(Shape shape, int color, int sizePx) {
        return new IconDrawable(shape, color, sizePx);
    }

    private static final class IconDrawable extends Drawable {
        private final Shape shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        IconDrawable(Shape shape, int color, int size) {
            this.shape = shape;
            this.size = size;
            paint.setColor(color);
            paint.setStrokeWidth(size / 12f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
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
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x = b.left + (b.width() - s) / 2f;
            float y = b.top + (b.height() - s) / 2f;
            switch (shape) {
                case BACK: {
                    Path p = new Path();
                    p.moveTo(x + s * 0.55f, y + s * 0.25f);
                    p.lineTo(x + s * 0.30f, y + s * 0.50f);
                    p.lineTo(x + s * 0.55f, y + s * 0.75f);
                    canvas.drawPath(p, paint);
                    canvas.drawLine(x + s * 0.30f, y + s * 0.50f, x + s * 0.78f, y + s * 0.50f, paint);
                    break;
                }
                case MENU:
                    for (int i = 0; i < 3; i++) {
                        float yy = y + s * (0.30f + i * 0.20f);
                        canvas.drawLine(x + s * 0.22f, yy, x + s * 0.78f, yy, paint);
                    }
                    break;
                default: {
                    Paint fill = new Paint(paint);
                    fill.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 3; i++) canvas.drawCircle(x + s * 0.5f, y + s * (0.28f + i * 0.22f), s * 0.06f, fill);
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
