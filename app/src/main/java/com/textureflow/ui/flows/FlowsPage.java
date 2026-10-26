package com.textureflow.ui.flows;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.textureflow.ui.MainSurface;
import com.textureflow.ui.MothMarketTheme;
import com.textureflow.ui.TextureDrawableFactory;
import com.textureflow.ui.kit.UiKit;

/**
 * A local visual prototype for commitments. Completing a node only changes this on-screen Flow.
 */
public final class FlowsPage {
    private enum StepState { COMPLETE, CURRENT, UPCOMING, LATER }

    private static final class Commitment {
        final String title;
        final String detail;
        final String source;
        StepState state;

        Commitment(String title, String detail, String source, StepState state) {
            this.title = title;
            this.detail = detail;
            this.source = source;
            this.state = state;
        }
    }

    private final MainSurface surface;
    private final Commitment[] commitments = new Commitment[]{
            new Commitment("Check the shopping list", "You collected the items for today.",
                    "Completed", StepState.COMPLETE),
            new Commitment("Bring oat milk", "Alex asked you to pick it up on the way home.",
                    "From Alex | Chat", StepState.CURRENT),
            new Commitment("Send the dinner time", "Confirm the time once you are back.",
                    "From Alex | Chat", StepState.UPCOMING)
    };
    private LinearLayout content;
    private int selectedIndex = 1;

    public FlowsPage(MainSurface surface) {
        this.surface = surface;
    }

    public FrameLayout build() {
        UiKit kit = surface.kit;
        content = kit.pageColumn();
        content.setPadding(kit.dp(20), kit.dp(24), kit.dp(20), kit.dp(16));
        render();
        return kit.scrollPage(content);
    }

    private void render() {
        UiKit kit = surface.kit;
        content.removeAllViews();

        LinearLayout header = new LinearLayout(surface.activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = kit.text("TODAY", 13, UiKit.TEAL, true);
        header.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView progress = kit.text(progressLabel(), 12, UiKit.MUTED, true);
        progress.setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(6));
        progress.setBackground(MothMarketTheme.glassSurface(
                surface.activity, MothMarketTheme.NAV_SELECTED, 16f));
        header.addView(progress, new LinearLayout.LayoutParams(-2, -2));
        content.addView(header, kit.matchWrap());

        TextView title = kit.text("Your path", 30, UiKit.INK, true);
        title.setAccessibilityHeading(true);
        content.addView(title, kit.topMargin(kit.dp(4)));

        TextView subtitle = kit.text("A small route through what matters today.", 15, UiKit.MUTED, false);
        content.addView(subtitle, kit.topMargin(kit.dp(6)));

        LinearLayout.LayoutParams trailParams = new LinearLayout.LayoutParams(-1, kit.dp(442));
        trailParams.setMargins(0, kit.dp(12), 0, 0);
        content.addView(commitmentTrail(), trailParams);
        content.addView(selectedCommitment(), kit.wideWithTop(kit.dp(8)));

        TextView footnote = kit.text("Only commitments you choose belong here.", 13, UiKit.MUTED, false);
        footnote.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(footnote, kit.wideWithTop(kit.dp(18)));
    }

    private FrameLayout commitmentTrail() {
        UiKit kit = surface.kit;
        FrameLayout trail = new FrameLayout(surface.activity);
        trail.setClipChildren(false);
        trail.setClipToPadding(false);
        trail.setMinimumHeight(kit.dp(442));
        trail.addView(new TrailBackdropView(surface.activity),
                new FrameLayout.LayoutParams(-1, kit.dp(442)));

        // The route deliberately descends in a staggered, map-like rhythm.
        addTrailNode(trail, 2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, kit.dp(18));
        addTrailLabel(trail, 2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, kit.dp(104));
        addTrailNode(trail, 1, Gravity.TOP | Gravity.END, kit.dp(18), kit.dp(142));
        addTrailLabel(trail, 1, Gravity.TOP | Gravity.END, kit.dp(6), kit.dp(244));
        addTrailNode(trail, 0, Gravity.TOP | Gravity.START, kit.dp(12), kit.dp(286));
        addTrailLabel(trail, 0, Gravity.TOP | Gravity.START, kit.dp(0), kit.dp(374));
        return trail;
    }

    private void addTrailNode(FrameLayout trail, int index, int gravity, int horizontalMargin, int topMargin) {
        UiKit kit = surface.kit;
        Commitment commitment = commitments[index];
        int size = commitment.state == StepState.CURRENT ? kit.dp(86) : kit.dp(72);
        TextView node = kit.text(nodeLabel(commitment.state, index),
                commitment.state == StepState.COMPLETE ? 11 : 17,
                commitment.state == StepState.COMPLETE ? Color.WHITE : UiKit.INK, true);
        node.setGravity(Gravity.CENTER);
        node.setClickable(true);
        node.setFocusable(true);
        node.setContentDescription(commitment.title + ", " + stateLabel(commitment.state));
        node.setBackground(nodeBackground(commitment.state, index == selectedIndex));
        node.setElevation(commitment.state == StepState.CURRENT ? kit.dp(8) : kit.dp(4));
        surface.textureEngine.attachGlassControl(node);
        node.setOnClickListener(view -> {
            selectedIndex = index;
            render();
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, gravity);
        if ((gravity & Gravity.END) == Gravity.END) params.rightMargin = horizontalMargin;
        if ((gravity & Gravity.START) == Gravity.START) params.leftMargin = horizontalMargin;
        params.topMargin = topMargin;
        trail.addView(node, params);
    }

    private void addTrailLabel(
            FrameLayout trail, int index, int gravity, int horizontalMargin, int topMargin) {
        UiKit kit = surface.kit;
        Commitment commitment = commitments[index];
        TextView label = kit.text(commitment.title, 14, UiKit.INK,
                commitment.state == StepState.CURRENT);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(5));
        label.setBackground(MothMarketTheme.glassSurface(
                surface.activity, Color.argb(168, 247, 249, 252), 15f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, gravity);
        if ((gravity & Gravity.END) == Gravity.END) params.rightMargin = horizontalMargin;
        if ((gravity & Gravity.START) == Gravity.START) params.leftMargin = horizontalMargin;
        params.topMargin = topMargin;
        trail.addView(label, params);
    }

    private LinearLayout selectedCommitment() {
        UiKit kit = surface.kit;
        if (selectedIndex < 0 || selectedIndex >= commitments.length) selectedIndex = currentIndex();
        Commitment commitment = selectedIndex >= 0 ? commitments[selectedIndex] : null;
        if (commitment == null) return finishedState();

        LinearLayout card = new LinearLayout(surface.activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(kit.dp(20), kit.dp(18), kit.dp(20), kit.dp(18));
        card.setBackground(TextureDrawableFactory.emphasizedPanel(
                surface.activity, kit.dp(28), stateAccent(commitment.state)));
        TextView label = kit.text(stateLabel(commitment.state).toUpperCase(), 12,
                stateAccent(commitment.state), true);
        card.addView(label);
        TextView title = kit.text(commitment.title, 22, UiKit.INK, true);
        card.addView(title, kit.topMargin(kit.dp(6)));
        TextView detail = kit.text(commitment.detail, 15, UiKit.MUTED, false);
        detail.setLineSpacing(kit.dp(2), 1f);
        card.addView(detail, kit.topMargin(kit.dp(6)));
        TextView source = kit.text(commitment.source, 13, UiKit.MUTED, true);
        card.addView(source, kit.topMargin(kit.dp(12)));

        if (commitment.state == StepState.CURRENT) {
            LinearLayout actions = new LinearLayout(surface.activity);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button later = kit.compactButton("Move to tomorrow", UiKit.AMBER);
            later.setOnClickListener(this::moveCurrentToLater);
            LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, -2, 1f);
            laterParams.setMargins(0, 0, kit.dp(8), 0);
            actions.addView(later, laterParams);
            Button complete = kit.compactButton("Mark complete", UiKit.TEAL);
            complete.setOnClickListener(this::completeCurrent);
            actions.addView(complete, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(actions, kit.wideWithTop(kit.dp(16)));
        }
        return card;
    }

    private LinearLayout finishedState() {
        UiKit kit = surface.kit;
        LinearLayout card = kit.surface(kit.dp(28));
        TextView label = kit.text("TODAY IS CLEAR", 12, UiKit.TEAL, true);
        card.addView(label);
        boolean hasLaterCommitments = laterCount() > 0;
        TextView title = kit.text(hasLaterCommitments ? "Nothing else is due today." : "You followed through.",
                22, UiKit.INK, true);
        card.addView(title, kit.topMargin(kit.dp(6)));
        TextView detail = kit.text(hasLaterCommitments
                        ? "Your postponed commitment is waiting for tomorrow."
                        : "The path is ready when you choose your next commitment.",
                15, UiKit.MUTED, false);
        card.addView(detail, kit.topMargin(kit.dp(6)));
        return card;
    }

    private GradientDrawable nodeBackground(StepState state, boolean selected) {
        UiKit kit = surface.kit;
        int fill = switch (state) {
            case COMPLETE -> UiKit.TEAL;
            case CURRENT -> MothMarketTheme.NAV_SELECTED;
            case LATER -> MothMarketTheme.CHIP;
            case UPCOMING -> MothMarketTheme.SURFACE_RAISED;
        };
        GradientDrawable drawable = MothMarketTheme.circle(fill);
        int stroke = state == StepState.CURRENT ? kit.dp(3) : kit.dp(1);
        int color = selected ? stateAccent(state) : Color.argb(150, 255, 255, 255);
        drawable.setStroke(stroke, color);
        return drawable;
    }

    private void completeCurrent(View source) {
        int current = currentIndex();
        if (current < 0) return;
        commitments[current].state = StepState.COMPLETE;
        if (current + 1 < commitments.length && commitments[current + 1].state == StepState.UPCOMING) {
            commitments[current + 1].state = StepState.CURRENT;
            selectedIndex = current + 1;
        }
        surface.textureEngine.playBoundaryBump(source);
        render();
    }

    private void moveCurrentToLater(View source) {
        int current = currentIndex();
        if (current < 0) return;
        commitments[current].state = StepState.LATER;
        for (int i = current + 1; i < commitments.length; i++) {
            if (commitments[i].state == StepState.UPCOMING) {
                commitments[i].state = StepState.CURRENT;
                selectedIndex = i;
                break;
            }
        }
        surface.textureEngine.playBoundaryBump(source);
        render();
    }

    private int currentIndex() {
        for (int i = 0; i < commitments.length; i++) {
            if (commitments[i].state == StepState.CURRENT) return i;
        }
        return -1;
    }

    private String progressLabel() {
        int done = 0;
        for (Commitment commitment : commitments) {
            if (commitment.state == StepState.COMPLETE) done++;
        }
        return done + " of " + commitments.length + " commitments complete";
    }

    private int laterCount() {
        int later = 0;
        for (Commitment commitment : commitments) {
            if (commitment.state == StepState.LATER) later++;
        }
        return later;
    }

    private static String nodeLabel(StepState state, int index) {
        return switch (state) {
            case COMPLETE -> "Done";
            case CURRENT -> "Now";
            case LATER -> "Later";
            case UPCOMING -> String.valueOf(index + 1);
        };
    }

    private static String stateLabel(StepState state) {
        return switch (state) {
            case COMPLETE -> "Completed";
            case CURRENT -> "Next up";
            case LATER -> "Tomorrow";
            case UPCOMING -> "Later in your path";
        };
    }

    private static int stateAccent(StepState state) {
        return state == StepState.LATER ? UiKit.AMBER : UiKit.TEAL;
    }

    private final class TrailBackdropView extends View {
        private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        TrailBackdropView(android.content.Context context) {
            super(context);
            pathPaint.setStyle(Paint.Style.STROKE);
            pathPaint.setStrokeCap(Paint.Cap.ROUND);
            pathPaint.setStrokeWidth(surface.kit.dp(5));
            pathPaint.setColor(Color.argb(168, 38, 118, 110));
            haloPaint.setColor(Color.argb(24, 38, 118, 110));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            canvas.drawCircle(width * .50f, height * .18f, surface.kit.dp(46), haloPaint);
            canvas.drawCircle(width * .90f, height * .47f, surface.kit.dp(58), haloPaint);
            canvas.drawCircle(width * .13f, height * .78f, surface.kit.dp(46), haloPaint);
            path.reset();
            path.moveTo(width * .50f, height * .18f);
            path.cubicTo(width * .62f, height * .25f, width * .82f, height * .34f,
                    width * .90f, height * .47f);
            path.cubicTo(width * .84f, height * .61f, width * .35f, height * .69f,
                    width * .13f, height * .78f);
            canvas.drawPath(path, pathPaint);
        }
    }
}
