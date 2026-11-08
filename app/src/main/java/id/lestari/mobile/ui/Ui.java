package id.lestari.mobile.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Json;

import org.json.JSONObject;

import java.util.List;

/** View factory helpers — every screen is built in code with platform widgets. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, Context c, int l, int t, int r, int b) {
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return lp;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.addView(child, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static HorizontalScrollView hscroll(Context c, View child) {
        HorizontalScrollView s = new HorizontalScrollView(c);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(child);
        return s;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        return text(c, s, 18, Theme.text, true);
    }

    public static TextView body(Context c, CharSequence s) {
        return text(c, s, 14, Theme.text, false);
    }

    public static TextView muted(Context c, CharSequence s) {
        return text(c, s, 13, Theme.muted, false);
    }

    public static GradientDrawable rounded(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** A white rounded card with a border and padding. */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(rounded(Theme.surface, Theme.border, 10, c));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams cardParams(Context c) {
        return margins(match(), c, 12, 6, 12, 6);
    }

    private static Drawable pressable(Drawable base, Context c) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), base, null);
    }

    public enum Style { PRIMARY, OUTLINE, DANGER, TEXT }

    public static Button button(Context c, String label, Style style, View.OnClickListener onClick) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 40));
        b.setMinimumHeight(dp(c, 40));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        switch (style) {
            case PRIMARY:
                b.setTextColor(Theme.onPrimary);
                b.setBackground(pressable(rounded(Theme.primary, 0, 8, c), c));
                break;
            case DANGER:
                b.setTextColor(Theme.onPrimary);
                b.setBackground(pressable(rounded(Theme.danger, 0, 8, c), c));
                break;
            case OUTLINE:
                b.setTextColor(Theme.primary);
                b.setBackground(pressable(rounded(Theme.surface, Theme.primary, 8, c), c));
                break;
            default:
                b.setTextColor(Theme.primary);
                b.setBackground(pressable(new ColorDrawable(0x00000000), c));
        }
        if (onClick != null) b.setOnClickListener(onClick);
        return b;
    }

    /** A small colored pill for statuses. */
    public static TextView badge(Context c, String value) {
        TextView t = text(c, Format.humanize(value), 12, Theme.statusColor(value), true);
        int col = Theme.statusColor(value);
        t.setBackground(rounded((col & 0x00FFFFFF) | 0x22000000, 0, 10, c));
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return t;
    }

    public static View spinner(Context c) {
        LinearLayout l = vbox(c);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(c, 32), 0, dp(c, 32));
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(Theme.primary));
        l.addView(p, wrap());
        return l;
    }

    public static View empty(Context c, String message) {
        LinearLayout l = vbox(c);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 24), dp(c, 40), dp(c, 24), dp(c, 40));
        TextView t = text(c, message, 15, Theme.muted, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t, wrap());
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.border);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2 + 1)));
        return v;
    }

    /** "Label   value" line used by row cards and detail views. */
    public static View pair(Context c, String label, CharSequence value) {
        LinearLayout row = hbox(c);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(c, 3), 0, dp(c, 3));
        TextView l = muted(c, label);
        TextView v = body(c, value);
        v.setGravity(Gravity.END);
        v.setTextIsSelectable(true);
        row.addView(l, margins(weight(1), c, 0, 0, 12, 0));
        row.addView(v, weight(1.4f));
        return row;
    }

    /** A thumbnail image loaded from a URL. */
    public static ImageView thumb(Context c, String url, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(rounded(Theme.chipBg, 0, 6, c));
        iv.setClipToOutline(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        ImageLoader.load(iv, url, dp(c, sizeDp));
        return iv;
    }

    /** Page heading with optional description (shown at the top of list/report/settings screens). */
    public static View header(Context c, String description) {
        if (TextUtils.isEmpty(description)) return null;
        TextView t = muted(c, description);
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 4));
        return t;
    }

    /** Renders one row as a card: image + title + badge + "label: value" lines. */
    public static LinearLayout rowCard(Context c, List<Field> cols, JSONObject row, String titleKey) {
        LinearLayout card = card(c);
        LinearLayout top = hbox(c);
        Field image = null;
        Field titleCol = null;
        Field badgeCol = null;
        for (Field f : cols) {
            String fmt = Format.effectiveFormat(f);
            if ("image".equals(fmt) && image == null) image = f;
            else if ("badge".equals(fmt) && badgeCol == null && "status".equals(f.key)) badgeCol = f;
        }
        for (Field f : cols) if (f.key != null && f.key.equals(titleKey)) titleCol = f;
        if (titleCol == null) {
            for (Field f : cols) {
                String fmt = Format.effectiveFormat(f);
                if (f != image && f != badgeCol && ("text".equals(fmt))) {
                    titleCol = f;
                    break;
                }
            }
        }
        if (titleCol == null) {
            for (Field f : cols) if (f != image && f != badgeCol) { titleCol = f; break; }
        }
        if (image != null) {
            View iv = thumb(c, Json.str(row, image.key), 44);
            ((LinearLayout.LayoutParams) iv.getLayoutParams()).setMargins(0, 0, dp(c, 12), 0);
            top.addView(iv);
        }
        String title = titleCol == null ? "" : Format.cell(titleCol, row);
        TextView tv = text(c, title, 16, Theme.text, true);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(tv, weight(1));
        if (badgeCol != null) {
            String s = Json.str(row, badgeCol.key);
            if (!s.isEmpty()) top.addView(badge(c, s), wrap());
        }
        card.addView(top, match());
        boolean any = false;
        for (Field f : cols) {
            if (f == image || f == titleCol || f == badgeCol || f.key == null) continue;
            String v = "badge".equals(Format.effectiveFormat(f)) ? Format.cell(f, row) : Format.cell(f, row);
            if (!any) {
                LinearLayout.LayoutParams lp = match();
                lp.topMargin = dp(c, 6);
                card.addView(divider(c), lp);
                any = true;
            }
            card.addView(pair(c, f.label == null || f.label.isEmpty() ? f.key : f.label, v), match());
        }
        return card;
    }
}
