package com.textureflow.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;

import com.textureflow.texture.TextureCue;

import java.io.IOException;
import java.io.InputStream;

/**
 * Flat paper base. The moth JPEG is an invisible albedo map: whiter texels catch
 * more colored icon light. No tiled watermark; cover-sampled, seam-free.
 */
public final class TextureBackgroundView extends View {
    public static final float DEFAULT_ALBEDO_STRENGTH = 3.0f;
    public static final float MAX_ALBEDO_STRENGTH = 7.5f;

    private static final float DEFAULT_CONTRAST = 1.6f;
    private static final int MAX_LIGHTS = MothAlbedoShader.MAX_LIGHTS;
    private static final int MAX_RINGS = MothAlbedoShader.MAX_RINGS;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix coverMatrix = new Matrix();
    private final Bitmap albedoBitmap;
    private final BitmapShader albedoBitmapShader;
    private final RuntimeShader runtimeShader;
    private final boolean useRuntimeShader;
    private final float[] lightX = new float[MAX_LIGHTS];
    private final float[] lightY = new float[MAX_LIGHTS];
    private final float[] lightRadius = new float[MAX_LIGHTS];
    private final float[] lightIntensity = new float[MAX_LIGHTS];
    private final float[] lightMapPower = new float[MAX_LIGHTS];
    private final int[] lightColor = new int[MAX_LIGHTS];
    private final float[] ringCx = new float[MAX_RINGS];
    private final float[] ringCy = new float[MAX_RINGS];
    private final float[] ringHalfW = new float[MAX_RINGS];
    private final float[] ringHalfH = new float[MAX_RINGS];
    private final float[] ringCorner = new float[MAX_RINGS];
    private final float[] ringReach = new float[MAX_RINGS];
    private final float[] ringIntensity = new float[MAX_RINGS];
    private final float[] ringMapPower = new float[MAX_RINGS];
    private final int[] ringColor = new int[MAX_RINGS];

    private boolean reducedTexture;
    private float textureOffset;
    private float albedoStrength = DEFAULT_ALBEDO_STRENGTH;
    private float albedoContrast = DEFAULT_CONTRAST;
    private int baseColor = MothMarketTheme.BG;
    private float cueAlpha;
    private ValueAnimator cueAnimator;
    private int cueGeneration;
    private int cueColor = Color.rgb(42, 119, 114);

    public TextureBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        basePaint.setColor(baseColor);
        albedoBitmap = loadTexture(context);
        if (albedoBitmap != null) {
            // CLAMP + identity matrix: AGSL cover UVs address bitmap pixels directly (no tile seams).
            albedoBitmapShader = new BitmapShader(
                    albedoBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            albedoBitmapShader = null;
        }

        boolean shaderOk = false;
        RuntimeShader created = null;
        if (MothAlbedoShader.isSupported() && albedoBitmapShader != null) {
            try {
                created = MothAlbedoShader.create();
                created.setInputShader("albedoMap", albedoBitmapShader);
                shaderOk = true;
            } catch (RuntimeException ignored) {
                created = null;
                shaderOk = false;
            }
        }
        runtimeShader = created;
        useRuntimeShader = shaderOk;

        cuePaint.setColor(cueColor);
        fallbackMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        // Whiteness → alpha so DST_IN keeps light only where the map is bright.
        fallbackMaskPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0.299f, 0.587f, 0.114f, 0, 0
        })));
        clearLights();
        clearRings();
    }

    public void setBaseColor(int color) {
        if (baseColor == color) return;
        baseColor = color;
        basePaint.setColor(color);
        invalidate();
    }

    /** 0 = no reflection catch; 1 = nominal; up to {@link #MAX_ALBEDO_STRENGTH}. */
    public void setAlbedoStrength(float strength) {
        float clamped = clamp(strength, 0f, MAX_ALBEDO_STRENGTH);
        if (Math.abs(clamped - albedoStrength) < 0.001f) return;
        albedoStrength = clamped;
        invalidate();
    }

    public float getAlbedoStrength() {
        return albedoStrength;
    }

    public void setAlbedoContrast(float contrast) {
        float clamped = clamp(contrast, 0.8f, 4.0f);
        if (Math.abs(clamped - albedoContrast) < 0.001f) return;
        albedoContrast = clamped;
        invalidate();
    }

    public void setReducedTexture(boolean reducedTexture) {
        if (this.reducedTexture == reducedTexture) return;
        this.reducedTexture = reducedTexture;
        invalidate();
    }

    public boolean isReducedTexture() {
        return reducedTexture;
    }

    public void setScrollOffset(int scrollY) {
        if (reducedTexture) return;
        textureOffset = -(scrollY * 0.06f);
        invalidate();
    }

    /** Place a colored light in view coordinates. Index 0..{@link #MAX_LIGHTS}-1. */
    public void setLight(int index, float x, float y, int color, float radiusPx, float intensity) {
        setLight(index, x, y, color, radiusPx, intensity, 2f);
    }

    /**
     * @param mapPower higher = catch sticks more strictly to bright map texels (rings ~3.5).
     */
    public void setLight(int index, float x, float y, int color, float radiusPx, float intensity,
            float mapPower) {
        if (index < 0 || index >= MAX_LIGHTS) return;
        lightX[index] = x;
        lightY[index] = y;
        lightColor[index] = color;
        lightRadius[index] = Math.max(1f, radiusPx);
        lightIntensity[index] = Math.max(0f, intensity);
        lightMapPower[index] = Math.max(1f, mapPower);
        invalidate();
    }

    public void clearLight(int index) {
        if (index < 0 || index >= MAX_LIGHTS) return;
        lightIntensity[index] = 0f;
        invalidate();
    }

    public void clearLights() {
        for (int i = 0; i < MAX_LIGHTS; i++) {
            lightIntensity[i] = 0f;
            lightRadius[i] = 1f;
            lightMapPower[i] = 2f;
            lightColor[i] = Color.WHITE;
            lightX[i] = 0f;
            lightY[i] = 0f;
        }
        invalidate();
    }

    /**
     * Rounded-rect ring emitter (view coords). Light wraps the full border and is
     * gated by map whiteness ({@code mapPower} higher = stricter).
     */
    public void setRing(int index, float centerX, float centerY, float halfWidth, float halfHeight,
            float cornerRadiusPx, float reachPx, int color, float intensity, float mapPower) {
        if (index < 0 || index >= MAX_RINGS) return;
        ringCx[index] = centerX;
        ringCy[index] = centerY;
        ringHalfW[index] = Math.max(1f, halfWidth);
        ringHalfH[index] = Math.max(1f, halfHeight);
        ringCorner[index] = Math.max(0f, cornerRadiusPx);
        ringReach[index] = Math.max(0.5f, reachPx);
        ringColor[index] = color;
        ringIntensity[index] = Math.max(0f, intensity);
        ringMapPower[index] = Math.max(1f, mapPower);
        invalidate();
    }

    public void clearRing(int index) {
        if (index < 0 || index >= MAX_RINGS) return;
        ringIntensity[index] = 0f;
        invalidate();
    }

    public void clearRings() {
        for (int i = 0; i < MAX_RINGS; i++) {
            ringIntensity[i] = 0f;
            ringHalfW[i] = 1f;
            ringHalfH[i] = 1f;
            ringCorner[i] = 0f;
            ringReach[i] = 1f;
            ringMapPower[i] = 4f;
            ringColor[i] = Color.WHITE;
            ringCx[i] = 0f;
            ringCy[i] = 0f;
        }
        invalidate();
    }

    public boolean usesRuntimeShader() {
        return useRuntimeShader;
    }

    public void showCue(TextureCue cue, boolean reducedMotion) {
        if (cue == TextureCue.CONTENT_MOVEMENT || cue == TextureCue.FOCUS_ENTERED) {
            return;
        }
        if (cueAnimator != null) {
            cueAnimator.cancel();
        }
        int generation = ++cueGeneration;
        cueColor = colorFor(cue);
        cuePaint.setColor(cueColor);
        // Cue feeds a map-gated light only — no full-screen wash.
        setLight(2, getWidth() * 0.5f, getHeight() * 0.35f, cueColor,
                Math.max(getWidth(), getHeight()) * 0.55f,
                reducedMotion ? 0.35f : 0.85f);
        cueAlpha = 0f;
        if (reducedMotion) {
            invalidate();
            postDelayed(() -> {
                if (generation == cueGeneration) clearLight(2);
            }, 180L);
            return;
        }
        cueAnimator = ValueAnimator.ofFloat(0.85f, 0f);
        cueAnimator.setDuration(Math.max(220L, cue.durationMs()));
        cueAnimator.addUpdateListener(animation -> {
            lightIntensity[2] = (float) animation.getAnimatedValue() * (reducedTexture ? 0.5f : 1f);
            invalidate();
        });
        cueAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (generation == cueGeneration) clearLight(2);
            }
        });
        cueAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float strength = reducedTexture ? albedoStrength * 0.35f : albedoStrength;

        if (useRuntimeShader && runtimeShader != null && albedoBitmap != null
                && albedoBitmapShader != null) {
            // Identity local matrix: AGSL computes cover UVs in bitmap pixels.
            coverMatrix.reset();
            albedoBitmapShader.setLocalMatrix(coverMatrix);
            float scale = coverScale(w, h);
            float dx = (w - albedoBitmap.getWidth() * scale) * 0.5f;
            float dy = (h - albedoBitmap.getHeight() * scale) * 0.5f + textureOffset;
            runtimeShader.setInputShader("albedoMap", albedoBitmapShader);
            runtimeShader.setFloatUniform("resolution", (float) w, (float) h);
            runtimeShader.setFloatUniform("texSize",
                    (float) albedoBitmap.getWidth(), (float) albedoBitmap.getHeight());
            runtimeShader.setFloatUniform("coverScale", scale);
            runtimeShader.setFloatUniform("coverOffset", dx, dy);
            runtimeShader.setFloatUniform("baseColor",
                    Color.red(baseColor) / 255f,
                    Color.green(baseColor) / 255f,
                    Color.blue(baseColor) / 255f);
            runtimeShader.setFloatUniform("albedoStrength", strength);
            runtimeShader.setFloatUniform("albedoContrast", albedoContrast);
            bindLight(runtimeShader, 0);
            bindLight(runtimeShader, 1);
            bindLight(runtimeShader, 2);
            bindRing(runtimeShader, 0);
            bindRing(runtimeShader, 1);
            bindRing(runtimeShader, 2);
            shaderPaint.setShader(runtimeShader);
            canvas.drawRect(0, 0, w, h, shaderPaint);
        } else {
            drawFallback(canvas, w, h, strength);
        }
    }

    /**
     * API &lt; 33: flat paper + radial lights masked by a cover-scaled luminance map
     * so catch-light still follows whiteness without tiling seams.
     */
    private void drawFallback(Canvas canvas, int w, int h, float strength) {
        canvas.drawRect(0, 0, w, h, basePaint);
        if (albedoBitmap == null) return;

        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (lightIntensity[i] <= 0.001f) continue;
            float radius = lightRadius[i];
            int color = lightColor[i];
            int alpha = Math.round(255f * clamp(lightIntensity[i] * strength * 0.45f, 0f, 0.7f));

            int save = canvas.saveLayer(0, 0, w, h, null);
            RadialGradient gradient = new RadialGradient(
                    lightX[i], lightY[i], radius,
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                    Shader.TileMode.CLAMP);
            fallbackLightPaint.setShader(gradient);
            fallbackLightPaint.setXfermode(null);
            canvas.drawOval(new RectF(
                    lightX[i] - radius, lightY[i] - radius,
                    lightX[i] + radius, lightY[i] + radius), fallbackLightPaint);

            // Mask by cover-scaled albedo (whiter keeps more light).
            // Canvas BitmapShader local matrix maps bitmap → destination.
            float scale = coverScale(w, h);
            float dx = (w - albedoBitmap.getWidth() * scale) * 0.5f;
            float dy = (h - albedoBitmap.getHeight() * scale) * 0.5f + textureOffset;
            coverMatrix.reset();
            coverMatrix.setScale(scale, scale);
            coverMatrix.postTranslate(dx, dy);
            BitmapShader maskShader = new BitmapShader(
                    albedoBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            maskShader.setLocalMatrix(coverMatrix);
            fallbackMaskPaint.setShader(maskShader);
            canvas.drawRect(0, 0, w, h, fallbackMaskPaint);
            canvas.restoreToCount(save);
        }

        // Approximate ring emitters as thin rounded strokes masked by albedo.
        fallbackLightPaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < MAX_RINGS; i++) {
            if (ringIntensity[i] <= 0.001f) continue;
            int color = ringColor[i];
            int alpha = Math.round(255f * clamp(ringIntensity[i] * strength * 0.5f, 0f, 0.75f));
            float reach = ringReach[i];
            int save = canvas.saveLayer(0, 0, w, h, null);
            fallbackLightPaint.setStrokeWidth(Math.max(2f, reach * 2.2f));
            fallbackLightPaint.setShader(null);
            fallbackLightPaint.setXfermode(null);
            fallbackLightPaint.setColor(Color.argb(alpha,
                    Color.red(color), Color.green(color), Color.blue(color)));
            // Slightly expanded stroke so fallback also spills onto surrounding map.
            float expand = reach * 0.35f;
            canvas.drawRoundRect(new RectF(
                    ringCx[i] - ringHalfW[i] - expand, ringCy[i] - ringHalfH[i] - expand,
                    ringCx[i] + ringHalfW[i] + expand, ringCy[i] + ringHalfH[i] + expand),
                    ringCorner[i] + expand, ringCorner[i] + expand, fallbackLightPaint);

            float scale = coverScale(w, h);
            float dx = (w - albedoBitmap.getWidth() * scale) * 0.5f;
            float dy = (h - albedoBitmap.getHeight() * scale) * 0.5f + textureOffset;
            coverMatrix.reset();
            coverMatrix.setScale(scale, scale);
            coverMatrix.postTranslate(dx, dy);
            BitmapShader maskShader = new BitmapShader(
                    albedoBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            maskShader.setLocalMatrix(coverMatrix);
            fallbackMaskPaint.setShader(maskShader);
            canvas.drawRect(0, 0, w, h, fallbackMaskPaint);
            canvas.restoreToCount(save);
        }
        fallbackLightPaint.setStyle(Paint.Style.FILL);
    }

    /** Cover scale: one continuous map fills the view (CLAMP, never tiled). */
    private float coverScale(int viewW, int viewH) {
        float bw = albedoBitmap.getWidth();
        float bh = albedoBitmap.getHeight();
        return Math.max(viewW / bw, viewH / bh);
    }

    private void bindLight(RuntimeShader shader, int index) {
        String suffix = String.valueOf(index);
        shader.setFloatUniform("lightPos" + suffix, lightX[index], lightY[index]);
        int color = lightColor[index];
        shader.setFloatUniform("lightColor" + suffix,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f);
        shader.setFloatUniform("lightRadius" + suffix, lightRadius[index]);
        shader.setFloatUniform("lightIntensity" + suffix, lightIntensity[index]);
        shader.setFloatUniform("lightPower" + suffix, lightMapPower[index]);
    }

    private void bindRing(RuntimeShader shader, int index) {
        String suffix = String.valueOf(index);
        shader.setFloatUniform("ringCenter" + suffix, ringCx[index], ringCy[index]);
        shader.setFloatUniform("ringHalf" + suffix, ringHalfW[index], ringHalfH[index]);
        shader.setFloatUniform("ringCorner" + suffix, ringCorner[index]);
        shader.setFloatUniform("ringReach" + suffix, ringReach[index]);
        int color = ringColor[index];
        shader.setFloatUniform("ringColor" + suffix,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f);
        shader.setFloatUniform("ringIntensity" + suffix, ringIntensity[index]);
        shader.setFloatUniform("ringPower" + suffix, ringMapPower[index]);
    }

    @Override
    protected void onDetachedFromWindow() {
        ++cueGeneration;
        if (cueAnimator != null) {
            cueAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    private static int colorFor(TextureCue cue) {
        switch (cue) {
            case ATTENTION_URGENT:
            case CONFIRMATION_REQUIRED:
            case PROPOSAL_READY:
                return Color.rgb(180, 126, 56);
            case ACTION_FAILED:
                return Color.rgb(142, 61, 58);
            case CANCELLED:
                return Color.rgb(118, 104, 96);
            case ACTION_DISPATCHED:
            case EXECUTION_STARTED:
            case LISTENING_STARTED:
            default:
                return Color.rgb(42, 119, 114);
        }
    }

    private static Bitmap loadTexture(Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = context.getAssets().open("moth-texture.jpg")) {
            Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
            if (decoded == null) return null;
            Bitmap src = decoded;
            if (decoded.getConfig() != Bitmap.Config.ARGB_8888) {
                Bitmap copy = decoded.copy(Bitmap.Config.ARGB_8888, false);
                if (copy != null && copy != decoded) {
                    decoded.recycle();
                    src = copy;
                }
            }
            // Expand the JPEG's narrow high-key range into a true 0–1 whiteness map.
            return stretchWhitenessAlbedo(src);
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * moth-texture.jpg sits almost entirely in ~190–238 grey. Stretch p5→p98 into
     * 0–255 so whiteness gating can carve veins/eyespots into catch-lights.
     */
    private static Bitmap stretchWhitenessAlbedo(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] hist = new int[256];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int y = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114 + 500) / 1000;
            hist[Math.max(0, Math.min(255, y))]++;
        }
        int total = pixels.length;
        int lo = 0;
        int hi = 255;
        int cum = 0;
        int p5 = Math.max(1, total / 20);
        int p98 = Math.max(p5 + 1, (int) (total * 0.98f));
        for (int i = 0; i < 256; i++) {
            cum += hist[i];
            if (cum >= p5) { lo = i; break; }
        }
        cum = 0;
        for (int i = 0; i < 256; i++) {
            cum += hist[i];
            if (cum >= p98) { hi = i; break; }
        }
        if (hi <= lo) {
            lo = 200;
            hi = 240;
        }
        float inv = 255f / (hi - lo);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int y = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114 + 500) / 1000;
            float u = Math.max(0f, Math.min(1f, (y - lo) * inv / 255f));
            // Emphasize whiter paper; darker veins stay near 0 so catch is map-gated.
            int out = Math.round((float) Math.pow(u, 1.55) * 255f);
            pixels[i] = Color.argb(255, out, out, out);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        if (out != src) {
            src.recycle();
        }
        return out;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
