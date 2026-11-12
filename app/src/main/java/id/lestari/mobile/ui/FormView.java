package id.lestari.mobile.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import id.lestari.mobile.core.Api;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.FileRef;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema-driven form (the Android counterpart of the web client's UI.buildForm):
 * builds inputs for fields, reads typed values, validates required fields, shows server field errors
 * and applies {@code showIf} visibility.
 */
public final class FormView {
    public interface OnChange {
        void changed();
    }

    private final MainActivity act;
    private final Context c;
    private final LinearLayout root;
    private final List<W> widgets = new ArrayList<>();
    private final boolean compact;
    private OnChange onChange;

    public FormView(MainActivity act, List<Field> fields, JSONObject values, boolean compact) {
        this.act = act;
        this.c = act;
        this.compact = compact;
        root = Ui.vbox(c);
        JSONObject v = values == null ? new JSONObject() : values;
        for (Field f : fields) {
            if (!f.form) continue;
            W w = widget(f, initial(f, v), false);
            widgets.add(w);
            root.addView(w.block, Ui.match());
        }
        refreshVisibility();
    }

    public View view() {
        return root;
    }

    public void setOnChange(OnChange l) {
        onChange = l;
    }

    private void changed() {
        refreshVisibility();
        if (onChange != null) onChange.changed();
    }

    private static Object initial(Field f, JSONObject values) {
        if (f.key == null) return null;
        if (!values.has(f.key)) return f.def;
        Object v = values.opt(f.key);
        return v == JSONObject.NULL ? null : v;
    }

    /** Values of the visible fields, typed for the API (numbers, booleans, arrays, base64 passwords, null for empty). */
    public JSONObject read() {
        return read(false);
    }

    private JSONObject read(boolean raw) {
        JSONObject out = new JSONObject();
        JSONObject cur = raw ? null : read(true);
        for (W w : widgets) {
            if (w.f.key == null || "heading".equals(w.f.type)) continue;
            if (!raw && !w.f.visible(cur)) continue;
            Json.put(out, w.f.key, w.get());
        }
        return out;
    }

    private void refreshVisibility() {
        JSONObject cur = read(true);
        for (W w : widgets) {
            if (w.f.showIfKey != null) w.block.setVisibility(w.f.visible(cur) ? View.VISIBLE : View.GONE);
        }
    }

    /** Checks required fields; marks the missing ones. */
    public boolean validate() {
        boolean ok = true;
        JSONObject cur = read(true);
        for (W w : widgets) {
            w.setError(null);
            if (!w.f.required || w.f.key == null || !w.f.visible(cur)) continue;
            Object v = w.get();
            boolean empty = v == null || v == JSONObject.NULL || "".equals(v) || (v instanceof JSONArray && ((JSONArray) v).length() == 0)
                    || ("switch".equals(w.f.type) && Boolean.FALSE.equals(v));
            if (empty) {
                w.setError(I18n.t("Required"));
                ok = false;
            }
        }
        return ok;
    }

    public void setErrors(Map<String, String> errors) {
        for (W w : widgets) w.setError(w.f.key == null ? null : errors.get(w.f.key));
    }

    // ------------------------------------------------------------------ widgets

    private abstract class W {
        final Field f;
        final LinearLayout block;
        final TextView error;

        W(Field f) {
            this.f = f;
            block = Ui.vbox(c);
            block.setPadding(0, Ui.dp(c, compact ? 4 : 8), 0, Ui.dp(c, compact ? 4 : 8));
            error = Ui.text(c, "", 12, Theme.danger, false);
            error.setVisibility(View.GONE);
        }

        void label() {
            TextView l = Ui.text(c, f.label + (f.required ? " *" : ""), 13, Theme.muted, false);
            l.setPadding(0, 0, 0, Ui.dp(c, 4));
            block.addView(l);
        }

        void finish() {
            block.addView(error);
            if (!TextUtils.isEmpty(f.help) && !compact) {
                TextView h = Ui.text(c, f.help, 12, Theme.muted, false);
                h.setPadding(0, Ui.dp(c, 2), 0, 0);
                block.addView(h);
            }
        }

        abstract Object get();

        void setError(String msg) {
            error.setText(msg == null ? "" : msg);
            error.setVisibility(msg == null || msg.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private W widget(Field f, Object value, boolean inItems) {
        String t = f.type == null ? "text" : f.type;
        switch (t) {
            case "heading":
                return new HeadingW(f);
            case "switch":
                return new SwitchW(f, value);
            case "select":
                return new SelectW(f, value);
            case "multiselect":
                return new MultiW(f, value);
            case "date":
            case "datetime":
            case "time":
                return new DateW(f, value);
            case "image":
                return new ImageW(f, value);
            case "file":
                return new FileW(f);
            case "items":
                return new ItemsW(f, value);
            default:
                return new TextW(f, value);
        }
    }

    private static EditText styledEdit(Context c) {
        EditText e = new EditText(c);
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.muted);
        e.setBackground(Ui.rounded(Theme.surface, Theme.border, 8, c));
        int p = Ui.dp(c, 10);
        e.setPadding(p, p, p, p);
        e.setTextSize(15);
        return e;
    }

    private Button pickerButton(String text) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Theme.text);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setStateListAnimator(null);
        b.setBackground(Ui.rounded(Theme.surface, Theme.border, 8, c));
        int p = Ui.dp(c, 10);
        b.setPadding(p, 0, p, 0);
        b.setMinHeight(Ui.dp(c, 44));
        return b;
    }

    /** Integral doubles without ".0" so values look like what the user typed. */
    static String plain(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return String.valueOf(v);
    }

    private final class HeadingW extends W {
        HeadingW(Field f) {
            super(f);
            TextView h = Ui.text(c, f.label, 15, Theme.primary, true);
            h.setPadding(0, Ui.dp(c, 10), 0, 0);
            block.addView(h);
            block.addView(Ui.divider(c), Ui.margins(Ui.match(), c, 0, 6, 0, 0));
        }

        @Override
        Object get() {
            return null;
        }
    }

    private final class TextW extends W {
        final EditText edit;

        TextW(Field f, Object value) {
            super(f);
            label();
            edit = styledEdit(c);
            String t = f.type == null ? "text" : f.type;
            edit.setText("password".equals(t) ? "" : plain(value));
            if (f.placeholder != null) edit.setHint(f.placeholder);
            switch (t) {
                case "number":
                case "money":
                case "percent":
                    edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    if (f.placeholder == null) edit.setHint("money".equals(t) ? "Rp" : "percent".equals(t) ? "%" : "");
                    break;
                case "email":
                    edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    break;
                case "tel":
                    edit.setInputType(InputType.TYPE_CLASS_PHONE);
                    break;
                case "password":
                    edit.setInputType(f.encode ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                            : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                    break;
                case "textarea":
                    edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    edit.setMinLines(3);
                    edit.setGravity(Gravity.TOP | Gravity.START);
                    break;
                default:
                    edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    edit.setSingleLine(true);
            }
            if (f.readonly) {
                edit.setEnabled(false);
                edit.setTextColor(Theme.muted);
            }
            if ("color".equals(t)) {
                LinearLayout row = Ui.hbox(c);
                final View swatch = new View(c);
                row.addView(edit, Ui.weight(1));
                row.addView(swatch, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)), c, 10, 0, 0, 0));
                Runnable paint = () -> {
                    try {
                        swatch.setBackground(Ui.rounded(Color.parseColor(edit.getText().toString().trim()), Theme.border, 6, c));
                    } catch (IllegalArgumentException e) {
                        swatch.setBackground(Ui.rounded(Theme.surface, Theme.border, 6, c));
                    }
                };
                paint.run();
                edit.addTextChangedListener(watcher(paint));
                block.addView(row, Ui.match());
            } else if ("password".equals(t)) {
                block.addView(Ui.passwordField(c, edit), Ui.match());
            } else {
                block.addView(edit, Ui.match());
            }
            edit.addTextChangedListener(watcher(FormView.this::changed));
            finish();
        }

        @Override
        Object get() {
            String s = edit.getText().toString().trim();
            if (s.isEmpty()) return null;
            if (f.isNumeric()) {
                try {
                    return Double.valueOf(s.replace(",", "."));
                } catch (NumberFormatException e) {
                    return s; // the server answers with a field error
                }
            }
            if ("password".equals(f.type) && f.encode) return Api.encodePassword(s);
            return s;
        }
    }

    private static TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                r.run();
            }
        };
    }

    private final class SwitchW extends W {
        final Switch sw;

        SwitchW(Field f, Object value) {
            super(f);
            sw = new Switch(c);
            sw.setText(f.label);
            sw.setTextColor(Theme.text);
            sw.setTextSize(15);
            sw.setChecked(Json.truthy(value));
            sw.setEnabled(!f.readonly);
            sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.primary, 0xFFF1F1F1}));
            sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{(Theme.primary & 0x00FFFFFF) | 0x88000000, 0x55888888}));
            sw.setOnCheckedChangeListener((b, on) -> changed());
            block.addView(sw, Ui.match());
            finish();
        }

        @Override
        Object get() {
            return sw.isChecked();
        }
    }

    /** Normalized comparison key for option values (3, 3.0 and "3" are equal). */
    static String key(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        return plain(v);
    }

    private final class SelectW extends W {
        final Button button;
        List<Opt> options;
        Object value;

        SelectW(Field f, Object initial) {
            super(f);
            label();
            value = initial;
            options = f.options;
            button = pickerButton(String.valueOf(placeholder()));
            button.setEnabled(!f.readonly);
            button.setOnClickListener(v -> open());
            block.addView(button, Ui.match());
            finish();
            if (f.lookup != null) {
                LookupCache.load(act, f.lookup, opts -> {
                    options = opts;
                    paint();
                });
            } else {
                paint();
            }
        }

        private String placeholder() {
            return f.placeholder != null ? f.placeholder : I18n.t("— Select —");
        }

        void paint() {
            String text = placeholder();
            if (value != null) {
                text = plain(value);
                if (options != null) for (Opt o : options) if (key(o.value).equals(key(value))) text = o.label;
            }
            button.setText(text);
            button.setTextColor(value == null ? Theme.muted : Theme.text);
        }

        void open() {
            if (options == null) {
                act.toast(I18n.t("Loading options…"));
                return;
            }
            Dialogs.pick(c, f.label, options, !f.required || value == null, o -> {
                value = o.value;
                paint();
                changed();
            });
        }

        @Override
        Object get() {
            return value;
        }
    }

    private final class MultiW extends W {
        final Button button;
        List<Opt> options;
        final List<Object> values = new ArrayList<>();

        MultiW(Field f, Object initial) {
            super(f);
            label();
            values.addAll(Json.values(initial));
            options = f.options;
            button = pickerButton("");
            button.setEnabled(!f.readonly);
            button.setOnClickListener(v -> {
                if (options == null) {
                    act.toast(I18n.t("Loading options…"));
                    return;
                }
                Set<String> sel = new HashSet<>();
                for (Object x : values) sel.add(key(x));
                Dialogs.multiPick(c, f.label, options, sel, picked -> {
                    values.clear();
                    values.addAll(picked);
                    paint();
                    changed();
                });
            });
            block.addView(button, Ui.match());
            finish();
            if (f.lookup != null) LookupCache.load(act, f.lookup, opts -> {
                options = opts;
                paint();
            });
            else paint();
        }

        void paint() {
            if (values.isEmpty()) {
                button.setText(I18n.t("None selected"));
                button.setTextColor(Theme.muted);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Object v : values) {
                if (sb.length() > 0) sb.append(", ");
                String label = plain(v);
                if (options != null) for (Opt o : options) if (key(o.value).equals(key(v))) label = o.label;
                sb.append(label);
            }
            button.setText(sb.toString());
            button.setTextColor(Theme.text);
        }

        @Override
        Object get() {
            JSONArray a = new JSONArray();
            for (Object v : values) a.put(v);
            return a;
        }
    }

    private final class DateW extends W {
        final Button button;
        String value; // yyyy-MM-dd | ISO datetime | HH:mm

        DateW(Field f, Object initial) {
            super(f);
            label();
            String s = initial == null ? null : String.valueOf(initial);
            if (s != null && s.isEmpty()) s = null;
            if (s != null && "date".equals(f.type) && s.length() > 10) s = s.substring(0, 10);
            if (s != null && "time".equals(f.type) && s.length() > 5) s = s.substring(0, 5);
            value = s;
            LinearLayout row = Ui.hbox(c);
            button = pickerButton("");
            button.setEnabled(!f.readonly);
            button.setOnClickListener(v -> open());
            row.addView(button, Ui.weight(1));
            if (!f.readonly) {
                Button clear = Ui.button(c, I18n.t("Clear"), Ui.Style.TEXT, v -> {
                    value = null;
                    paint();
                    changed();
                });
                row.addView(clear, Ui.wrap());
            }
            block.addView(row, Ui.match());
            finish();
            paint();
        }

        void paint() {
            String text;
            if (value == null) text = I18n.t("date".equals(f.type) ? "Pick a date" : "time".equals(f.type) ? "Pick a time" : "Pick date and time");
            else if ("date".equals(f.type)) text = Format.date(value);
            else if ("time".equals(f.type)) text = value;
            else text = Format.datetime(value);
            button.setText(text);
            button.setTextColor(value == null ? Theme.muted : Theme.text);
        }

        void open() {
            if ("time".equals(f.type)) {
                LocalTime t = value == null ? LocalTime.of(9, 0) : LocalTime.parse(value);
                new TimePickerDialog(c, (tp, h, m) -> {
                    value = String.format(java.util.Locale.US, "%02d:%02d", h, m);
                    paint();
                    changed();
                }, t.getHour(), t.getMinute(), true).show();
                return;
            }
            LocalDateTime cur = LocalDateTime.now();
            try {
                if (value != null && "date".equals(f.type)) cur = LocalDate.parse(value).atStartOfDay();
                else if (value != null) cur = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) {
                // keep "now"
            }
            final LocalDateTime base = cur;
            new DatePickerDialog(c, (dp, y, m, d) -> {
                LocalDate date = LocalDate.of(y, m + 1, d);
                if ("date".equals(f.type)) {
                    value = date.toString();
                    paint();
                    changed();
                } else {
                    new TimePickerDialog(c, (tp, h, min) -> {
                        value = date.atTime(h, min).atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        paint();
                        changed();
                    }, base.getHour(), base.getMinute(), true).show();
                }
            }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()).show();
        }

        @Override
        Object get() {
            return value;
        }
    }

    private final class ImageW extends W {
        final ImageView preview;
        String url;

        ImageW(Field f, Object initial) {
            super(f);
            label();
            url = initial == null ? null : String.valueOf(initial);
            if (url != null && url.isEmpty()) url = null;
            LinearLayout row = Ui.hbox(c);
            preview = Ui.thumb(c, url, 72);
            row.addView(preview);
            LinearLayout buttons = Ui.vbox(c);
            Button choose = Ui.button(c, I18n.t("Choose image"), Ui.Style.OUTLINE, v -> act.pickFile(new String[]{"image/*"}, file -> upload(file)));
            Button remove = Ui.button(c, I18n.t("Remove"), Ui.Style.TEXT, v -> {
                url = null;
                ImageLoader.load(preview, null, 0);
                changed();
            });
            choose.setEnabled(!f.readonly);
            remove.setEnabled(!f.readonly);
            buttons.addView(choose, Ui.wrap());
            buttons.addView(remove, Ui.wrap());
            row.addView(buttons, Ui.margins(Ui.wrap(), c, 12, 0, 0, 0));
            block.addView(row, Ui.match());
            finish();
        }

        void upload(FileRef file) {
            act.toast(I18n.t("Uploading…"));
            act.run(() -> act.api().upload("/uploads", new Api.Multipart().file("file", file.name, file.mimeType, file.bytes).field("purpose", f.key))
                    .getJSONObject("data").getString("url"), u -> {
                url = u;
                ImageLoader.load(preview, u, Ui.dp(c, 72));
                changed();
            });
        }

        @Override
        Object get() {
            return url;
        }
    }

    private final class FileW extends W {
        final Button button;
        FileRef file;

        FileW(Field f) {
            super(f);
            label();
            button = pickerButton(I18n.t("Choose file"));
            button.setTextColor(Theme.muted);
            button.setOnClickListener(v -> act.pickFile(mimeTypes(f.accept), picked -> {
                file = picked;
                button.setText(picked.name);
                button.setTextColor(Theme.text);
                changed();
            }));
            block.addView(button, Ui.match());
            finish();
        }

        @Override
        Object get() {
            return file;
        }
    }

    static String[] mimeTypes(String accept) {
        if (accept == null || accept.isEmpty()) return new String[]{"*/*"};
        return accept.split("\\s*,\\s*");
    }

    /** An editable list of rows (wholesale tiers, purchase items, recipe lines …). */
    private final class ItemsW extends W {
        final LinearLayout rows;
        final List<Map<String, W>> rowWidgets = new ArrayList<>();

        ItemsW(Field f, Object initial) {
            super(f);
            label();
            rows = Ui.vbox(c);
            block.addView(rows, Ui.match());
            for (JSONObject r : Json.objects(initial)) addRow(r);
            Button add = Ui.button(c, "+ Add row", Ui.Style.OUTLINE, v -> {
                addRow(new JSONObject());
                changed();
            });
            add.setEnabled(!f.readonly);
            block.addView(add, Ui.margins(Ui.wrap(), c, 0, 6, 0, 0));
            finish();
        }

        void addRow(JSONObject r) {
            final LinearLayout card = Ui.vbox(c);
            card.setBackground(Ui.rounded(Theme.background, Theme.border, 8, c));
            int p = Ui.dp(c, 10);
            card.setPadding(p, Ui.dp(c, 2), p, p);
            final Map<String, W> ws = new LinkedHashMap<>();
            for (Field s : f.fields) {
                W w = widget(s, initial(s, r), true);
                ws.put(s.key, w);
                card.addView(w.block, Ui.match());
            }
            Button del = Ui.button(c, I18n.t("Remove row"), Ui.Style.TEXT, v -> {
                rows.removeView(card);
                rowWidgets.remove(ws);
                changed();
            });
            del.setTextColor(Theme.danger);
            card.addView(del, Ui.wrap());
            rowWidgets.add(ws);
            rows.addView(card, Ui.margins(Ui.match(), c, 0, 4, 0, 4));
        }

        @Override
        Object get() {
            JSONArray a = new JSONArray();
            for (Map<String, W> ws : rowWidgets) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, W> e : ws.entrySet()) Json.put(o, e.getKey(), e.getValue().get());
                a.put(o);
            }
            return a;
        }
    }
}
