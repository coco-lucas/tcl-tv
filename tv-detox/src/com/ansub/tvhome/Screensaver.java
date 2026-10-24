package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Screensaver extends DreamService {
    private static final int SHOW_MS = 60000, FADE_MS = 3000;
    private static final float ZOOM = 1.08f;
    private static final PathInterpolator EASE_IN_OUT = MainActivity.EASE_FOCUS;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ImageView[] views = new ImageView[2];
    private final Map<String, String> captions = new HashMap<>();
    private File[] photos = new File[0];
    private TextView caption;
    private TextClock time;
    private int next, front;
    private float scale = 1f;
    private boolean running;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        File dir = getExternalFilesDir("screensaver");
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        if (fs != null) {
            Arrays.sort(fs);
            photos = fs;
        }
        captions.putAll(MainActivity.names(new File(dir, "captions.txt")));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        for (int i = 0; i < 2; i++) {
            views[i] = new ImageView(this);
            views[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            views[i].setAlpha(0f);
            root.addView(views[i], new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x59000000, 0x00000000, 0x00000000, 0x73000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        Typeface light = font(300), regular = font(400);
        time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTypeface(light);
        time.setTextColor(0xF2FFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(84));
        time.setFontFeatureSettings("tnum");
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.topMargin = px(64);
        tp.rightMargin = px(96);
        root.addView(time, tp);

        caption = new TextView(this);
        caption.setTypeface(regular);
        caption.setTextColor(0xCCFFFFFF);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
        caption.setSingleLine(true);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cp.leftMargin = px(96);
        cp.bottomMargin = px(72);
        root.addView(caption, cp);

        setContentView(root);
        next = photos.length == 0 ? 0 : random.nextInt(photos.length);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        showNext();
    }

    @Override
    public void onDreamingStopped() {
        running = false;
        ui.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void showNext() {
        if (!running || photos.length == 0) return;
        final File f = photos[next];
        next = (next + 1) % photos.length;
        new Thread(() -> {
            final Bitmap b = BitmapFactory.decodeFile(f.getPath());
            ui.post(() -> {
                if (!running) return;
                if (b != null) crossfadeTo(b, captions.containsKey(f.getName()) ? captions.get(f.getName()) : "");
                ui.postDelayed(this::showNext, SHOW_MS - FADE_MS);
            });
        }).start();
    }

    private void crossfadeTo(Bitmap b, String place) {
        final ImageView old = views[front];
        front = 1 - front;
        ImageView v = views[front];
        v.animate().cancel();
        v.setImageBitmap(b);
        FrameLayout root = (FrameLayout) v.getParent();
        root.removeView(v);
        root.addView(v, 1, new FrameLayout.LayoutParams(-1, -1));
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0);
        v.setTranslationY(0);
        v.setAlpha(0f);
        float dx = (random.nextBoolean() ? 1 : -1) * px(40), dy = (random.nextBoolean() ? 1 : -1) * px(16);
        v.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(EASE_IN_OUT).withEndAction(() -> {
            old.setImageDrawable(null);
            old.setAlpha(0f);
        }).start();
        v.animate().scaleX(ZOOM).scaleY(ZOOM).translationX(dx).translationY(dy)
                .setDuration(SHOW_MS + FADE_MS).setInterpolator(new LinearInterpolator()).start();
        caption.animate().alpha(0f).setDuration(FADE_MS / 2).setInterpolator(EASE_IN_OUT).withEndAction(() -> {
            caption.setText(place);
            caption.animate().alpha(1f).setDuration(FADE_MS / 2).setInterpolator(EASE_IN_OUT).start();
        }).start();
        time.animate().translationX(random.nextInt(px(24)) - px(12)).translationY(random.nextInt(px(16)) - px(8))
                .setDuration(FADE_MS).setInterpolator(EASE_IN_OUT).start();
    }

    private Typeface font(int weight) {
        Typeface t = MainActivity.inter(this, weight);
        return t != null ? t : Typeface.create(weight < 400 ? "sans-serif-light" : "sans-serif", Typeface.NORMAL);
    }

    private int px(int v) {
        return Math.round(v * scale);
    }
}
