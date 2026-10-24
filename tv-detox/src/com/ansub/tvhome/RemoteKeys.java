package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class RemoteKeys extends AccessibilityService {
    // TCL's own key codes, from /tclconfig/.../input_key/shortcutkey.tkl on the TV.
    private static final SparseArray<String> APP_KEYS = new SparseArray<>();
    static {
        APP_KEYS.put(4062, "com.netflix.ninja");
        APP_KEYS.put(4099, "com.amazon.amazonvideo.livingroom");
        APP_KEYS.put(4141, "com.disney.disneyplus");
        APP_KEYS.put(4097, "com.globo.globotv");
        APP_KEYS.put(KeyEvent.KEYCODE_BUTTON_3, "com.google.android.youtube.tv");
        APP_KEYS.put(4001, "com.tcl.tv");
    }

    private static final int TCL_QUICK_SETTINGS = 4514, TCL_GUIDE = 4001;
    private String foreground = "";

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == TCL_GUIDE && "com.tcl.tv".equals(foreground)) return false;
        String pkg = APP_KEYS.get(code);
        boolean quick = code == KeyEvent.KEYCODE_SETTINGS || code == TCL_QUICK_SETTINGS;
        boolean settings = code == KeyEvent.KEYCODE_NOTIFICATION;
        if (pkg == null && !quick && !settings) return false;
        if (e.getAction() != KeyEvent.ACTION_UP) return true;
        if (pkg != null) {
            Intent i = MainActivity.launchIntent(getPackageManager(), pkg);
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        if (settings) startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        else startActivity(new Intent(this, QuickActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null)
            foreground = e.getPackageName().toString();
    }
    @Override public void onInterrupt() {}
}
