package com.ansub.tvhome;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

final class InfoCards {
    private static final String CITY = "Rio de Janeiro";
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=-22.91&longitude=-43.17"
            + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code"
            + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=America%2FSao_Paulo&forecast_days=2";
    private static final String CRYPTO_URL =
            "https://api.binance.com/api/v3/ticker/24hr?symbols=%5B%22BTCBRL%22,%22ETHBRL%22,%22SOLBRL%22%5D";
    private static final String USD_URL = "https://economia.awesomeapi.com.br/json/last/USD-BRL";
    private static final long REFRESH_MS = 10 * 60_000;

    private final MainActivity a;
    private FrameLayout weatherBox, quotesBox;
    private JSONObject weather, usd;
    private JSONArray crypto;
    private long fetchedAt;

    InfoCards(MainActivity a) {
        this.a = a;
    }

    void attach(FrameLayout weatherBox, FrameLayout quotesBox) {
        this.weatherBox = weatherBox;
        this.quotesBox = quotesBox;
        fillWeather();
        fillQuotes();
    }

    void refresh() {
        if (System.currentTimeMillis() - fetchedAt < REFRESH_MS) return;
        fetchedAt = System.currentTimeMillis();
        new Thread(() -> {
            JSONObject w = null, u = null;
            JSONArray c = null;
            try { w = new JSONObject(get(WEATHER_URL)); } catch (Exception ignored) {}
            try { c = new JSONArray(get(CRYPTO_URL)); } catch (Exception ignored) {}
            try { u = new JSONObject(get(USD_URL)).getJSONObject("USDBRL"); } catch (Exception ignored) {}
            final JSONObject fw = w, fu = u;
            final JSONArray fc = c;
            a.runOnUiThread(() -> {
                if (a.isDestroyed()) return;
                if (fw == null || fc == null || fu == null) fetchedAt = 0;
                if (fw != null) weather = fw;
                if (fc != null) crypto = fc;
                if (fu != null) usd = fu;
                fillWeather();
                fillQuotes();
            });
        }).start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    private static String weatherIcon(int code, boolean day) {
        if (code == 0) return day ? "sun" : "moon";
        if (code <= 2) return day ? "cloud-sun" : "cloud-moon";
        if (code == 3) return "cloud";
        if (code <= 48) return "cloud-fog";
        if (code <= 57) return "cloud-drizzle";
        if (code <= 67 || (code >= 80 && code <= 82)) return "cloud-rain";
        if (code <= 86) return "cloud-snow";
        return "cloud-lightning";
    }

    private static String weatherName(int code) {
        if (code == 0) return "Céu limpo";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code <= 48) return "Neblina";
        if (code <= 57) return "Garoa";
        if (code <= 67 || (code >= 80 && code <= 82)) return "Chuva";
        if (code <= 86) return "Neve";
        return "Tempestade";
    }

    private static String degrees(double t) {
        return Math.round(t) + "°";
    }

    private static String clockOf(String iso) {
        String hm = iso.substring(11, 16);
        return hm.startsWith("0") ? hm.substring(1) : hm;
    }

    private void put(FrameLayout box, View v, int x, int y, int w, int h) {
        box.addView(v, MainActivity.at(a.px(x), a.px(y), w < 0 ? w : a.px(w), h < 0 ? h : a.px(h)));
    }

    private ImageView icon(String name) {
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(a.icon(name, LineIcon.PICTURE));
        return iv;
    }

    private TextView label(String t, int size, int color, Typeface tf) {
        TextView v = a.text(a.px(size), color, tf);
        v.setText(t);
        v.setSingleLine(true);
        return v;
    }

    private void fillWeather() {
        if (weatherBox == null) return;
        weatherBox.removeAllViews();
        put(weatherBox, label(CITY, 26, 0xB3FFFFFF, MainActivity.semibold), 40, 32, -2, -2);
        if (weather == null) {
            put(weatherBox, label("Carregando…", 30, 0x80FFFFFF, MainActivity.regular), 40, 90, -2, -2);
            return;
        }
        try {
            JSONObject cur = weather.getJSONObject("current"), d = weather.getJSONObject("daily");
            JSONObject h = weather.getJSONObject("hourly");
            int code = cur.getInt("weather_code");
            boolean day = cur.optInt("is_day", 1) == 1;
            put(weatherBox, icon(weatherIcon(code, day)), 40, 108, 84, 84);
            put(weatherBox, label(degrees(cur.getDouble("temperature_2m")), 120, Color.WHITE, MainActivity.light), 140, 72, -2, -2);
            put(weatherBox, label(weatherName(code), 32, Color.WHITE, MainActivity.regular), 40, 226, -2, -2);
            put(weatherBox, label("Máx. " + degrees(d.getJSONArray("temperature_2m_max").getDouble(0))
                    + "   Mín. " + degrees(d.getJSONArray("temperature_2m_min").getDouble(0)), 24, 0x99FFFFFF,
                    MainActivity.regular), 40, 278, -2, -2);
            View line = new View(a);
            line.setBackgroundColor(0x26FFFFFF);
            put(weatherBox, line, 440, 40, 1, 292);

            JSONArray times = h.getJSONArray("time"), temps = h.getJSONArray("temperature_2m"),
                    codes = h.getJSONArray("weather_code");
            String now = cur.getString("time").substring(0, 13);
            int start = 0;
            for (int i = 0; i < times.length(); i++) if (times.getString(i).startsWith(now)) { start = i + 1; break; }
            for (int k = 0; k < 6 && start + k < times.length(); k++) {
                int i = start + k, hour = Integer.parseInt(times.getString(i).substring(11, 13));
                int cx = 480 + k * 92;
                TextView hl = label(hour + "h", 24, 0x99FFFFFF, MainActivity.regular);
                hl.setGravity(Gravity.CENTER_HORIZONTAL);
                put(weatherBox, hl, cx, 48, 84, -2);
                put(weatherBox, icon(weatherIcon(codes.getInt(i), hour >= 6 && hour < 18)), cx + 20, 100, 44, 44);
                TextView tl = label(degrees(temps.getDouble(i)), 30, Color.WHITE, MainActivity.regular);
                tl.setGravity(Gravity.CENTER_HORIZONTAL);
                put(weatherBox, tl, cx, 162, 84, -2);
            }
            put(weatherBox, icon("sunrise"), 480, 268, 32, 32);
            put(weatherBox, label("Nascer " + clockOf(d.getJSONArray("sunrise").getString(0)), 24, 0x99FFFFFF,
                    MainActivity.regular), 522, 270, -2, -2);
            put(weatherBox, icon("sunset"), 720, 268, 32, 32);
            put(weatherBox, label("Pôr do sol " + clockOf(d.getJSONArray("sunset").getString(0)), 24, 0x99FFFFFF,
                    MainActivity.regular), 762, 270, -2, -2);
        } catch (Exception ignored) {}
    }

    private void fillQuotes() {
        if (quotesBox == null) return;
        quotesBox.removeAllViews();
        put(quotesBox, label("Cotações", 26, 0xB3FFFFFF, MainActivity.semibold), 36, 32, -2, -2);
        TextView note = label("variação em 24 h", 20, 0x80FFFFFF, MainActivity.regular);
        note.setGravity(Gravity.END);
        put(quotesBox, note, 330, 36, 298, -2);
        String[] names = {"Bitcoin", "Ethereum", "Solana", "Dólar"}, marks = {"₿", "Ξ", "◎", "$"};
        double[] price = new double[4], pct = new double[4];
        boolean[] ok = new boolean[4];
        try {
            for (int i = 0; crypto != null && i < crypto.length() && i < 3; i++) {
                JSONObject o = crypto.getJSONObject(i);
                price[i] = o.getDouble("lastPrice");
                pct[i] = o.getDouble("priceChangePercent");
                ok[i] = true;
            }
            if (usd != null) {
                price[3] = usd.getDouble("bid");
                pct[3] = usd.getDouble("pctChange");
                ok[3] = true;
            }
        } catch (Exception ignored) {}
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        for (int i = 0; i < 4; i++) {
            int y = 92 + i * 66;
            put(quotesBox, label(marks[i], 28, 0x99FFFFFF, MainActivity.regular), 36, y + 2, -2, -2);
            put(quotesBox, label(names[i], 32, Color.WHITE, MainActivity.regular), 80, y, -2, -2);
            if (!ok[i]) continue;
            int digits = price[i] >= 10000 ? 0 : 2;
            nf.setMinimumFractionDigits(digits);
            nf.setMaximumFractionDigits(digits);
            TextView pv = label("R$ " + nf.format(price[i]), 30, Color.WHITE, MainActivity.regular);
            pv.setFontFeatureSettings("tnum");
            pv.setGravity(Gravity.END);
            put(quotesBox, pv, 250, y + 2, 260, -2);
            boolean up = pct[i] >= 0;
            nf.setMinimumFractionDigits(1);
            nf.setMaximumFractionDigits(1);
            TextView badge = label((up ? "+" : "−") + nf.format(Math.abs(pct[i])) + "%", 22,
                    up ? 0xFF5BD47A : 0xFFFF7A70, MainActivity.semibold);
            badge.setGravity(Gravity.CENTER);
            badge.setFontFeatureSettings("tnum");
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(a.px(10));
            bg.setColor(up ? 0x2934C759 : 0x29FF453A);
            badge.setBackground(bg);
            put(quotesBox, badge, 528, y + 4, 100, 40);
        }
    }
}
