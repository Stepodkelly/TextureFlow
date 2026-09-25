package com.textureflow.ui.kit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.textureflow.ui.HapticTextureEngine;
import com.textureflow.ui.MothMarketTheme;
import com.textureflow.ui.TextureDrawableFactory;

/** Shared Moth Market widgets and layout helpers used by every page controller. */
public final class UiKit {
    public static final int INK = MothMarketTheme.INK;
    public static final int MUTED = MothMarketTheme.MUTED;
    public static final int TEAL = Color.rgb(38, 118, 110);
    public static final int AMBER = Color.rgb(178, 124, 52);
    public static final int CRIMSON = Color.rgb(142, 61, 58);
    public static final int TRANSPARENT = Color.TRANSPARENT;

    public interface ScrollHook {
        void onScroll(int scrollY);
    }

    private final Activity activity;
    private final HapticTextureEngine textureEngine;
    private ScrollHook scrollHook;

    public UiKit(Activity activity, HapticTextureEngine textureEngine) {
        this.activity = activity;
        this.textureEngine = textureEngine;
    }

    public Activity activity() {
        return activity;
    }

    public HapticTextureEngine textureEngine() {
        return textureEngine;
    }

    public void setScrollHook(ScrollHook scrollHook) {
        this.scrollHook = scrollHook;
    }

    public FrameLayout scrollPage(LinearLayout content) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setClipChildren(false);
        frame.setBackgroundColor(TRANSPARENT);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setBackgroundColor(TRANSPARENT);
        scroll.setPadding(dp(4), dp(4), dp(4), dp(20));
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        textureEngine.attachScrollTexture(scroll, scrollY -> {
            if (scrollHook != null) scrollHook.onScroll(scrollY);
        });
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, matchFrame());
        return frame;
    }

    public LinearLayout pageColumn() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, dp(18));
        layout.setBackgroundColor(TRANSPARENT);
        return layout;
    }

    public LinearLayout surface(float radius) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(17), dp(18), dp(17));
        panel.setBackground(TextureDrawableFactory.quietPanel(activity, radius));
        return panel;
    }

    public Button button(String label, int accent) {
        Button button = compactButton(label, accent);
        button.setMinHeight(dp(54));
        button.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        button.setLayoutParams(params);
        return button;
    }

    public Button compactButton(String label, int accent) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setBackground(TextureDrawableFactory.glassButton(activity, dp(24), accent));
        textureEngine.attachGlassControl(button);
        return button;
    }

    public ImageButton circleIconButton(int iconRes) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(iconRes);
        button.setBackground(MothMarketTheme.circle(MothMarketTheme.CHIP));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        textureEngine.attachGlassControl(button);
        return button;
    }

    public Switch settingSwitch(String label) {
        Switch control = new Switch(activity);
        control.setText(label);
        control.setTextSize(16);
        control.setTextColor(INK);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinHeight(dp(52));
        control.setPadding(dp(2), dp(4), dp(2), dp(4));
        return control;
    }

    public EditText setupField(String hint, String value, int inputType) {
        EditText field = new EditText(activity);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setText(value);
        return field;
    }

    public TextView sectionHeading(String value) {
        TextView heading = text(value, 14, MUTED, true);
        heading.setAllCaps(true);
        heading.setAccessibilityHeading(true);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        heading.setLayoutParams(params);
        return heading;
    }

    public TextView value(String value) {
        return text(value, 18, INK, true);
    }

    public TextView supportingValue(String value) {
        return text(value, 14, MUTED, false);
    }

    public TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    public FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    public LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    public LinearLayout.LayoutParams wideWithTop(int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, top, 0, 0);
        return params;
    }

    public LinearLayout.LayoutParams wideWithBottom(int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, bottom);
        return params;
    }

    public LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, top, 0, 0);
        return params;
    }

    public LinearLayout.LayoutParams narrowStart() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    public FrameLayout.LayoutParams navigationParams() {
        // WRAP_CONTENT so icon + "chat"/"flow" captions are not clipped.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(18), 0, dp(18), dp(18));
        return params;
    }

    public LinearLayout.LayoutParams navItemParams() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
