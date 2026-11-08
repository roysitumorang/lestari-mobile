package id.lestari.mobile.ui;

import android.graphics.Color;

/** Color palette (mirrors the web client's themes: emerald, ivory, charcoal). */
public final class Theme {
    private Theme() {}

    public static int primary;
    public static int primaryDark;
    public static int onPrimary;
    public static int background;
    public static int surface;
    public static int text;
    public static int muted;
    public static int border;
    public static int danger;
    public static int success;
    public static int warning;
    public static int info;
    public static int chipBg;
    public static boolean dark;
    public static final int[] CHART = new int[4];

    static {
        apply("emerald");
    }

    public static void apply(String name) {
        primary = Color.parseColor("#0F8A68");
        primaryDark = Color.parseColor("#0B6B51");
        onPrimary = Color.WHITE;
        danger = Color.parseColor("#C0392B");
        success = Color.parseColor("#198754");
        warning = Color.parseColor("#B7791F");
        info = Color.parseColor("#2B6CB0");
        CHART[1] = Color.parseColor("#E0A526");
        CHART[2] = Color.parseColor("#3A6EA5");
        CHART[3] = Color.parseColor("#C2185B");
        if ("charcoal".equals(name)) {
            dark = true;
            background = Color.parseColor("#181D1B");
            surface = Color.parseColor("#20262A");
            text = Color.parseColor("#E8EEEB");
            muted = Color.parseColor("#94A39D");
            border = Color.parseColor("#2A322F");
            chipBg = Color.parseColor("#2A322F");
            primary = Color.parseColor("#1F9E79");
            CHART[0] = Color.parseColor("#3CC49B");
        } else {
            dark = false;
            background = "ivory".equals(name) ? Color.parseColor("#FAF8F3") : Color.parseColor("#F4F6F5");
            surface = Color.WHITE;
            text = Color.parseColor("#1F2A26");
            muted = Color.parseColor("#6B7A75");
            border = Color.parseColor("#E3E8E6");
            chipBg = Color.parseColor("#EEF2F0");
            CHART[0] = primary;
        }
    }

    /** Badge colors by status word. */
    public static int statusColor(String s) {
        if (s == null) return muted;
        switch (s) {
            case "paid":
            case "active":
            case "success":
            case "completed":
            case "approved":
            case "accepted":
            case "delivered":
            case "present":
            case "in":
            case "income":
                return success;
            case "pending":
            case "unpaid":
            case "trial":
            case "processing":
            case "awaiting_confirmation":
            case "queued":
            case "late":
            case "invited":
                return warning;
            case "cancelled":
            case "rejected":
            case "failed":
            case "expired":
            case "payment_rejected":
            case "disabled":
            case "expense":
            case "out":
                return danger;
            default:
                return info;
        }
    }
}
