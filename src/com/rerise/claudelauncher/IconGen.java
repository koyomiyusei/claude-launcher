package com.rerise.claudelauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;

public class IconGen {

    /** ホーム画面に置くアダプティブアイコン用（中央66%がセーフゾーン） */
    public static Bitmap adaptive(String label, int color) {
        int size = 432;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(color);
        drawLabel(canvas, label, size, size * 0.30f);
        return bmp;
    }

    /** アプリ内の一覧に出す小さい丸アイコン用 */
    public static Bitmap circle(String label, int color, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        drawLabel(canvas, label, size, size * 0.42f);
        return bmp;
    }

    private static void drawLabel(Canvas canvas, String label, int size, float textSize) {
        if (label == null) label = "";
        label = label.trim();
        if (label.isEmpty()) label = "C";
        if (label.codePointCount(0, label.length()) > 2) {
            label = label.substring(0, label.offsetByCodePoints(0, 2));
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(label.codePointCount(0, label.length()) > 1 ? textSize * 0.72f : textSize);
        Rect bounds = new Rect();
        p.getTextBounds(label, 0, label.length(), bounds);
        canvas.drawText(label, size / 2f, size / 2f + bounds.height() / 2f, p);
    }

    public static Drawable dot(int color) {
        ShapeDrawable d = new ShapeDrawable(new OvalShape());
        d.getPaint().setColor(color);
        return d;
    }
}
