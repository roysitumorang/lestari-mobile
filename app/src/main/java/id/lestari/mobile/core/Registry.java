package id.lestari.mobile.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All pages by id plus the navigation menu (the web client's sidebar). */
public final class Registry {
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final List<Group> menu = new ArrayList<>();

    public Page add(Page p) {
        if (pages.containsKey(p.id)) throw new IllegalStateException("Duplicate page id: " + p.id);
        pages.put(p.id, p);
        return p;
    }

    public Page page(String id) {
        return pages.get(id);
    }

    public Collection<Page> pages() {
        return pages.values();
    }

    public List<Group> menu() {
        return menu;
    }

    public Group group(String label, Object... items) {
        Group g = new Group(label, Arrays.asList(items));
        menu.add(g);
        return g;
    }

    /** A menu group; items are page ids (String) or nested groups. */
    public static final class Group {
        public final String label;
        public final List<Object> items;

        public Group(String label, List<Object> items) {
            this.label = label;
            this.items = items;
        }
    }

    public static Group sub(String label, Object... items) {
        return new Group(label, Arrays.asList(items));
    }
}
