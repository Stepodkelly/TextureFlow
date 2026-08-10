package com.textureflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public final class EyeOfHorusView extends View {
    public enum State {
        DISCONNECTED("Disconnected"),
        CONNECTED("Connected"),
        LISTENING("Listening"),
        THINKING("Thinking"),
        PROPOSAL("Proposal ready"),
        AWAITING_CONFIRMATION("Awaiting confirmation"),
        EXECUTING("Executing"),
        SUCCESS("Action dispatched"),
        FAILURE("Action failed");

        private final String description;

        State(String description) {
            this.description = description;
        }
    }

    private final Paint sage = paint(Color.rgb(104, 118, 106));
    private final Paint ink = paint(Color.rgb(37, 41, 39));
    private final Paint paper = paint(Color.rgb(242, 243, 239));
    private final Paint rust = paint(Color.rgb(136, 107, 94));
    private final Paint statePaint = paint(Color.rgb(126, 132, 126));
    private final Paint glowPaint = paint(Color.argb(44, 42, 119, 114));
    private State state = State.DISCONNECTED;
    private boolean reducedMotion;
    private float irisScale = 1f;
    private ValueAnimator listeningAnimator;

    public EyeOfHorusView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setState(State state) {
        if (state == null || this.state == state) {
            return;
        }
        this.state = state;
        statePaint.setColor(colorFor(state));
        glowPaint.setColor(withAlpha(colorFor(state), 42));
        updateAnimation();
        invalidate();
    }

    public State getState() {
        return state;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        updateAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        canvas.save();
        canvas.scale(w / 108f, h / 108f);

        canvas.drawCircle(55, 52, state == State.DISCONNECTED ? 29f : 35f, glowPaint);

        Path outer = new Path();
        outer.moveTo(8, 52);
        outer.cubicTo(23, 31, 45, 24, 64, 30);
        outer.cubicTo(78, 34, 90, 44, 100, 55);
        outer.cubicTo(85, 65, 69, 70, 52, 68);
        outer.cubicTo(34, 66, 20, 61, 8, 52);
        canvas.drawPath(outer, state == State.DISCONNECTED ? sage : statePaint);

        Path eye = new Path();
        eye.moveTo(18, 52);
        eye.cubicTo(31, 39, 47, 36, 61, 39);
        eye.cubicTo(72, 42, 82, 48, 91, 55);
        eye.cubicTo(78, 61, 66, 64, 54, 62);
        eye.cubicTo(39, 61, 28, 58, 18, 52);
        canvas.drawPath(eye, ink);

        Path white = new Path();
        white.moveTo(30, 52);
        white.cubicTo(42, 43, 57, 42, 70, 51);
        white.cubicTo(58, 59, 44, 60, 30, 52);
        canvas.drawPath(white, paper);
        canvas.save();
        canvas.scale(irisScale, irisScale, 57, 51);
        canvas.drawCircle(57, 51, 8.8f, ink);
        canvas.drawCircle(57, 51, 4.1f, statePaint);
        canvas.drawCircle(60, 48, 2.1f, paper);
        canvas.restore();

        Path tail = new Path();
        tail.moveTo(59, 66);
        tail.cubicTo(59, 79, 53, 87, 45, 94);
        tail.cubicTo(59, 91, 69, 82, 69, 66);
        canvas.drawPath(tail, sage);

        Path curl = new Path();
        curl.moveTo(73, 66);
        curl.cubicTo(71, 79, 76, 88, 85, 96);
        curl.cubicTo(81, 84, 83, 73, 90, 62);
        canvas.drawPath(curl, rust);

        Path underline = new Path();
        underline.moveTo(21, 57);
        underline.cubicTo(37, 72, 64, 78, 87, 63);
        underline.cubicTo(69, 84, 38, 80, 21, 57);
        canvas.drawPath(underline, ink);

        statePaint.setStrokeWidth(state == State.EXECUTING ? 4f : 2.4f);
        statePaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(35, 101, state == State.EXECUTING ? 79 : 72, 101, statePaint);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void updateAnimation() {
        stopAnimation();
        irisScale = state == State.LISTENING ? 1.08f : 1f;
        if (state != State.LISTENING || reducedMotion || !isAttachedToWindow()) {
            invalidate();
            return;
        }
        listeningAnimator = ValueAnimator.ofFloat(1f, 1.13f);
        listeningAnimator.setDuration(900L);
        listeningAnimator.setRepeatCount(ValueAnimator.INFINITE);
        listeningAnimator.setRepeatMode(ValueAnimator.REVERSE);
        listeningAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        listeningAnimator.addUpdateListener(animation -> {
            irisScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        listeningAnimator.start();
    }

    private void stopAnimation() {
        if (listeningAnimator != null) {
            listeningAnimator.cancel();
            listeningAnimator = null;
        }
    }

    private static int colorFor(State state) {
        switch (state) {
            case LISTENING:
            case CONNECTED:
            case EXECUTING:
            case SUCCESS:
                return Color.rgb(42, 119, 114);
            case THINKING:
            case PROPOSAL:
            case AWAITING_CONFIRMATION:
                return Color.rgb(180, 126, 56);
            case FAILURE:
                return Color.rgb(142, 61, 58);
            case DISCONNECTED:
            default:
                return Color.rgb(126, 132, 126);
        }
    }

    private static Paint paint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
