package com.textureflow.ui.nav;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.textureflow.ui.MainSurface;
import com.textureflow.ui.MothMarketTheme;
import com.textureflow.ui.kit.UiKit;

public final class NavigationController {
    private final MainSurface surface;
    private Page currentPage = Page.CHATS;
    private FrameLayout chatsPage;
    private FrameLayout chatThreadPage;
    private FrameLayout settingsPage;
    private FrameLayout flowsPage;
    private LinearLayout chatsTab;
    private LinearLayout flowsTab;

    public NavigationController(MainSurface surface) {
        this.surface = surface;
    }

    public Page currentPage() {
        return currentPage;
    }

    public LinearLayout chatsTab() {
        return chatsTab;
    }

    public LinearLayout flowsTab() {
        return flowsTab;
    }

    public void bindPages(
            FrameLayout chatsPage,
            FrameLayout chatThreadPage,
            FrameLayout settingsPage,
            FrameLayout flowsPage) {
        this.chatsPage = chatsPage;
        this.chatThreadPage = chatThreadPage;
        this.settingsPage = settingsPage;
        this.flowsPage = flowsPage;
    }

    public LinearLayout build() {
        UiKit kit = surface.kit;
        LinearLayout shell = new LinearLayout(surface.activity);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(6));
        shell.setBackground(MothMarketTheme.roundRect(MothMarketTheme.NAV, 30f, surface.activity));
        shell.setElevation(kit.dp(8));
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        chatsTab = navTab("chat", com.textureflow.R.drawable.ic_chats, Page.CHATS, true);
        flowsTab = navTab("flow", com.textureflow.R.drawable.moth_logo, Page.FLOWS, false);
        shell.addView(chatsTab, kit.navItemParams());
        shell.addView(flowsTab, kit.navItemParams());
        return shell;
    }

    public LinearLayout navTab(String label, int icon, Page page, boolean selected) {
        UiKit kit = surface.kit;
        LinearLayout tab = new LinearLayout(surface.activity);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setPadding(kit.dp(18), kit.dp(8), kit.dp(18), kit.dp(8));
        tab.setBackground(MothMarketTheme.navPill(surface.activity, selected));
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setSelected(selected);
        tab.setOnClickListener(view -> showPage(page));
        tab.setClipChildren(false);
        tab.setClipToPadding(false);

        ImageView iconView = new ImageView(surface.activity);
        iconView.setImageResource(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setContentDescription(label);
        tab.addView(iconView, new LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)));

        TextView caption = kit.text(label, 11, selected ? UiKit.INK : UiKit.MUTED, selected);
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.topMargin = kit.dp(3);
        captionParams.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(caption, captionParams);
        surface.textureEngine.attachGlassControl(tab);
        return tab;
    }

    public void showPage(Page page) {
        currentPage = page;
        chatsPage.setVisibility(page == Page.CHATS ? View.VISIBLE : View.GONE);
        chatThreadPage.setVisibility(page == Page.CHAT ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == Page.SETTINGS ? View.VISIBLE : View.GONE);
        flowsPage.setVisibility(page == Page.FLOWS ? View.VISIBLE : View.GONE);
        selectNavTab(chatsTab, page == Page.CHATS || page == Page.CHAT);
        selectNavTab(flowsTab, page == Page.FLOWS);
        View bump = page == Page.FLOWS ? flowsTab : chatsTab;
        surface.textureEngine.playBoundaryBump(bump);
        bump.post(surface::updateReflectionLights);
    }

    public void selectNavTab(LinearLayout tab, boolean selected) {
        tab.setSelected(selected);
        tab.setBackground(MothMarketTheme.navPill(surface.activity, selected));
        if (tab.getChildCount() > 1 && tab.getChildAt(1) instanceof TextView caption) {
            caption.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            caption.setTextColor(selected ? UiKit.INK : UiKit.MUTED);
        }
    }
}
