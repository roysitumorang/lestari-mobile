package id.lestari.mobile.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Opt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dialog helpers: single/multi pickers with search, confirmations and action sheets. */
public final class Dialogs {
    private Dialogs() {}

    public interface OnPick {
        void pick(Opt option);
    }

    public interface OnMulti {
        void done(List<Object> values);
    }

    public interface OnIndex {
        void choose(int index);
    }

    private static AlertDialog.Builder builder(Context c) {
        return new AlertDialog.Builder(c, Theme.dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    /** Pick one option; a search box appears for long lists. {@code allowNone} adds "— None —". */
    public static void pick(Context c, String title, List<Opt> options, boolean allowNone, OnPick onPick) {
        final List<Opt> all = new ArrayList<>();
        if (allowNone) all.add(new Opt(null, I18n.t("— None —")));
        all.addAll(options);
        final List<Opt> shown = new ArrayList<>(all);
        LinearLayout box = Ui.vbox(c);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(c, android.R.layout.simple_list_item_1, labels(shown));
        ListView list = new ListView(c);
        list.setAdapter(adapter);
        if (all.size() > 10) {
            EditText search = new EditText(c);
            search.setHint(I18n.t("Search"));
            search.setSingleLine(true);
            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int d) {}

                @Override
                public void afterTextChanged(Editable e) {
                    String q = e.toString().trim().toLowerCase(Locale.ROOT);
                    shown.clear();
                    for (Opt o : all) if (q.isEmpty() || o.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(o);
                    adapter.clear();
                    adapter.addAll(labels(shown));
                }
            });
            box.addView(search, Ui.margins(Ui.match(), c, 20, 8, 20, 0));
        }
        box.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, Math.min(420, 52 * Math.max(all.size(), 1)))));
        final AlertDialog d = builder(c).setTitle(title).setView(box).setNegativeButton(I18n.t("Cancel"), null).create();
        list.setOnItemClickListener((parent, view, pos, id) -> {
            d.dismiss();
            onPick.pick(shown.get(pos));
        });
        d.show();
    }

    public static void multiPick(Context c, String title, List<Opt> options, Set<String> selected, OnMulti onDone) {
        String[] labels = labels(options).toArray(new String[0]);
        final boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++) checked[i] = selected.contains(String.valueOf(options.get(i).value));
        builder(c).setTitle(title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(I18n.t("Cancel"), null)
                .setNeutralButton(I18n.t("Clear"), (dialog, w) -> onDone.done(new ArrayList<>()))
                .setPositiveButton("OK", (dialog, w) -> {
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) out.add(options.get(i).value);
                    onDone.done(out);
                })
                .show();
    }

    /** Confirmation; with {@code typeToConfirm} the OK button stays disabled until the word is typed. */
    public static void confirm(Context c, String title, String message, String okText, boolean danger, String typeToConfirm, Runnable onOk) {
        AlertDialog.Builder b = builder(c).setTitle(title == null ? I18n.t("Confirm") : title).setNegativeButton(I18n.t("Cancel"), null)
                .setPositiveButton(okText == null ? I18n.t("Yes") : okText, (d, w) -> onOk.run());
        EditText input = null;
        if (typeToConfirm != null) {
            LinearLayout box = Ui.vbox(c);
            int p = Ui.dp(c, 22);
            box.setPadding(p, Ui.dp(c, 8), p, 0);
            box.addView(Ui.body(c, message));
            TextView hint = Ui.muted(c, I18n.t("Type {word} to continue", "word", typeToConfirm));
            hint.setPadding(0, Ui.dp(c, 12), 0, 0);
            box.addView(hint);
            input = new EditText(c);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            box.addView(input, Ui.match());
            b.setView(box);
        } else {
            b.setMessage(message);
        }
        AlertDialog d = b.create();
        d.show();
        Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (danger) ok.setTextColor(Theme.danger);
        if (input != null) {
            ok.setEnabled(false);
            final EditText in = input;
            in.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b2, int d2) {}

                @Override
                public void onTextChanged(CharSequence s, int a, int b2, int d2) {}

                @Override
                public void afterTextChanged(Editable e) {
                    ok.setEnabled(e.toString().trim().equalsIgnoreCase(typeToConfirm));
                }
            });
        }
    }

    /** A list of choices (row actions). */
    public static void sheet(Context c, String title, List<String> items, OnIndex onChoose) {
        builder(c).setTitle(title).setItems(items.toArray(new String[0]), (d, which) -> onChoose.choose(which))
                .setNegativeButton(I18n.t("Close"), null).show();
    }

    public static void info(Context c, String title, View body) {
        builder(c).setTitle(title).setView(body).setPositiveButton(I18n.t("Close"), null).show();
    }

    public static AlertDialog.Builder alert(Context c) {
        return builder(c);
    }

    private static List<String> labels(List<Opt> opts) {
        List<String> out = new ArrayList<>();
        for (Opt o : opts) out.add(o.label);
        return out;
    }
}
