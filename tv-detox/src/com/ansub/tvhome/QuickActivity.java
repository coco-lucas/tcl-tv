package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class QuickActivity extends Activity {
    private static final PathInterpolator EASE_DRAWER = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.23f, 1f, 0.32f, 1f);
    private static final int ENTER_MS = 380, EXIT_MS = 200, STAGGER_MS = 24;

    private float scale = 1f;
    private Typeface light, regular, semibold;
    private View scrim, panel;
    private final List<View> rows = new ArrayList<>();
    private boolean closing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setWindowAnimations(0);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        light = font(300, "sans-serif-light");
        regular = font(400, "sans-serif");
        semibold = font(600, "sans-serif-medium");

        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> close(null));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        int w = px(560), m = px(40);
        FrameLayout sheet = new FrameLayout(this);
        final float corner = px(36) * 1.5f;
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                if (android.os.Build.VERSION.SDK_INT >= 30) o.setPath(Glass.squircle(v.getWidth(), v.getHeight(), corner));
                else o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner / 1.5f);
            }
        });
        sheet.setClipToOutline(true);
        sheet.setBackgroundColor(0xE01C1C1E);
        sheet.setForeground(new Glass.Edge(corner, Math.max(1, scale)));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(w, -1, Gravity.END);
        sp.setMargins(m, m, m, m);
        root.addView(sheet, sp);
        panel = sheet;

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(28), px(40), px(28), px(32));
        sheet.addView(list, new FrameLayout.LayoutParams(-1, -1));

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        style(time, 72, Color.WHITE, light);
        time.setPadding(px(20), 0, 0, 0);
        list.addView(time);
        TextClock date = new TextClock(this);
        String day = Strings.datePattern();
        date.setFormat12Hour(day);
        date.setFormat24Hour(day);
        style(date, 26, 0x99FFFFFF, regular);
        date.setPadding(px(20), 0, 0, px(28));
        list.addView(date);
        rows.add(time);
        rows.add(date);

        addRow(list, "wifi", "Wi‑Fi", () -> Open.network(this));
        addRow(list, "bluetooth", "Bluetooth", () -> Open.quick(this, "bluetooth"));
        if (!Open.quickIntents(this, "picture").isEmpty())
            addRow(list, "image", "Imagem", () -> Open.quick(this, "picture"));
        if (!Open.quickIntents(this, "sound").isEmpty())
            addRow(list, "speaker", "Som", () -> Open.quick(this, "sound"));
        addSection(list, "Entradas");
        Intent live = MainActivity.launchIntent(getPackageManager(), "com.tcl.tv");
        if (live != null) addRow(list, "tv", "TV ao vivo", () -> Open.start(this, live, null));
        for (final TvInputInfo in : Open.hdmiInputs(this, 1))
            addRow(list, "hdmi-port", Open.inputLabel(this, in), () -> Open.input(this, in.getId()));
        addSection(list, null);
        addRow(list, "settings", "Todos os ajustes", () -> Open.start(this, new Intent(Settings.ACTION_SETTINGS), null));

        setContentView(root);
        enter(w + m);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        close(null);
    }

    @Override
    public void onBackPressed() {
        close(null);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_SETTINGS) {
            close(null);
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void enter(int offscreen) {
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(ENTER_MS).setInterpolator(EASE_OUT).start();
        panel.setTranslationX(offscreen);
        panel.animate().translationX(0).setDuration(ENTER_MS).setInterpolator(EASE_DRAWER).start();
        for (int i = 0; i < rows.size(); i++) {
            View v = rows.get(i);
            float target = v.getAlpha();
            v.setAlpha(0f);
            v.setTranslationX(px(36));
            v.animate().alpha(target).translationX(0).setStartDelay(80 + i * STAGGER_MS)
                    .setDuration(ENTER_MS - 40).setInterpolator(EASE_OUT).start();
        }
        if (rows.size() > 2) rows.get(2).requestFocus();
    }

    private void close(Runnable then) {
        if (closing) return;
        closing = true;
        if (then != null) {
            then.run();
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        scrim.animate().alpha(0f).setDuration(EXIT_MS).setInterpolator(EASE_OUT).start();
        panel.animate().translationX(panel.getWidth() + px(40)).setDuration(EXIT_MS).setInterpolator(EASE_OUT)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    private void addSection(LinearLayout list, String title) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, px(1)));
        lp.setMargins(px(20), px(16), px(20), px(12));
        list.addView(line, lp);
        rows.add(line);
        if (title == null) return;
        TextView t = new TextView(this);
        t.setText(title);
        style(t, 24, 0x80FFFFFF, semibold);
        t.setPadding(px(20), px(4), 0, px(8));
        list.addView(t);
        rows.add(t);
    }

    private void addRow(LinearLayout list, String icon, String label, Runnable action) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(20), 0, px(20), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(22));
        bg.setColor(Color.TRANSPARENT);
        row.setBackground(bg);

        final LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        Drawable d = Open.lucide(this, icon);
        if (d != null) iv.setImageDrawable(d);
        content.addView(iv, new LinearLayout.LayoutParams(px(36), px(36)));
        TextView t = new TextView(this);
        t.setText(label);
        style(t, 30, Color.WHITE, regular);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(24);
        content.addView(t, tp);
        content.setAlpha(MainActivity.REST_ALPHA);
        row.addView(content, new LinearLayout.LayoutParams(-1, -1));

        row.setFocusable(true);
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x24FFFFFF : Color.TRANSPARENT);
            content.animate().alpha(has ? 1f : MainActivity.REST_ALPHA).setDuration(MainActivity.FOCUS_MS)
                    .setInterpolator(MainActivity.EASE_FOCUS).start();
        });
        row.setOnClickListener(v -> close(action));
        list.addView(row, new LinearLayout.LayoutParams(-1, px(84)));
        rows.add(row);
    }

    private void style(TextView t, int size, int color, Typeface tf) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
    }

    private Typeface font(int weight, String fallback) {
        Typeface t = MainActivity.inter(this, weight);
        return t != null ? t : Typeface.create(fallback, Typeface.NORMAL);
    }

    private int px(int v) {
        return Math.round(v * scale);
    }
}
