package com.ansub.tvhome;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Open {
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    private Open() {}

    static void start(Context c, Intent i, View from) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (from != null && from.isAttachedToWindow() && from.getWidth() > 0)
                c.startActivity(i, ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle());
            else c.startActivity(i);
        } catch (Exception ignored) {}
    }

    static void input(Context c, String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        ResolveInfo def = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(pm) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    static List<TvInputInfo> hdmiInputs(Context c, int max) {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList())
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(c)) out.add(in);
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        SharedPreferences prefs = c.getSharedPreferences("config", 0);
        String last = prefs.getString("hdmi", null);
        TvInputInfo live = null, remembered = null;
        for (TvInputInfo in : out) {
            if (live == null && tim.getInputState(in.getId()) == TvInputManager.INPUT_STATE_CONNECTED) live = in;
            if (in.getId().equals(last)) remembered = in;
        }
        if (live != null && !live.getId().equals(last)) prefs.edit().putString("hdmi", live.getId()).apply();
        TvInputInfo first = live != null ? live : remembered;
        if (first != null) {
            out.remove(first);
            out.add(0, first);
        }
        return out.size() > max ? out.subList(0, max) : out;
    }

    static String inputLabel(Context c, TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(c);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(c);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    static void network(Context c) {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                c.startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    static List<Intent> quickIntents(Context c, String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES"));
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT"));
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = c.getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    static void quick(Context c, String name) {
        for (Intent i : quickIntents(c, name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) c.startService(i);
                else c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    static Drawable lucide(Context c, String name) {
        File f = new File(c.getExternalFilesDir("icons"), name + ".png");
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (b == null) return null;
        b.setHasMipMap(true);
        BitmapDrawable d = new BitmapDrawable(c.getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }
}
