package com.autoradio.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Generates the iOS-style backgrounds in code (fewer resource files, same look everywhere). */
final class Ui {

    // iOS dark palette (top, bottom) for app icons
    static final int[] BLUE = {0xFF6AC4FF, 0xFF1A8CFF};
    static final int[] RED = {0xFFFC5C7D, 0xFFFA233B};
    static final int[] GREEN = {0xFF5BE584, 0xFF28C840};
    static final int[] DARK = {0xFF3A3A3C, 0xFF1C1C1E};
    static final int[] GRAPHITE = {0xFF3A3A3C, 0xFF2C2C2E};
    static final int[] GRAY = {0xFF8E8E93, 0xFF636366};
    static final int[] ORANGE = {0xFFFFB340, 0xFFFF8A00};
    static final int[] BT_BLUE = {0xFF5E9BFF, 0xFF2F6BFF};

    static final int CARD = 0xFF1C1C1E;
    static final int CARD_PRESSED = 0xFF2C2C2E;
    static final int SYSTEM_BLUE = 0xFF0A84FF;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Rounded-square app icon background with a vertical gradient (~22% corner radius, like iOS). */
    static Drawable icon(Context c, int[] colors, float sizeDp) {
        float r = dp(c, sizeDp * 0.2237f);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        normal.setCornerRadius(r);
        GradientDrawable pressed = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {darken(colors[0]), darken(colors[1])});
        pressed.setCornerRadius(r);
        return states(pressed, normal);
    }

    /** Dark card, slightly lighter while pressed. */
    static Drawable card(Context c) {
        return states(solid(CARD_PRESSED, dp(c, 18)), solid(CARD, dp(c, 18)));
    }

    /** Hero card: soft spotlight from below on the car. */
    static Drawable heroCard(Context c, int widthPx) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {0xFF2E313B, CARD});
        g.setGradientCenter(0.5f, 0.78f);
        g.setGradientRadius(Math.max(1, widthPx * 0.6f));
        g.setCornerRadius(dp(c, 18));
        return g;
    }

    static Drawable pill(Context c, int color) {
        return states(solid(darken(color), dp(c, 12)), solid(color, dp(c, 12)));
    }

    static Drawable dot(Context c, boolean active) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(active ? 0xFFFFFFFF : 0x4DFFFFFF);
        g.setSize(dp(c, 7), dp(c, 7));
        return g;
    }

    static Drawable roundedSolid(Context c, int color, float radiusDp) {
        return solid(color, dp(c, radiusDp));
    }

    private static GradientDrawable solid(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static Drawable states(Drawable pressed, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, pressed);
        s.addState(new int[0], normal);
        return s;
    }

    private static int darken(int color) {
        int a = color >>> 24;
        int r = ((color >> 16) & 0xFF) * 3 / 4;
        int g = ((color >> 8) & 0xFF) * 3 / 4;
        int b = (color & 0xFF) * 3 / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
