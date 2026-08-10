package com.textureflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import com.textureflow.texture.TextureCue;

import java.io.IOException;
import java.io.InputStream;

public final class TextureBackgroundView extends View {
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint washPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final BitmapShader textureShader;
    private boolean reducedTexture;
    private float textureOffset;
    private float cueAlpha;
    private ValueAnimator cueAnimator;
    private int cueGeneration;

    public TextureBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        basePaint.setColor(Color.rgb(231, 232, 225));
        Bitmap texture = loadTexture(context);
        if (texture != null) {
            textureShader = new BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            texturePaint.setShader(textureShader);
        } else {
            textureShader = null;
            texturePaint.setColor(Color.rgb(222, 224, 215));
        }
        washPaint.setColor(Color.argb(92, 246, 246, 241));
        grainPaint.setColor(Color.argb(17, 37, 41, 39));
        cuePaint.setColor(Color.rgb(104, 118, 106));
    }

    public void setReducedTexture(boolean reducedTexture) {
        if (this.reducedTexture == reducedTexture) {
            return;
        }
        this.reducedTexture = reducedTexture;
        invalidate();
    }

    public boolean isReducedTexture() {
        return reducedTexture;
    }

    public void setScrollOffset(int scrollY) {
        if (reducedTexture) {
            return;
        }
        textureOffset = -(scrollY * 0.06f);
        invalidate();
    }

    public void showCue(TextureCue cue, boolean reducedMotion) {
        if (cue == TextureCue.CONTENT_MOVEMENT || cue == TextureCue.FOCUS_ENTERED) {
            return;
        }
        if (cueAnimator != null) {
            cueAnimator.cancel();
        }
        int generation = ++cueGeneration;
        cuePaint.setColor(colorFor(cue));
        float startAlpha = reducedMotion ? 0.055f : 0.13f;
        if (reducedMotion) {
            cueAlpha = startAlpha;
            invalidate();
            postDelayed(() -> {
                if (generation == cueGeneration) {
                    cueAlpha = 0f;
                    invalidate();
                }
            }, 180L);
            return;
        }
        cueAnimator = ValueAnimator.ofFloat(startAlpha, 0f);
        cueAnimator.setDuration(Math.max(220L, cue.durationMs()));
        cueAnimator.addUpdateListener(animation -> {
            cueAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        cueAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), basePaint);
        if (textureShader != null) {
            shaderMatrix.setTranslate(0f, textureOffset);
            textureShader.setLocalMatrix(shaderMatrix);
        }
        texturePaint.setAlpha(reducedTexture ? 58 : 172);
        canvas.drawRect(0, 0, getWidth(), getHeight(), texturePaint);
        washPaint.setAlpha(reducedTexture ? 196 : 92);
        canvas.drawRect(0, 0, getWidth(), getHeight(), washPaint);
        if (!reducedTexture) {
            for (int y = 0; y < getHeight(); y += 10) {
                canvas.drawRect(new Rect(0, y, getWidth(), y + 1), grainPaint);
            }
        }
        if (cueAlpha > 0f) {
            cuePaint.setAlpha(Math.round(255f * cueAlpha));
            canvas.drawRect(0, 0, getWidth(), getHeight(), cuePaint);
        }
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
        try (InputStream stream = context.getAssets().open("moth-texture.jpg")) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException ignored) {
            return null;
        }
    }
}
