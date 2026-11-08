package id.lestari.mobile.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One field of a form, a table column or a detail row — the same schema as the web client.
 *
 * <p>type: text | textarea | number | money | percent | email | tel | password | date | datetime | time |
 * select | multiselect | switch | color | image | file | items | heading.
 * Columns usually only set {@code format}: money | number | percent | date | datetime | boolean | badge | list.
 */
public final class Field {
    public final String key;
    public final String label;
    public String type;
    public String format;
    public boolean required;
    public boolean readonly;
    /** false = hidden from forms */
    public boolean form = true;
    /** false = hidden from default list columns */
    public boolean list = true;
    /** false = send a password-type field as typed (PINs); other passwords are base64-encoded. */
    public boolean encode = true;
    public boolean sortable = true;
    public List<Opt> options;
    public Lookup lookup;
    public List<Field> fields;
    public int col = 6;
    public String showIfKey;
    public List<String> showIfIn;
    public String placeholder;
    public String help;
    public String accept;
    public Object def;
    public Double min;
    public Double max;
    public Render render;

    /** Custom cell rendering (e.g. star ratings). */
    public interface Render {
        String render(Object value, JSONObject row);
    }

    public Field(String key, String label, String type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }

    // ---- factories (static import F.*-style through these) ----

    public static Field text(String k, String l) { return new Field(k, l, "text"); }
    public static Field textarea(String k, String l) { return new Field(k, l, "textarea").w(12); }
    public static Field number(String k, String l) { return new Field(k, l, "number"); }
    public static Field money(String k, String l) { return new Field(k, l, "money"); }
    public static Field percent(String k, String l) { return new Field(k, l, "percent"); }
    public static Field email(String k, String l) { return new Field(k, l, "email"); }
    public static Field tel(String k, String l) { return new Field(k, l, "tel"); }
    public static Field password(String k, String l) { return new Field(k, l, "password"); }
    public static Field date(String k, String l) { return new Field(k, l, "date"); }
    public static Field datetime(String k, String l) { return new Field(k, l, "datetime"); }
    public static Field time(String k, String l) { return new Field(k, l, "time"); }
    public static Field color(String k, String l) { return new Field(k, l, "color"); }
    public static Field image(String k, String l) { return new Field(k, l, "image"); }
    public static Field file(String k, String l) { return new Field(k, l, "file"); }
    public static Field sw(String k, String l) { return new Field(k, l, "switch"); }

    public static Field select(String k, String l, List<Opt> options) {
        Field f = new Field(k, l, "select");
        f.options = options;
        return f;
    }

    public static Field lookup(String k, String l, Lookup lookup) {
        Field f = new Field(k, l, "select");
        f.lookup = lookup;
        return f;
    }

    public static Field multi(String k, String l, List<Opt> options) {
        Field f = new Field(k, l, "multiselect");
        f.options = options;
        return f;
    }

    public static Field multiLookup(String k, String l, Lookup lookup) {
        Field f = new Field(k, l, "multiselect");
        f.lookup = lookup;
        return f;
    }

    public static Field items(String k, String l, Field... sub) {
        Field f = new Field(k, l, "items");
        f.fields = new ArrayList<>(Arrays.asList(sub));
        return f.w(12);
    }

    public static Field heading(String l) {
        return new Field(null, l, "heading");
    }

    /** A plain table column / detail row (no input type). */
    public static Field col(String k, String l) {
        return new Field(k, l, null);
    }

    public static Field col(String k, String l, String format) {
        return new Field(k, l, null).fmt(format);
    }

    // ---- chainable options ----

    public Field req() { required = true; return this; }
    public Field ro() { readonly = true; return this; }
    public Field noForm() { form = false; return this; }
    public Field noList() { list = false; return this; }
    public Field noEncode() { encode = false; return this; }
    public Field noSort() { sortable = false; return this; }
    public Field def(Object v) { def = v; return this; }
    public Field min(double v) { min = v; return this; }
    public Field max(double v) { max = v; return this; }
    public Field help(String s) { help = s; return this; }
    public Field ph(String s) { placeholder = s; return this; }
    public Field accept(String s) { accept = s; return this; }
    public Field fmt(String s) { format = s; return this; }
    public Field w(int col) { this.col = col; return this; }
    public Field opts(List<Opt> o) { options = o; return this; }
    public Field render(Render r) { render = r; return this; }

    public Field showIf(String key, String... values) {
        showIfKey = key;
        showIfIn = Arrays.asList(values);
        return this;
    }

    public boolean isNumeric() {
        return "number".equals(type) || "money".equals(type) || "percent".equals(type);
    }

    /** Whether the field is visible given the current form values (showIf). */
    public boolean visible(JSONObject values) {
        if (showIfKey == null) return true;
        Object cur = values.opt(showIfKey);
        String s = cur == null || cur == JSONObject.NULL ? "" : String.valueOf(cur);
        if (cur instanceof Double && ((Double) cur) == Math.rint((Double) cur)) s = String.valueOf(((Double) cur).longValue());
        return showIfIn.contains(s);
    }
}
