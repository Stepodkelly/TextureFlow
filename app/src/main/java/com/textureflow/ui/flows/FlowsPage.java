package com.textureflow.ui.flows;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.textureflow.R;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.kit.UiKit;

public final class FlowsPage {
    private final MainSurface surface;

    public FlowsPage(MainSurface surface) {
        this.surface = surface;
    }

    public FrameLayout build() {
        UiKit kit = surface.kit;
        LinearLayout content = kit.pageColumn();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(kit.dp(24), kit.dp(48), kit.dp(24), kit.dp(24));
        ImageView moth = new ImageView(surface.activity);
        moth.setImageResource(R.drawable.moth_logo);
        moth.setContentDescription("Moth Market Flows");
        content.addView(moth, new LinearLayout.LayoutParams(kit.dp(96), kit.dp(96)));
        TextView title = kit.text("Flows", 28, UiKit.INK, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title, kit.topMargin(kit.dp(16)));
        TextView body = kit.text("Flows will live here next. For now, Chats is ready.", 16, UiKit.MUTED, false);
        body.setGravity(Gravity.CENTER);
        content.addView(body, kit.topMargin(kit.dp(10)));
        return kit.scrollPage(content);
    }
}
