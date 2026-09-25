package com.textureflow.ui.lighting;

import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;

import com.textureflow.ui.MainSurface;
import com.textureflow.ui.TextureBackgroundView;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.nav.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Catch-lights into the moth albedo map: nav icons (point 0–1) plus
 * rounded-rect ring emitters around unread chat cards. Cue uses point 2.
 */
public final class ReflectionLightsController {
    private final MainSurface surface;
    private final List<View> glowRingViews = new ArrayList<>();
    private final List<Integer> glowRingColors = new ArrayList<>();

    public ReflectionLightsController(MainSurface surface) {
        this.surface = surface;
    }

    public void clearGlowRings() {
        glowRingViews.clear();
        glowRingColors.clear();
    }

    public void registerGlowRing(View card, int color) {
        glowRingViews.add(card);
        glowRingColors.add(color);
    }

    public void update() {
        TextureBackgroundView backgroundView = surface.backgroundView;
        LinearLayout chatsTab = surface.navigation == null ? null : surface.navigation.chatsTab();
        LinearLayout flowsTab = surface.navigation == null ? null : surface.navigation.flowsTab();
        if (backgroundView == null || chatsTab == null || flowsTab == null) return;
        UiKit kit = surface.kit;
        Page currentPage = surface.navigation.currentPage();

        int[] rootLoc = new int[2];
        int[] loc = new int[2];
        backgroundView.getLocationOnScreen(rootLoc);

        chatsTab.getLocationOnScreen(loc);
        float chatX = loc[0] - rootLoc[0] + chatsTab.getWidth() * 0.5f;
        float chatY = loc[1] - rootLoc[1] + chatsTab.getHeight() * 0.35f;
        boolean chatLit = currentPage == Page.CHATS || currentPage == Page.CHAT;
        backgroundView.setLight(0, chatX, chatY,
                Color.parseColor("#2A7772"),
                kit.dp(chatLit ? 360 : 220),
                chatLit ? 0.95f : 0.35f);

        flowsTab.getLocationOnScreen(loc);
        float flowX = loc[0] - rootLoc[0] + flowsTab.getWidth() * 0.5f;
        float flowY = loc[1] - rootLoc[1] + flowsTab.getHeight() * 0.35f;
        boolean flowLit = currentPage == Page.FLOWS;
        backgroundView.setLight(1, flowX, flowY,
                Color.parseColor("#6B7585"),
                kit.dp(flowLit ? 380 : 230),
                flowLit ? 1.0f : 0.3f);

        backgroundView.clearRings();
        if (!chatLit) return;

        int slot = 0;
        int viewH = backgroundView.getHeight();
        float corner = kit.dp(30);
        // Soft outward reach so catch settles into the map, not a hard ring fringe.
        float reach = kit.dp(36);
        for (int i = 0; i < glowRingViews.size() && slot < 3; i++) {
            View card = glowRingViews.get(i);
            if (card.getWindowToken() == null || !card.isShown()
                    || card.getWidth() <= 0 || card.getHeight() <= 0) {
                continue;
            }
            card.getLocationOnScreen(loc);
            float x = loc[0] - rootLoc[0] + card.getWidth() * 0.5f;
            float y = loc[1] - rootLoc[1] + card.getHeight() * 0.5f;
            if (y < -card.getHeight() || y > viewH + card.getHeight()) continue;

            backgroundView.setRing(slot,
                    x, y,
                    card.getWidth() * 0.5f,
                    card.getHeight() * 0.5f,
                    corner,
                    reach,
                    glowRingColors.get(i),
                    0.45f,
                    3.2f);
            slot++;
        }
    }
}
