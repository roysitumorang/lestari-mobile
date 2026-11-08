package id.lestari.mobile.ui;

import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Opt;

import java.util.ArrayList;
import java.util.List;

/** Loads lookup options in the background and hands them to the UI thread. */
final class LookupCache {
    private LookupCache() {}

    interface Loaded {
        void loaded(List<Opt> options);
    }

    static void load(MainActivity act, Lookup lookup, Loaded cb) {
        act.runQuietly(() -> lookup.load(act.api()), cb::loaded, e -> cb.loaded(new ArrayList<>()));
    }
}
