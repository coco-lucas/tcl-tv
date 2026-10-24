package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final List<String> HIDDEN = Arrays.asList("com.tcl.suspension", "com.tcl.tv.tclhome_passive");
    private static final String SETTINGS = "settings";
    private static final String ALL = "all";
    private static final String WALL = "wall";
    private static final String QUICK_PANEL = "quickpanel";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int TOP_UP = 6, MAX_INPUTS = 1;

    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4;

    private static final int MARGIN = 96, RADIUS = 22;
    private static final int ICON_TOP = 56, ICON_BUTTON = 72, ICON_SIZE = 36, ICON_GAP = 12;
    private static final int DOCK_SIDE = 60, DOCK_PAD = 40, DOCK_H = 176, DOCK_TOP = 731, DOCK_R = 52;
    private static final int SLOT = 220, GLYPH_W = 180, GLYPH_H = 88, LINE_GLYPH = 72;
    private static final String PANEL = "p:";
    private static final int TILE_W = 220, TILE_H = 150, TILE_GAP = 24;
    private static final float PANEL_DIM = 1f;
    private float scale = 1f;
    static final android.view.animation.PathInterpolator EASE_FOCUS =
            new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f);
    static final int FOCUS_MS = 240;
    private static final android.view.animation.PathInterpolator EASE_DRAWER =
            new android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f);
    static final float REST_SCALE = 0.92f, REST_ALPHA = 0.62f;

    static void rest(View v) {
        v.setScaleX(REST_SCALE);
        v.setScaleY(REST_SCALE);
        v.setAlpha(REST_ALPHA);
    }

    static void focus(View v, boolean has) {
        float sc = has ? 1f : REST_SCALE;
        v.animate().scaleX(sc).scaleY(sc).alpha(has ? 1f : REST_ALPHA)
                .setDuration(FOCUS_MS).setInterpolator(EASE_FOCUS).start();
    }

    private static void lockEnds(List<View> row) {
        if (row.isEmpty()) return;
        for (View v : row) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        View first = row.get(0), last = row.get(row.size() - 1);
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    static Typeface regular, semibold, light;

    private FrameLayout root;
    private int slot, dockX, dockTop;
    private FrameLayout dock;
    private TextView label;
    private boolean labelShown;
    private final List<View> chrome = new ArrayList<>();
    private final List<View> dockCards = new ArrayList<>(), topIcons = new ArrayList<>(), panelCards = new ArrayList<>();
    private int page;
    private final InfoCards info = new InfoCards(this);
    private final java.util.Map<String, Grid.Item> appItems = new java.util.HashMap<>();
    Bitmap wall;
    private final List<View> tiles = new ArrayList<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale;
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>();
    private String movingKey;
    private TextView moveHint;
    private boolean longPressed;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            appItems.clear();
        }
    };

    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        int sh = getResources().getDisplayMetrics().heightPixels;
        root = new FrameLayout(this);
        root.setClipChildren(false);
        shownLocale = java.util.Locale.getDefault();
        loadWall();

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(DOCK_SIDE), px(DOCK_TOP - 64), -2, -2));

        FrameLayout viewport = new FrameLayout(this);
        viewport.addView(root, new FrameLayout.LayoutParams(-1, 2 * sh));
        setContentView(viewport);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now != null && now.getTag() instanceof String)
                showPage(((String) now.getTag()).startsWith(PANEL) ? 1 : 0);
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) buildTiles();
        else if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String wallName = intent == null ? null : intent.getStringExtra("wall");
        if (apps == null && quick == null && wallName == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (wallName != null) e.putString("wallName", wallName.trim());
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= TOP_UP) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        refreshNetwork();
        info.refresh();
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (e.getRepeatCount() == 0) open("com.tcl.tv");
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private Drawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF15100F, 0xFF0A0809, 0xFF050405});
        GradientDrawable warm = radial(0x36FF8048, 0.08f, -0.12f, px(1250));
        GradientDrawable plum = radial(0x2EAA3C8C, 1.0f, 1.1f, px(1000));
        for (GradientDrawable g : new GradientDrawable[] {base, warm, plum}) g.setDither(true);
        return new LayerDrawable(new Drawable[] {base, warm, plum});
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    private void buildTiles() {
        loadWall();
        String want = focusedKey;
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        for (View v : chrome) root.removeView(v);
        chrome.clear();
        dockCards.clear();
        topIcons.clear();
        panelCards.clear();
        labelShown = false;
        tiles.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int sw = getResources().getDisplayMetrics().widthPixels, m = px(MARGIN);
        rowApps.addAll(appList(pm));
        List<TvInputInfo> inputs = Open.hdmiInputs(this, MAX_INPUTS);
        int n = Math.max(1, rowApps.size() + inputs.size());
        slot = px(SLOT);
        int dockW = n * slot + 2 * px(DOCK_PAD), dockH = px(DOCK_H);
        dockX = (sw - dockW) / 2;
        dockTop = px(DOCK_TOP);
        addChrome(glass(dockX, dockTop, dockW, dockH, px(DOCK_R), false), at(dockX, dockTop, dockW, dockH));

        label = text(px(40), 0xF2FFFFFF, regular);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setAlpha(0f);
        addChrome(label, at(gridX(0) + slot / 2 - px(300), dockTop + dockH + px(22), px(600), -2));
        ((FrameLayout.LayoutParams) moveHint.getLayoutParams()).topMargin = dockTop - px(64);

        int i = 0;
        for (String pkg : rowApps) addTile(pkg, labelOf(pkg), logo(pkg), i++);
        for (TvInputInfo in : inputs) addTile(in.getId(), Open.inputLabel(this, in), icon("hdmi-port", LineIcon.HDMI), i++);

        int pillH = px(72), pillW = px(236), pillX = sw - m - pillW, pillY = px(ICON_TOP);
        addChrome(profilePill(pillX, pillY, pillW, pillH), at(pillX, pillY, pillW, pillH));
        List<Integer> quick = quickList();
        int ib = px(ICON_BUTTON), ig = px(ICON_GAP), count = quick.size() + 4;
        int qx = pillX - px(28) - count * ib - (count - 1) * ig;
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), icon(QUICK_NAMES[q], QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        netIcon = addIcon(NETWORK, Strings.t("Network"), null, qx);
        qx += ib + ig;
        addIcon(WALL, "Plano de fundo", icon("image", LineIcon.PICTURE), qx);
        qx += ib + ig;
        addIcon(ALL, "Todos os apps", icon("layout-grid", LineIcon.GRID), qx);
        qx += ib + ig;
        addIcon(QUICK_PANEL, "Controle rápido", icon("settings", LineIcon.SETTINGS), qx);
        netState = -1;
        refreshNetwork();

        buildPanel(inputs);

        lockEnds(dockCards);
        lockEnds(topIcons);
        lockEnds(panelCards);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    private void addChrome(View v, FrameLayout.LayoutParams lp) {
        root.addView(v, lp);
        chrome.add(v);
    }

    private void showPage(int p) {
        if (p == page) return;
        page = p;
        root.animate().cancel();
        root.animate().translationY(p == 0 ? 0 : -getResources().getDisplayMetrics().heightPixels)
                .setDuration(420).setInterpolator(EASE_DRAWER).start();
        if (p == 1) info.refresh();
    }

    private View profilePill(int x, int y, int w, int h) {
        FrameLayout pill = glass(x, y, w, h, h / 2, false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(px(28), 0, px(8), 0);
        TextClock time = clock("h:mm a", "H:mm", px(32), Color.WHITE, semibold);
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(time, tp);
        ImageView photo = new ImageView(this);
        Drawable face = fileDrawable(new File(getExternalFilesDir(null), "profile.png"));
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setColor(0x33FFFFFF);
        photo.setBackground(round);
        photo.setClipToOutline(true);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (face != null) photo.setImageDrawable(face);
        int ps = h - px(16);
        row.addView(photo, new LinearLayout.LayoutParams(ps, ps));
        pill.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return pill;
    }

    private void buildPanel(List<TvInputInfo> inputs) {
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        int m = px(MARGIN), top = sh;

        int tw = px(TILE_W), tg = px(TILE_GAP), rowY = top + px(150);
        int x = m;
        addChrome(sectionTitle("Entradas"), at(x, top + px(92), -2, -2));
        if (launchIntent(getPackageManager(), "com.tcl.tv") != null)
            x = panelTile(PANEL + "live", "TV ao vivo", icon("tv", LineIcon.HDMI), x, rowY,
                    () -> open("com.tcl.tv"));
        for (final TvInputInfo in : inputs)
            x = panelTile(PANEL + in.getId(), Open.inputLabel(this, in), icon("hdmi-port", LineIcon.HDMI), x, rowY,
                    () -> Open.input(this, in.getId()));
        x += px(40);
        addChrome(sectionTitle("Atalhos"), at(x, top + px(92), -2, -2));
        x = panelTile(PANEL + "all", "Todos os apps", icon("layout-grid", LineIcon.GRID), x, rowY, this::showAllApps);
        if (!Open.quickIntents(this, "sound").isEmpty())
            x = panelTile(PANEL + "sound", "Som", icon("speaker", LineIcon.SOUND), x, rowY, () -> Open.quick(this, "sound"));
        if (!Open.quickIntents(this, "picture").isEmpty())
            x = panelTile(PANEL + "picture", "Imagem", icon("image", LineIcon.PICTURE), x, rowY, () -> Open.quick(this, "picture"));
        panelTile(PANEL + "settings", "Ajustes da TV", icon("settings", LineIcon.SETTINGS), x, rowY,
                () -> open(SETTINGS));

        int cardY = rowY + px(TILE_H) + px(56), cardH = px(372);
        int qw = px(664), ww = sw - 2 * m - qw - tg;
        FrameLayout wc = glass(m, cardY, ww, cardH, px(36), true);
        FrameLayout weatherBox = new FrameLayout(this);
        wc.addView(weatherBox, new FrameLayout.LayoutParams(-1, -1));
        addChrome(wc, at(m, cardY, ww, cardH));
        FrameLayout qc = glass(m + ww + tg, cardY, qw, cardH, px(36), true);
        FrameLayout quotesBox = new FrameLayout(this);
        qc.addView(quotesBox, new FrameLayout.LayoutParams(-1, -1));
        addChrome(qc, at(m + ww + tg, cardY, qw, cardH));
        info.attach(weatherBox, quotesBox);
    }

    private TextView sectionTitle(String t) {
        TextView v = text(px(28), 0xB3FFFFFF, semibold);
        v.setText(t);
        return v;
    }

    private int panelTile(String key, String name, Drawable icon, int x, int y, final Runnable action) {
        int w = px(TILE_W), h = px(TILE_H);
        FrameLayout card = glass(x, y, w, h, px(28), true);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        inner.addView(iv, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView t = text(px(26), Color.WHITE, regular);
        t.setText(name);
        t.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(14);
        inner.addView(t, lp);
        card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        rest(card);
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(name);
        card.setOnClickListener(v -> {
            launchFrom = v;
            action.run();
        });
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            focus(v, has);
        });
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        root.addView(wrap, at(x, y, w, h));
        tiles.add(card);
        panelCards.add(card);
        return x + w + px(TILE_GAP);
    }

    private void addTile(final String key, final String name, Drawable glyph, final int index) {
        FrameLayout card = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(glyph);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        boolean line = isInput(key);
        card.addView(iv, new FrameLayout.LayoutParams(px(line ? LINE_GLYPH : GLYPH_W), px(line ? LINE_GLYPH : GLYPH_H), Gravity.CENTER));
        rest(card);
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(name);
        dockCards.add(card);

        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                markFocus(rowApps.contains(key) ? rowApps.indexOf(key) : index, name);
            }
            focus(v, has);
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> {
            launchFrom = v;
            open(key);
        });
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                launchFrom = v;
                open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(slot, px(DOCK_H)));
        root.addView(wrap, at(gridX(index), gridY(index), slot, px(DOCK_H)));
        tiles.add(card);
    }

    private void markFocus(int i, String name) {
        float dx = gridX(i) - gridX(0);
        label.setText(name);
        label.animate().cancel();
        if (!labelShown) label.setTranslationX(dx);
        labelShown = true;
        label.animate().translationX(dx).alpha(1f).setDuration(FOCUS_MS).setInterpolator(EASE_FOCUS).start();
    }

    private void hideFocusMarks() {
        if (label == null) return;
        labelShown = false;
        label.animate().cancel();
        label.animate().alpha(0f).setDuration(120).setInterpolator(EASE_FOCUS).start();
    }

    Drawable icon(String name, int[] fallback) {
        Drawable d = fileDrawable(new File(getExternalFilesDir("icons"), name + ".png"));
        return d != null ? d : new LineIcon(fallback);
    }

    private Drawable fileDrawable(File f) {
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (b == null) return null;
        b.setHasMipMap(true);
        BitmapDrawable d = new BitmapDrawable(getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }

    private Drawable logo(String pkg) {
        Drawable d = fileDrawable(new File(getExternalFilesDir("logos"), pkg + ".png"));
        if (d != null) {
            d.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            return d;
        }
        try { return getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    private void loadWall() {
        File dir = getExternalFilesDir("wallpapers");
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        wall = null;
        if (fs != null && fs.length > 0) {
            Arrays.sort(fs);
            String want = getSharedPreferences(PREFS, 0).getString("wallName", null);
            File pick = fs[0];
            for (File f : fs) if (f.getName().equals(want)) pick = f;
            wall = BitmapFactory.decodeFile(pick.getPath());
        }
        getWindow().setBackgroundDrawable(wall != null ? new BitmapDrawable(getResources(), wall) : background());
    }

    private final java.util.Map<String, Drawable> thumbs = new java.util.HashMap<>();

    private Drawable wallThumb(File f) {
        Drawable d = thumbs.get(f.getName());
        if (d != null) return d;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 4;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        d = new BitmapDrawable(getResources(), b);
        thumbs.put(f.getName(), d);
        return d;
    }

    private Grid.Item appItem(String pkg) {
        Grid.Item cached = appItems.get(pkg);
        if (cached != null) return cached;
        PackageManager pm = getPackageManager();
        Drawable banner = null, icon = null;
        try {
            Intent li = launchIntent(pm, pkg);
            if (li != null) banner = pm.getActivityBanner(li);
            if (banner == null) banner = pm.getApplicationBanner(pkg);
        } catch (Exception ignored) {}
        Grid.Item it;
        if (banner != null) it = new Grid.Item(labelOf(pkg), fit(banner, px(400), px(225)), true);
        else {
            try { icon = fit(pm.getApplicationIcon(pkg), px(128), px(128)); } catch (Exception ignored) {}
            it = new Grid.Item(labelOf(pkg), icon, false);
        }
        appItems.put(pkg, it);
        return it;
    }

    static java.util.Map<String, String> names(File f) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (!f.exists()) return out;
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
            for (String line; (line = r.readLine()) != null; ) {
                int eq = line.indexOf('=');
                if (eq > 0) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void pickWall() {
        File dir = getExternalFilesDir("wallpapers");
        File[] fs = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        if (fs == null || fs.length == 0) return;
        Arrays.sort(fs);
        java.util.Map<String, String> given = names(new File(dir, "names.txt"));
        List<String> names = new ArrayList<>();
        for (File f : fs) {
            if (given.containsKey(f.getName())) {
                names.add(given.get(f.getName()));
                continue;
            }
            String n = f.getName().replaceFirst("\\.[^.]+$", "").replaceFirst("^\\d+-", "").replace('-', ' ');
            names.add(n.isEmpty() ? f.getName() : Character.toUpperCase(n.charAt(0)) + n.substring(1));
        }
        String current = getSharedPreferences(PREFS, 0).getString("wallName", fs[0].getName());
        List<Grid.Item> items = new ArrayList<>();
        int focus = 0;
        for (int i = 0; i < fs.length; i++) {
            boolean on = fs[i].getName().equals(current);
            if (on) focus = i;
            items.add(new Grid.Item(names.get(i) + (on ? "  ✓" : ""), wallThumb(fs[i]), true));
        }
        final File[] files = fs;
        Grid.show(this, "Plano de fundo", items, 4, focus, which -> {
            getSharedPreferences(PREFS, 0).edit().putString("wallName", files[which].getName()).apply();
            buildTiles();
        });
    }

    private FrameLayout glass(int x, int y, int w, int h, int r, boolean onPanel) {
        FrameLayout g = new FrameLayout(this);
        final float corner = r * 1.5f;
        g.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                if (android.os.Build.VERSION.SDK_INT >= 30) o.setPath(Glass.squircle(v.getWidth(), v.getHeight(), corner));
                else o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner / 1.5f);
            }
        });
        g.setClipToOutline(true);
        int screenY = onPanel ? y - getResources().getDisplayMetrics().heightPixels : y;
        if (wall != null) {
            ImageView back = new ImageView(this);
            back.setScaleType(ImageView.ScaleType.FIT_XY);
            back.setImageBitmap(Glass.frosted(wall, getResources().getDisplayMetrics().widthPixels, x, screenY, w, h,
                    Math.max(1, px(3)), onPanel ? PANEL_DIM : 1f));
            g.addView(back, new FrameLayout.LayoutParams(-1, -1));
        }
        View veil = new View(this);
        veil.setBackgroundColor(0x33000000);
        g.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        g.setForeground(new Glass.Edge(corner, Math.max(1, scale)));
        return g;
    }

    private ImageView addIcon(final String key, String label, Drawable icon, int x) {
        int b = px(ICON_BUTTON), size = px(ICON_SIZE);
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);
        card.setAlpha(0.6f);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                hideFocusMarks();
            }
            bg.setColor(has ? 0x24FFFFFF : Color.TRANSPARENT);
            v.animate().alpha(has ? 1f : 0.6f).setDuration(FOCUS_MS).setInterpolator(EASE_FOCUS).start();
        });
        card.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(b, b));
        root.addView(wrap, at(x, px(ICON_TOP), b, b));
        tiles.add(card);
        topIcons.add(card);
        return iv;
    }

    View launchFrom;

    private void open(String key) {
        Intent i;
        if (WALL.equals(key)) {
            pickWall();
            return;
        } else if (ALL.equals(key)) {
            showAllApps();
            return;
        } else if (SETTINGS.equals(key)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (QUICK_PANEL.equals(key)) {
            startActivity(new Intent(this, QuickActivity.class));
            return;
        } else if (isInput(key)) {
            Open.input(this, key);
            return;
        } else if (NETWORK.equals(key)) {
            Open.network(this);
            return;
        } else if (key.startsWith(QUICK)) {
            Open.quick(this, key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        View from = launchFrom;
        launchFrom = null;
        Open.start(this, i, from);
    }

    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || Open.quickIntents(this, QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        String name = state == ETHERNET ? "ethernet-port" : state == OFFLINE ? "wifi-off"
                : state == 1 ? "wifi-low" : state == 2 ? "wifi-high" : "wifi";
        Drawable lucide = fileDrawable(new File(getExternalFilesDir("icons"), name + ".png"));
        if (lucide != null) {
            netIcon.setImageDrawable(lucide);
        } else if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    interface Pick { void at(int which); }

    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(28));
        bg.setColor(0xF21C1C1E);
        bg.setStroke(Math.max(1, px(1)), 0x33FFFFFF);
        box.setBackground(bg);
        float widest = 0;
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(18));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x24FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? null : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    private TreeMap<String, String> launchableApps() {
        PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || HIDDEN.contains(p) || byLabel.containsValue(p)) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        return byLabel;
    }

    private void showAddMenu(final int replaceIndex) {
        TreeMap<String, String> byLabel = launchableApps();
        byLabel.values().removeIf(p -> rowApps.contains(p) || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE)));
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = byLabel.isEmpty() ? Strings.t("Every app is already here")
                : replaceIndex < 0 ? "Adicionar ao dock"
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        List<Grid.Item> items = new ArrayList<>();
        for (String p : pkgs) items.add(appItem(p));
        Grid.show(this, title, items, 5, 0, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private void showAllApps() {
        TreeMap<String, String> byLabel = launchableApps();
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        List<Grid.Item> items = new ArrayList<>();
        items.add(new Grid.Item("Adicionar ao dock", icon("plus", LineIcon.GRID), false));
        for (String p : pkgs) items.add(appItem(p));
        Grid.show(this, "Todos os apps", items, 5, 1, which -> {
            if (which == 0) showAddMenu(-1);
            else open(pkgs.get(which - 1));
        });
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    private int gridX(int i) {
        return dockX + px(DOCK_PAD) + i * slot;
    }

    private int gridY(int i) {
        return dockTop;
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                markFocus(to, labelOf(key));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
            }
            return true;
        }
        return true;
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void loadFonts() {
        if (regular != null) return;
        regular = inter(this, 400);
        semibold = inter(this, 600);
        light = inter(this, 300);
        if (regular != null && semibold != null && light != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Typeface inter(Context c, int weight) {
        File f = new File(c.getExternalFilesDir("fonts"), "Inter.ttf");
        if (!f.exists() || android.os.Build.VERSION.SDK_INT < 26) return null;
        try { return new Typeface.Builder(f).setFontVariationSettings("'wght' " + weight + ", 'opsz' 32").build(); }
        catch (Exception e) { return null; }
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
