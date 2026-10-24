package com.ansub.tvhome;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class Grid {
    static final class Item {
        final String label;
        final Drawable art;
        final boolean cover;
        private Bitmap baked;

        Item(String label, Drawable art, boolean cover) {
            this.label = label;
            this.art = art;
            this.cover = cover;
        }
    }

    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.23f, 1f, 0.32f, 1f);
    private static final int ENTER_MS = 320, STAGGER_MS = 28, STAGGER_MAX = 15, EXIT_MS = 160;
    private static final float TILE_REST_ALPHA = 0.45f, LABEL_REST_ALPHA = 0.5f;
    private static final int FOCUS_IN_MS = 150, FOCUS_OUT_MS = 220;

    private Grid() {}

    static void show(MainActivity a, String title, List<Item> items, int cols, int focus, MainActivity.Pick pick) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        Window win = d.getWindow();
        win.setWindowAnimations(0);
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        int sw = a.getResources().getDisplayMetrics().widthPixels;
        int m = a.px(96), gap = a.px(40), labelH = a.px(64), top = a.px(168);
        int tw = (sw - 2 * m - (cols - 1) * gap) / cols, th = tw * 9 / 16;

        FrameLayout page = new FrameLayout(a);
        page.setClipChildren(false);
        TextView head = a.text(a.px(44), Color.WHITE, MainActivity.semibold);
        head.setText(title);
        page.addView(head, MainActivity.at(m, a.px(64), -2, -2));

        final float corner = a.px(18) * 1.5f, stroke = Math.max(1, a.px(1));
        List<View> cells = new ArrayList<>();
        final List<View> parts = new ArrayList<>();
        final boolean[] closing = {false};
        final FrameLayout frame = new FrameLayout(a);
        for (int i = 0; i < items.size(); i++) {
            final int which = i;
            Item it = items.get(i);
            int x = m + (i % cols) * (tw + gap), y = top + (i / cols) * (th + labelH + gap);

            ImageView tile = new ImageView(a);
            tile.setImageBitmap(bake(it, tw, th, corner, stroke));
            final TextView name = a.text(a.px(28), Color.WHITE, MainActivity.regular);
            name.setText(it.label);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setAlpha(LABEL_REST_ALPHA);

            tile.setFocusable(true);
            tile.setScaleX(MainActivity.REST_SCALE);
            tile.setScaleY(MainActivity.REST_SCALE);
            tile.setAlpha(TILE_REST_ALPHA);
            tile.setOnFocusChangeListener((v, has) -> {
                float sc = has ? 1f : MainActivity.REST_SCALE;
                int ms = has ? FOCUS_IN_MS : FOCUS_OUT_MS;
                v.animate().scaleX(sc).scaleY(sc).alpha(has ? 1f : TILE_REST_ALPHA)
                        .setDuration(ms).setInterpolator(EASE_OUT).start();
                name.animate().alpha(has ? 1f : LABEL_REST_ALPHA).setDuration(ms).setInterpolator(EASE_OUT).start();
            });
            tile.setOnClickListener(v -> {
                if (closing[0]) return;
                a.launchFrom = v;
                pick.at(which);
                close(a, d, frame, parts, closing);
            });
            page.addView(tile, MainActivity.at(x, y, tw, th));
            page.addView(name, MainActivity.at(x, y + th + a.px(16), tw, -2));
            cells.add(tile);
            parts.add(tile);
            parts.add(name);
        }
        int rows = (items.size() + cols - 1) / cols;
        page.setMinimumHeight(top + rows * (th + labelH + gap) + m);

        ScrollView scroller = new ScrollView(a);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.addView(page, new FrameLayout.LayoutParams(-1, -2));
        frame.setBackground(backdrop(a));
        frame.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        d.setContentView(frame);
        frame.setAlpha(0f);
        frame.animate().alpha(1f).setDuration(220).setInterpolator(EASE_OUT).start();

        d.setOnKeyListener((dialog, code, ev) -> {
            if (code != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (ev.getAction() == android.view.KeyEvent.ACTION_UP) close(a, d, frame, parts, closing);
            return true;
        });

        parts.add(0, head);
        int lift = a.px(28);
        for (int i = 0; i < parts.size(); i++) {
            View v = parts.get(i);
            float target = v.getAlpha();
            v.setAlpha(0f);
            v.setTranslationY(lift);
            int order = i == 0 ? 0 : Math.min((i + 1) / 2, STAGGER_MAX);
            v.animate().alpha(target).translationY(0).setStartDelay(order * STAGGER_MS)
                    .setDuration(ENTER_MS).setInterpolator(EASE_OUT).start();
        }
        d.show();
        if (!cells.isEmpty()) cells.get(Math.max(0, Math.min(focus, cells.size() - 1))).requestFocus();
    }

    private static void close(MainActivity a, Dialog d, View frame, List<View> parts, boolean[] closing) {
        if (closing[0]) return;
        closing[0] = true;
        for (View v : parts) {
            v.animate().cancel();
            v.animate().alpha(0f).translationY(-a.px(12)).setStartDelay(0).setDuration(EXIT_MS)
                    .setInterpolator(EASE_OUT).start();
        }
        frame.animate().alpha(0f).setDuration(EXIT_MS + 40).setInterpolator(EASE_OUT).start();
        frame.postDelayed(() -> {
            try { d.dismiss(); } catch (Exception ignored) {}
        }, EXIT_MS + 40);
    }

    private static Bitmap bake(Item it, int w, int h, float corner, float stroke) {
        if (it.baked != null && it.baked.getWidth() == w && it.baked.getHeight() == h) return it.baked;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0x1FFFFFFF);
        Drawable art = it.art;
        if (art != null) {
            int iw = art.getIntrinsicWidth(), ih = art.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            if (it.cover) {
                float k = Math.max((float) w / iw, (float) h / ih);
                int dw = Math.round(iw * k), dh = Math.round(ih * k);
                art.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
            } else {
                int sz = h * 2 / 5;
                art.setBounds((w - sz) / 2, (h - sz) / 2, (w + sz) / 2, (h + sz) / 2);
            }
            art.draw(c);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        fill.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        oc.drawPath(Glass.squircle(w, h, corner), fill);
        b.recycle();
        Glass.Edge edge = new Glass.Edge(corner, stroke);
        edge.setBounds(0, 0, w, h);
        edge.draw(oc);
        it.baked = out;
        return out;
    }

    private static Drawable backdrop(MainActivity a) {
        if (a.wall == null) return new ColorDrawable(0xFF0B0B0C);
        Bitmap b = Glass.frosted(a.wall, 480, 0, 0, 480, 270, 8, 0.4f);
        BitmapDrawable d = new BitmapDrawable(a.getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }
}
