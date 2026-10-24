package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Previews {
    static final class Title {
        final String name, detail, image;

        Title(String name, String detail, String image) {
            this.name = name;
            this.detail = detail;
            this.image = image;
        }
    }

    private static final Map<String, Integer> TMDB_PROVIDERS = new HashMap<>();
    private static final Map<String, String> SERVICE_NAMES = new HashMap<>();
    static {
        TMDB_PROVIDERS.put("com.netflix.ninja", 8);
        TMDB_PROVIDERS.put("com.amazon.amazonvideo.livingroom", 119);
        TMDB_PROVIDERS.put("com.disney.disneyplus", 337);
        SERVICE_NAMES.put("com.netflix.ninja", "Netflix");
        SERVICE_NAMES.put("com.amazon.amazonvideo.livingroom", "Prime Video");
        SERVICE_NAMES.put("com.disney.disneyplus", "Disney+");
    }

    private static final int SETTLE_MS = 350, FADE_MS = 500, SLIDE_MS = 12000, CROSS_MS = 1200;
    private static final int LIVE_IN_MS = 450, LIVE_OUT_MS = 220, ART_OUT_MS = 300;
    private static final long REFRESH_MS = 30 * 60_000;
    private static final PathInterpolator EASE = new PathInterpolator(0.42f, 0f, 0.58f, 1f);

    private final MainActivity a;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, List<Title>> titles = new HashMap<>();
    private final Map<String, Long> fetched = new HashMap<>();
    private FrameLayout host;
    private ImageView[] art;
    private TextView name, detail;
    private TvView live;
    private FrameLayout liveFrame;
    private ImageView liveCover;
    private boolean stopping;
    private String current;
    private int slide, front, loop;
    private final java.util.Set<String> loading = new java.util.HashSet<>();
    private int heroW, heroH;

    Previews(MainActivity a) {
        this.a = a;
    }

    void attach(FrameLayout root, int width, int height) {
        heroW = width;
        heroH = height;
        host = new FrameLayout(a);
        host.setAlpha(0f);
        art = new ImageView[] {new ImageView(a), new ImageView(a)};
        for (ImageView v : art) {
            v.setScaleType(ImageView.ScaleType.FIT_XY);
            v.setAlpha(0f);
            host.addView(v, new FrameLayout.LayoutParams(width, height));
        }
        name = a.text(a.px(56), Color.WHITE, MainActivity.semibold);
        name.setSingleLine(true);
        detail = a.text(a.px(26), 0xCCFFFFFF, MainActivity.regular);
        detail.setSingleLine(true);
        host.addView(name, MainActivity.at(a.px(96), height - a.px(250), width - a.px(192), -2));
        host.addView(detail, MainActivity.at(a.px(96), height - a.px(170), width - a.px(192), -2));
        root.addView(host, 0, new FrameLayout.LayoutParams(width, height));
    }

    void prefetch() {
        for (String pkg : TMDB_PROVIDERS.keySet()) {
            Long at = fetched.get(pkg);
            if (at == null || System.currentTimeMillis() - at > REFRESH_MS) load(pkg);
        }
    }

    boolean handles(String key) {
        return TMDB_PROVIDERS.containsKey(key) || key.indexOf('/') > 0;
    }

    void focus(final String key) {
        ui.removeCallbacksAndMessages(null);
        if (!handles(key)) {
            hide();
            return;
        }
        if (key.equals(current)) return;
        current = null;
        if (live != null) stopLive(() -> {
            if (!hasTitles(key)) host.setAlpha(0f);
        });
        ui.postDelayed(() -> show(key), SETTLE_MS);
    }

    void hide() {
        ui.removeCallbacksAndMessages(null);
        current = null;
        if (host == null) return;
        if (live != null || stopping) stopLive(() -> host.setAlpha(0f));
        else host.animate().alpha(0f).setDuration(FADE_MS / 2).setInterpolator(EASE).start();
    }

    private boolean hasTitles(String key) {
        List<Title> list = titles.get(key);
        return list != null && !list.isEmpty();
    }

    private void show(String key) {
        if (key.equals(current)) return;
        current = key;
        stopLive(null);
        if (key.indexOf('/') > 0) {
            startLive(key);
            return;
        }
        List<Title> list = titles.get(key);
        Long at = fetched.get(key);
        if (list == null || at == null || System.currentTimeMillis() - at > REFRESH_MS) load(key);
        if (list != null && !list.isEmpty()) {
            startSlides(key);
        } else if (!stopping) {
            host.animate().alpha(0f).setDuration(FADE_MS / 2).setInterpolator(EASE).start();
        }
    }

    private void startLive(final String inputId) {
        boolean artShowing = art[0].getAlpha() > 0.01f || art[1].getAlpha() > 0.01f;
        for (ImageView v : art) v.animate().alpha(0f).setDuration(ART_OUT_MS).setInterpolator(EASE).start();
        for (View tv : new View[] {name, detail}) tv.animate().alpha(0f).setDuration(ART_OUT_MS).setInterpolator(EASE).start();
        if (artShowing) {
            ui.postDelayed(() -> {
                if (inputId.equals(current)) openLive(inputId);
            }, ART_OUT_MS);
            return;
        }
        openLive(inputId);
    }

    private void openLive(String inputId) {
        int w = a.px(880), h = w * 9 / 16, x = heroW - a.px(96) - w, y = a.px(150);
        float corner = a.px(28) * 1.5f;
        liveFrame = new FrameLayout(a);
        live = new TvView(a);
        live.setStreamVolume(0f);
        liveFrame.addView(live, new FrameLayout.LayoutParams(w, h));
        ImageView corners = new ImageView(a);
        corners.setImageBitmap(corners(x, y, w, h, corner));
        liveFrame.addView(corners, new FrameLayout.LayoutParams(w, h));
        liveCover = new ImageView(a);
        liveCover.setImageBitmap(backdrop(x, y, w, h));
        liveFrame.addView(liveCover, new FrameLayout.LayoutParams(w, h));
        live.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) {
                if (liveCover != null)
                    liveCover.animate().alpha(0f).setDuration(LIVE_IN_MS).setInterpolator(EASE).start();
            }
        });
        host.addView(liveFrame, 2, MainActivity.at(x, y, w, h));

        name.setText(a.inputName(inputId));
        detail.setText("Ao vivo");
        for (View tv : new View[] {name, detail}) {
            tv.animate().cancel();
            tv.setAlpha(0f);
            tv.setTranslationY(a.px(12));
        }
        name.animate().alpha(1f).translationY(0).setStartDelay(120).setDuration(FADE_MS).setInterpolator(EASE).start();
        detail.animate().alpha(1f).translationY(0).setStartDelay(180).setDuration(FADE_MS).setInterpolator(EASE).start();
        host.animate().cancel();
        host.setAlpha(1f);
        live.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId));
    }

    private void stopLive(final Runnable then) {
        if (live == null) {
            if (then != null && !stopping) then.run();
            return;
        }
        stopping = true;
        for (View tv : new View[] {name, detail}) {
            tv.animate().cancel();
            tv.animate().alpha(0f).setStartDelay(0).setDuration(LIVE_OUT_MS).setInterpolator(EASE).start();
        }
        final TvView v = live;
        final FrameLayout frame = liveFrame;
        live = null;
        liveFrame = null;
        ImageView cover = liveCover;
        liveCover = null;
        Runnable done = () -> {
            try { v.reset(); } catch (Exception ignored) {}
            host.removeView(frame);
            stopping = false;
            if (then != null) then.run();
        };
        if (cover == null) {
            done.run();
            return;
        }
        cover.animate().cancel();
        cover.animate().alpha(1f).setDuration(LIVE_OUT_MS).setInterpolator(EASE).withEndAction(done).start();
    }

    private Bitmap backdrop(int x, int y, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (a.wall == null) {
            out.eraseColor(Color.BLACK);
            return out;
        }
        Canvas c = new Canvas(out);
        float k = heroW / (float) a.wall.getWidth();
        c.translate(-x, -y);
        c.scale(k, k);
        c.drawBitmap(a.wall, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private Bitmap corners(int x, int y, int w, int h, float corner) {
        Bitmap out = backdrop(x, y, w, h);
        Canvas c = new Canvas(out);
        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        c.drawPath(Glass.squircle(w, h, corner), hole);
        Glass.Edge edge = new Glass.Edge(corner, Math.max(1, a.px(1)));
        edge.setBounds(0, 0, w, h);
        edge.draw(c);
        return out;
    }

    void stop() {
        hide();
    }

    private void startSlides(String key) {
        loop++;
        slide = 0;
        if (host.getAlpha() < 0.99f) {
            for (ImageView v : art) {
                v.animate().cancel();
                v.setImageDrawable(null);
                v.setAlpha(0f);
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
            for (View tv : new View[] {name, detail}) {
                tv.animate().cancel();
                tv.setAlpha(0f);
            }
            host.animate().cancel();
            host.setAlpha(1f);
        }
        nextSlide(key, loop);
    }

    private void nextSlide(final String key, final int gen) {
        if (!key.equals(current) || gen != loop) return;
        List<Title> list = titles.get(key);
        if (list == null || list.isEmpty()) return;
        final Title t = list.get(slide % list.size());
        slide++;
        new Thread(() -> {
            final Bitmap b = image(t.image);
            ui.post(() -> {
                if (!key.equals(current) || gen != loop) return;
                if (b != null) crossfade(b, t);
                ui.postDelayed(() -> nextSlide(key, gen), SLIDE_MS);
            });
        }).start();
    }

    private void crossfade(Bitmap b, Title t) {
        final ImageView old = art[front];
        front = 1 - front;
        ImageView v = art[front];
        v.animate().cancel();
        v.setImageBitmap(b);
        v.setAlpha(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        host.removeView(v);
        host.addView(v, 1, new FrameLayout.LayoutParams(heroW, heroH));
        v.animate().alpha(1f).setDuration(CROSS_MS).setInterpolator(EASE).withEndAction(() -> old.setAlpha(0f)).start();
        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(SLIDE_MS + CROSS_MS).setInterpolator(new LinearInterpolator()).start();
        for (View tv : new View[] {name, detail}) {
            tv.animate().cancel();
            tv.setAlpha(0f);
            tv.setTranslationY(a.px(12));
        }
        name.setText(t.name);
        detail.setText(t.detail);
        name.animate().alpha(1f).translationY(0).setStartDelay(200).setDuration(FADE_MS).setInterpolator(EASE).start();
        detail.animate().alpha(1f).translationY(0).setStartDelay(260).setDuration(FADE_MS).setInterpolator(EASE).start();
        host.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(EASE).start();
    }

    private Bitmap image(String url) {
        File f = download(url);
        if (f == null) return null;
        Bitmap src = BitmapFactory.decodeFile(f.getPath());
        return src == null ? null : fade(src);
    }

    private File download(String url) {
        File dir = new File(a.getCacheDir(), "previews");
        dir.mkdirs();
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".jpg");
        try {
            if (!f.exists()) {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    con.disconnect();
                }
            }
            return f;
        } catch (Exception e) {
            f.delete();
            return null;
        }
    }

    private Bitmap fade(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(heroW, heroH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float k = Math.max((float) heroW / src.getWidth(), (float) heroH / src.getHeight());
        int dw = Math.round(src.getWidth() * k), dh = Math.round(src.getHeight() * k);
        c.drawBitmap(src, null, new android.graphics.Rect((heroW - dw) / 2, 0, (heroW + dw) / 2, dh),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, 0, heroW * 0.6f, 0, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, heroW, heroH, shade);
        Paint mask = new Paint();
        mask.setShader(new LinearGradient(0, heroH * 0.45f, 0, heroH * 0.88f, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(0, 0, heroW, heroH, mask);
        return out;
    }

    private void load(final String pkg) {
        final String key = tmdbKey();
        final Integer provider = TMDB_PROVIDERS.get(pkg);
        if (key == null || provider == null || !loading.add(pkg)) return;
        fetched.put(pkg, System.currentTimeMillis());
        final String service = SERVICE_NAMES.get(pkg);
        new Thread(() -> {
            java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long now = System.currentTimeMillis(), dayMs = 24L * 3600_000;
            String movieSince = day.format(new java.util.Date(now - 365 * dayMs));
            String tvSince = day.format(new java.util.Date(now - 730 * dayMs));
            List<Title> movies = fetch(key, "movie", provider, service, "&primary_release_date.gte=" + movieSince);
            List<Title> shows = fetch(key, "tv", provider, service, "&first_air_date.gte=" + tvSince);
            final List<Title> out = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                if (i < movies.size()) out.add(movies.get(i));
                if (i < shows.size()) out.add(shows.get(i));
            }
            for (int i = 0; i < out.size() && i < 2; i++) download(out.get(i).image);
            ui.post(() -> {
                loading.remove(pkg);
                if (out.isEmpty()) {
                    fetched.remove(pkg);
                    return;
                }
                boolean waiting = !titles.containsKey(pkg) || titles.get(pkg).isEmpty();
                titles.put(pkg, out);
                if (pkg.equals(current) && waiting) startSlides(pkg);
            });
        }).start();
    }

    private static List<Title> fetch(String key, String kind, int provider, String service, String since) {
        List<Title> out = new ArrayList<>();
        try {
            boolean token = key.length() > 40;
            String url = "https://api.themoviedb.org/3/discover/" + kind + "?watch_region=BR&with_watch_providers="
                    + provider + "&sort_by=popularity.desc&language=pt-BR&vote_count.gte=20" + since
                    + (token ? "" : "&api_key=" + key);
            JSONArray results = new JSONObject(get(url, token ? key : null)).getJSONArray("results");
            boolean tv = kind.equals("tv");
            for (int i = 0; i < results.length() && out.size() < 3; i++) {
                JSONObject r = results.getJSONObject(i);
                String backdrop = r.optString("backdrop_path", "");
                if (backdrop.isEmpty() || "null".equals(backdrop)) continue;
                String date = r.optString(tv ? "first_air_date" : "release_date", "");
                String year = date.length() >= 4 ? " · " + date.substring(0, 4) : "";
                out.add(new Title(r.optString(tv ? "name" : "title"),
                        "Em alta na " + service + " · " + (tv ? "Série" : "Filme") + year,
                        "https://image.tmdb.org/t/p/w1280" + backdrop));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String tmdbKey() {
        File f = new File(a.getExternalFilesDir(null), "tmdb.txt");
        if (!f.exists()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[1024];
            int n = in.read(b);
            String k = n > 0 ? new String(b, 0, n, "UTF-8").trim() : "";
            return k.isEmpty() ? null : k;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer " + bearer);
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }
}
