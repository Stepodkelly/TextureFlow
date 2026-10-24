package com.textureflow.ui.flows;

import android.graphics.Color;
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

        TextView eyebrow = kit.text("TODAY", 13, UiKit.TEAL, true);
        content.addView(eyebrow);
        TextView title = kit.text("Your path", 30, UiKit.INK, true);
        title.setAccessibilityHeading(true);
        content.addView(title, kit.topMargin(kit.dp(4)));
        TextView progress = kit.text(progressLabel(), 15, UiKit.MUTED, false);
        content.addView(progress, kit.topMargin(kit.dp(6)));

        int current = currentIndex();
        if (current >= 0) {
            content.addView(currentCommitment(commitments[current]), kit.wideWithTop(kit.dp(22)));
        } else {
            content.addView(finishedState(), kit.wideWithTop(kit.dp(22)));
        }

        TextView pathHeading = kit.sectionHeading("Commitments");
        content.addView(pathHeading, kit.topMargin(kit.dp(26)));
        for (int i = 0; i < commitments.length; i++) {
            content.addView(pathNode(commitments[i], i + 1), kit.wideWithTop(kit.dp(i == 0 ? 0 : 4)));
            if (i < commitments.length - 1) {
                content.addView(pathConnector(commitments[i]), connectorParams());
            }
        }

        TextView footnote = kit.text("Only commitments you choose belong here.", 13, UiKit.MUTED, false);
        footnote.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(footnote, kit.wideWithTop(kit.dp(20)));
    }

    private LinearLayout currentCommitment(Commitment commitment) {
        UiKit kit = surface.kit;
        LinearLayout card = new LinearLayout(surface.activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(kit.dp(20), kit.dp(18), kit.dp(20), kit.dp(18));
        card.setBackground(TextureDrawableFactory.emphasizedPanel(
                surface.activity, kit.dp(28), UiKit.TEAL));
        card.setElevation(kit.dp(3));

        TextView label = kit.text("NEXT UP", 12, UiKit.TEAL, true);
        card.addView(label);
        TextView title = kit.text(commitment.title, 22, UiKit.INK, true);
        card.addView(title, kit.topMargin(kit.dp(6)));
        TextView detail = kit.text(commitment.detail, 15, UiKit.MUTED, false);
        detail.setLineSpacing(kit.dp(2), 1f);
        card.addView(detail, kit.topMargin(kit.dp(6)));
        TextView source = kit.text(commitment.source, 13, UiKit.MUTED, true);
        card.addView(source, kit.topMargin(kit.dp(12)));

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

    private LinearLayout pathNode(Commitment commitment, int position) {
        UiKit kit = surface.kit;
        LinearLayout row = new LinearLayout(surface.activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(kit.dp(72));

        TextView marker = markerFor(commitment.state, position);
        row.addView(marker, new LinearLayout.LayoutParams(kit.dp(40), kit.dp(40)));

        LinearLayout details = new LinearLayout(surface.activity);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1f);
        detailParams.setMargins(kit.dp(14), 0, 0, 0);
        TextView title = kit.text(commitment.title, 17, UiKit.INK,
                commitment.state == StepState.CURRENT);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(title);
        TextView source = kit.text(commitment.source, 13, UiKit.MUTED, false);
        source.setSingleLine(true);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(source, kit.topMargin(kit.dp(2)));
        row.addView(details, detailParams);

        if (commitment.state == StepState.CURRENT) {
            TextView next = kit.text("Next", 13, UiKit.TEAL, true);
            row.addView(next, new LinearLayout.LayoutParams(-2, -2));
        } else if (commitment.state == StepState.LATER) {
            TextView later = kit.text("Tomorrow", 13, UiKit.AMBER, true);
            row.addView(later, new LinearLayout.LayoutParams(-2, -2));
        }
        return row;
    }

    private TextView markerFor(StepState state, int position) {
        UiKit kit = surface.kit;
        TextView marker = kit.text(state == StepState.COMPLETE ? "Done" : String.valueOf(position),
                state == StepState.COMPLETE ? 10 : 16,
                state == StepState.COMPLETE ? Color.WHITE : UiKit.INK, true);
        marker.setGravity(Gravity.CENTER);
        GradientDrawable background;
        if (state == StepState.COMPLETE) {
            background = MothMarketTheme.circle(UiKit.TEAL);
        } else if (state == StepState.CURRENT) {
            background = MothMarketTheme.circle(MothMarketTheme.NAV_SELECTED);
            background.setStroke(kit.dp(2), UiKit.TEAL);
        } else if (state == StepState.LATER) {
            background = MothMarketTheme.circle(MothMarketTheme.CHIP);
            background.setStroke(kit.dp(1), UiKit.AMBER);
        } else {
            background = MothMarketTheme.circle(MothMarketTheme.CHIP);
        }
        marker.setBackground(background);
        return marker;
    }

    private View pathConnector(Commitment commitment) {
        View line = new View(surface.activity);
        line.setBackgroundColor(commitment.state == StepState.COMPLETE
                ? UiKit.TEAL : Color.argb(90, 107, 117, 133));
        return line;
    }

    private LinearLayout.LayoutParams connectorParams() {
        UiKit kit = surface.kit;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(kit.dp(2), kit.dp(18));
        params.setMargins(kit.dp(19), 0, 0, 0);
        return params;
    }

    private void completeCurrent(View source) {
        int current = currentIndex();
        if (current < 0) return;
        commitments[current].state = StepState.COMPLETE;
        if (current + 1 < commitments.length && commitments[current + 1].state == StepState.UPCOMING) {
            commitments[current + 1].state = StepState.CURRENT;
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
}
