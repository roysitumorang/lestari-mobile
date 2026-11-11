package id.lestari.mobile.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Display formatting for table cells, detail views and summary cards (Indonesian locale, IDR). */
public final class Format {
    private Format() {}

    public static final Locale LOCALE = new Locale("id", "ID");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", LOCALE);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH.mm", LOCALE);

    public static String money(Object v) {
        if (blank(v)) return "-";
        NumberFormat f = NumberFormat.getCurrencyInstance(LOCALE);
        f.setMaximumFractionDigits(0);
        f.setMinimumFractionDigits(0);
        return f.format(Json.num(v)).replace(" ", " ");
    }

    public static String number(Object v) {
        if (blank(v)) return "-";
        NumberFormat f = NumberFormat.getNumberInstance(LOCALE);
        f.setMaximumFractionDigits(2);
        return f.format(Json.num(v));
    }

    public static String percent(Object v) {
        return blank(v) ? "-" : number(v) + "%";
    }

    public static String date(Object v) {
        if (blank(v)) return "-";
        String s = String.valueOf(v);
        try {
            if (s.length() > 10) return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(DATE);
            return LocalDate.parse(s).format(DATE);
        } catch (Exception e) {
            return s;
        }
    }

    public static String datetime(Object v) {
        if (blank(v)) return "-";
        String s = String.valueOf(v);
        try {
            if (s.length() <= 10) return LocalDate.parse(s).format(DATE);
            return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).format(DATETIME);
        } catch (Exception e) {
            return s;
        }
    }

    public static String text(Object v) {
        if (blank(v)) return "-";
        if (v instanceof Double && ((Double) v) == Math.rint((Double) v)) return String.valueOf(((Double) v).longValue());
        return String.valueOf(v);
    }

    public static String humanize(String s) {
        if (s == null || s.isEmpty()) return "-";
        String t = s.replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Compact numbers for chart axes: 1.2K, 3.4M, 5B. */
    public static String compact(double n) {
        double a = Math.abs(n);
        if (a >= 1e9) return trim(n / 1e9) + "B";
        if (a >= 1e6) return trim(n / 1e6) + "M";
        if (a >= 1e3) return trim(n / 1e3) + "K";
        return trim(Math.round(n * 100) / 100.0);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static boolean blank(Object v) {
        return v == null || v == JSONObject.NULL || (v instanceof String && ((String) v).isEmpty());
    }

    /** Formats one value by a format name (money, number, percent, date, datetime, boolean, badge, list, text). */
    public static String by(String format, Object v) {
        if (format == null) format = "text";
        switch (format) {
            case "money":
                return money(v);
            case "number":
                return number(v);
            case "percent":
                return percent(v);
            case "date":
                return date(v);
            case "datetime":
                return datetime(v);
            case "boolean":
                return I18n.t(Json.truthy(v) ? "Yes" : "No");
            case "badge":
                return blank(v) ? "-" : badge(String.valueOf(v));
            case "list":
                return list(v);
            default:
                return text(v);
        }
    }

    /** A status/type value: its translation when the dictionary has one, otherwise humanized ("in_transit" → "In transit"). */
    public static String badge(String raw) {
        String tr = I18n.t(raw);
        return tr.equals(raw) ? humanize(raw) : tr;
    }

    private static String list(Object v) {
        if (!(v instanceof JSONArray)) return blank(v) ? "-" : String.valueOf(v);
        StringBuilder sb = new StringBuilder();
        for (Object x : Json.values(v)) {
            if (sb.length() > 0) sb.append(", ");
            if (x instanceof JSONObject) {
                JSONObject o = (JSONObject) x;
                sb.append(Json.str(o, "name", Json.str(o, "label", Json.str(o, "id"))));
            } else sb.append(x);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** Formats a column/field value of a row the way the web client does (render → options → format → type). */
    public static String cell(Field f, JSONObject row) {
        Object v = Json.get(row, f.key);
        if (f.render != null) return f.render.render(v, row);
        if (f.options != null && ("select".equals(f.type) || "multiselect".equals(f.type))) {
            if ("multiselect".equals(f.type) && v instanceof JSONArray) {
                StringBuilder sb = new StringBuilder();
                for (Object x : Json.values(v)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(optionLabel(f.options, x));
                }
                return sb.length() == 0 ? "-" : sb.toString();
            }
            if (blank(v)) return "-";
            return optionLabel(f.options, v);
        }
        return by(effectiveFormat(f), v);
    }

    public static String effectiveFormat(Field f) {
        if (f.format != null) return f.format;
        switch (f.type == null ? "" : f.type) {
            case "money":
                return "money";
            case "number":
                return "number";
            case "percent":
                return "percent";
            case "date":
                return "date";
            case "datetime":
                return "datetime";
            case "switch":
                return "boolean";
            case "multiselect":
                return "list";
            case "image":
                return "image";
            default:
                return "text";
        }
    }

    public static String optionLabel(List<Opt> opts, Object v) {
        for (Opt o : opts) if (String.valueOf(o.value).equals(String.valueOf(v))) return o.label;
        return text(v);
    }
}
