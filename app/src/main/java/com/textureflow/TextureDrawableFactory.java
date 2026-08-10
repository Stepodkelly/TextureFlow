package com.textureflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

import java.io.IOException;
import java.io.InputStream;

public final class TextureDrawableFactory {
    private TextureDrawableFactory() {
    }

    public static Drawable texturedField(Context context, float radius) {
        return new TexturedSurfaceDrawable(loadTexture(context), radius,
                Color.argb(188, 248, 248, 243), Color.argb(156, 139, 149, 140), 134);
    }

    public static Drawable quietPanel(Context context, float radius) {
        return new TexturedSurfaceDrawable(loadTexture(context), radius,
                Color.argb(146, 242, 243, 237), Color.argb(96, 132, 142, 134), 104);
    }

    public static Drawable emphasizedPanel(Context context, float radius, int accentColor) {
        return new TexturedSurfaceDrawable(loadTexture(context), radius,
                Color.argb(174, 247, 247, 241), withAlpha(accentColor, 174), 116);
    }

    public static Drawable glassButton(Context context, float radius, int accentColor) {
        int stroke = Math.max(1, Math.round(context.getResources().getDisplayMetrics().density));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},
                glassLayer(radius, Color.argb(70, 242, 243, 239), Color.argb(55, 82, 88, 84), stroke));
        states.addState(new int[]{android.R.attr.state_pressed},
                glassLayer(radius, Color.argb(188, 255, 255, 255), withAlpha(accentColor, 230), stroke * 2));
        states.addState(new int[]{android.R.attr.state_focused},
                glassLayer(radius, Color.argb(164, 255, 255, 255), withAlpha(accentColor, 242), stroke * 2));
        states.addState(new int[]{android.R.attr.state_hovered},
                glassLayer(radius, Color.argb(152, 255, 255, 255), withAlpha(accentColor, 210), stroke));
        states.addState(new int[]{},
                glassLayer(radius, Color.argb(126, 255, 255, 255), Color.argb(136, 255, 255, 255), stroke));
        return states;
    }

    public static Drawable glassButton(float radius) {
        return glassLayer(radius, Color.argb(126, 255, 255, 255),
                Color.argb(136, 255, 255, 255), 1);
    }

    private static Drawable glassLayer(float radius, int bodyColor, int strokeColor, int strokeWidth) {
        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        brighten(bodyColor, 24),
                        bodyColor,
                        Color.argb(Math.max(42, Color.alpha(bodyColor) - 46), 92, 108, 99)
                });
        body.setCornerRadius(radius);
        body.setStroke(strokeWidth, strokeColor);

        GradientDrawable innerLight = new GradientDrawable();
        innerLight.setColor(Color.TRANSPARENT);
        innerLight.setCornerRadius(Math.max(0f, radius - 2f));
        innerLight.setStroke(1, Color.argb(76, 255, 255, 255));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{body, innerLight});
        layers.setLayerInset(1, 2, 2, 2, 2);
        return layers;
    }

    private static Bitmap loadTexture(Context context) {
        try (InputStream stream = context.getAssets().open("moth-texture.jpg")) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException ignored) {
            Bitmap fallback = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            fallback.eraseColor(Color.rgb(222, 224, 215));
            return fallback;
        }
    }

    private static int brighten(int color, int amount) {
        return Color.argb(Color.alpha(color),
                Math.min(255, Color.red(color) + amount),
                Math.min(255, Color.green(color) + amount),
                Math.min(255, Color.blue(color) + amount));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static final class TexturedSurfaceDrawable extends Drawable {
        private final Paint texturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final float radius;

        TexturedSurfaceDrawable(Bitmap texture, float radius, int veilColor, int strokeColor, int textureAlpha) {
            this.radius = radius;
            texturePaint.setShader(new BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            texturePaint.setAlpha(textureAlpha);
            veilPaint.setColor(veilColor);
            strokePaint.setColor(strokeColor);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.5f);
        }

        @Override
        protected void onBoundsChange(Rect rect) {
            bounds.set(rect.left + 0.75f, rect.top + 0.75f, rect.right - 0.75f, rect.bottom - 0.75f);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(bounds, radius, radius, texturePaint);
            canvas.drawRoundRect(bounds, radius, radius, veilPaint);
            canvas.drawRoundRect(bounds, radius, radius, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            veilPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            veilPaint.setColorFilter(colorFilter);
            texturePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
