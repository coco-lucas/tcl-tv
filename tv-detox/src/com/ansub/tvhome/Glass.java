package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class Glass {
    private Glass() {}

    static Bitmap frosted(Bitmap wall, int screenW, int x, int y, int w, int h, int radius, float dim) {
        Bitmap back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(back);
        float k = screenW / (float) wall.getWidth();
        c.translate(-x, -y);
        c.scale(k, k);
        c.drawBitmap(wall, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] px = new int[w * h];
        back.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boxBlur(px, w, h, radius, true);
            boxBlur(px, w, h, radius, false);
        }
        if (dim < 1f) for (int i = 0; i < px.length; i++) {
            int col = px[i];
            px[i] = 0xFF000000 | (int) (((col >> 16) & 255) * dim) << 16 | (int) (((col >> 8) & 255) * dim) << 8
                    | (int) ((col & 255) * dim);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private static void boxBlur(int[] px, int w, int h, int r, boolean rows) {
        int lines = rows ? h : w, len = rows ? w : h, n = 2 * r + 1;
        int[] line = new int[len];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = px[rows ? l * w + i : i * w + l];
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = line[Math.min(len - 1, Math.max(0, i))];
                sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                px[rows ? l * w + i : i * w + l] = 0xFF000000 | (sr / n) << 16 | (sg / n) << 8 | (sb / n);
                int in = line[Math.min(len - 1, i + r + 1)], out = line[Math.max(0, i - r)];
                sr += ((in >> 16) & 255) - ((out >> 16) & 255);
                sg += ((in >> 8) & 255) - ((out >> 8) & 255);
                sb += (in & 255) - (out & 255);
            }
        }
    }

    static Path squircle(float w, float h, float c) {
        c = Math.min(c, Math.min(w, h) / 2);
        float d = c * 0.33f;
        Path p = new Path();
        p.moveTo(c, 0);
        p.lineTo(w - c, 0);
        p.cubicTo(w - d, 0, w, d, w, c);
        p.lineTo(w, h - c);
        p.cubicTo(w, h - d, w - d, h, w - c, h);
        p.lineTo(c, h);
        p.cubicTo(d, h, 0, h - d, 0, h - c);
        p.lineTo(0, c);
        p.cubicTo(0, d, d, 0, c, 0);
        p.close();
        return p;
    }

    static final class Edge extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float corner, stroke;
        private Path path;
        private int pathW, pathH;

        Edge(float corner, float stroke) {
            this.corner = corner;
            this.stroke = stroke;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (path == null || pathW != b.width() || pathH != b.height()) {
                pathW = b.width();
                pathH = b.height();
                path = squircle(pathW - stroke, pathH - stroke, corner - stroke / 2);
                paint.setShader(new LinearGradient(0, 0, 0, pathH, 0x4DFFFFFF, 0x17FFFFFF, Shader.TileMode.CLAMP));
            }
            c.save();
            c.translate(b.left + stroke / 2, b.top + stroke / 2);
            c.drawPath(path, paint);
            c.restore();
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
