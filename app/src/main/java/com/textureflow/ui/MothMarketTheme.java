package com.textureflow.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

/** Soft Moth Market surfaces matching the Figma Ecommerce chat-list frame. */
public final class MothMarketTheme {
    /** Figma canvas fill */
    public static final int BG = Color.parseColor("#E6EBF2");
    public static final int SURFACE = Color.parseColor("#EEF2F7");
    public static final int SURFACE_RAISED = Color.parseColor("#F7F9FC");
    public static final int LOGO_WELL = Color.parseColor("#D8DEE8");
    public static final int INK = Color.parseColor("#1A1F27");
    public static final int MUTED = Color.parseColor("#6B7585");
    public static final int SEARCH = Color.parseColor("#D3DAE5");
    public static final int CHIP = Color.parseColor("#E2E7EF");
    public static final int NAV = Color.parseColor("#D0D7E1");
    public static final int NAV_SELECTED = Color.parseColor("#E9EEF5");
    public static final int PLACEHOLDER = Color.parseColor("#D9E0EA");
    public static final int BUBBLE_IN = Color.parseColor("#FFFFFF");
    public static final int BUBBLE_OUT = Color.parseColor("#DCF8C6");

    private MothMarketTheme() {}

    public static int glowForPackage(String packageName, String appLabel) {
        String hay = ((packageName == null ? "" : packageName) + " "
                + (appLabel == null ? "" : appLabel)).toLowerCase();
        if (hay.contains("whatsapp")) return Color.parseColor("#25D366");
        if (hay.contains("telegram") || hay.contains("org.telegram")) return Color.parseColor("#229ED9");
        if (hay.contains("instagram")) return Color.parseColor("#C13584");
        if (hay.contains("signal")) return Color.parseColor("#3A76F0");
        if (hay.contains("messenger") || hay.contains("facebook.orca")) return Color.parseColor("#0084FF");
        if (hay.contains("sms") || hay.contains("mms") || hay.contains("messaging")
                || hay.contains("com.google.android.apps.messaging")) {
            return Color.parseColor("#34C759");
        }
        return Color.parseColor("#9AA3B2");
    }

    public static GradientDrawable roundRect(int fill, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable circle(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    /** Soft card with a faint app-colored outer glow. */
    public static LayerDrawable glowingCard(Context context, int glowColor) {
        GradientDrawable glow = roundRect(withAlpha(glowColor, 100), 30f, context);
        GradientDrawable face = roundRect(SURFACE_RAISED, 28f, context);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{glow, face});
        // Keep the face nearly flush with the view edge so overlap math matches what you see.
        int inset = dp(context, 2);
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    public static GradientDrawable quietCard(Context context) {
        return roundRect(SURFACE, 28f, context);
    }

    public static GradientDrawable placeholderSlot(Context context) {
        return roundRect(PLACEHOLDER, 28f, context);
    }

    public static StateListDrawable navPill(Context context, boolean selected) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable on = roundRect(NAV_SELECTED, 18f, context);
        GradientDrawable off = roundRect(Color.TRANSPARENT, 18f, context);
        if (selected) {
            states.addState(new int[]{}, on);
        } else {
            states.addState(new int[]{android.R.attr.state_selected}, on);
            states.addState(new int[]{}, off);
        }
        return states;
    }

    public static void applyGlow(View view, Context context, int glowColor) {
        view.setBackground(glowingCard(context, glowColor));
        view.setElevation(dp(context, 3));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
