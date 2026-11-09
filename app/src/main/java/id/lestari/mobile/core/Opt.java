package id.lestari.mobile.core;

import java.util.ArrayList;
import java.util.List;

/** A select option. The value keeps its JSON type (string or number). */
public final class Opt {
    public final Object value;
    public final String label;

    public Opt(Object value, String label) {
        this.value = value;
        this.label = label;
    }

    /** Options whose value and label are the same string. */
    public static List<Opt> of(String... values) {
        List<Opt> out = new ArrayList<>();
        for (String v : values) out.add(new Opt(v, I18n.t(v)));
        return out;
    }

    /** Options from value/label pairs: pairs("a", "Label A", "b", "Label B"). Values may be numbers. */
    public static List<Opt> pairs(Object... vl) {
        List<Opt> out = new ArrayList<>();
        for (int i = 0; i + 1 < vl.length; i += 2) out.add(new Opt(vl[i], I18n.t(String.valueOf(vl[i + 1]))));
        return out;
    }

    /** Options whose label is the humanized value ("pln_token" → "Pln token"). */
    public static List<Opt> humanized(String... values) {
        List<Opt> out = new ArrayList<>();
        for (String v : values) out.add(new Opt(v, I18n.t(Format.humanize(v))));
        return out;
    }

    public static List<Opt> concat(List<Opt> a, List<Opt> b) {
        List<Opt> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
