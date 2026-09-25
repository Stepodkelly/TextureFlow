package com.textureflow.ui.chats;

import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.textureflow.R;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.ui.EyeOfHorusView;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.MothMarketTheme;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.nav.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatListController {
    public static final class LegacyControls {
        public EyeOfHorusView eyeView;
        public LinearLayout attentionPanel;
        public TextView attentionMeta;
        public TextView attentionBody;
        public TextView attentionReason;
        public LinearLayout responsePanel;
        public TextView responseSmile;
        public TextView responseTitle;
        public EditText responseEditor;
        public LinearLayout responseOptions;
        public TextView responseStatus;
        public Button talkButton;
        public Button confirmButton;
        public Button cancelButton;
    }

    private final MainSurface surface;
    private final LegacyControls legacy = new LegacyControls();
    private final List<PersonTimeline> cachedPeople = new ArrayList<>();
    private FrameLayout chatsPage;
    private LinearLayout chatListContainer;
    private EditText searchField;
    private TextView chatsEmpty;
    private String peopleSignature = "";
    @SuppressWarnings("unused")
    private Map<String, AttentionAssessment> assessments = Collections.emptyMap();

    public ChatListController(MainSurface surface) {
        this.surface = surface;
    }

    public LegacyControls legacy() {
        return legacy;
    }

    public FrameLayout page() {
        return chatsPage;
    }

    public LinearLayout chatListContainer() {
        return chatListContainer;
    }

    /** Stream I seam: assessments are ignored until intelligence wiring lands. */
    public void setAssessments(Map<String, AttentionAssessment> assessments) {
        this.assessments = assessments == null ? Collections.emptyMap() : assessments;
    }

    public FrameLayout buildPage() {
        UiKit kit = surface.kit;
        LinearLayout content = kit.pageColumn();
        content.setPadding(kit.dp(20), kit.dp(28), kit.dp(20), kit.dp(12));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        LinearLayout topBar = new LinearLayout(surface.activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(surface.activity);
        logo.setImageResource(R.drawable.moth_logo);
        logo.setContentDescription("Moth Market");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topBar.addView(logo, new LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)));

        View spacer = new View(surface.activity);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageButton more = kit.circleIconButton(R.drawable.ic_more_vert);
        more.setContentDescription("Settings");
        more.setOnClickListener(view -> surface.showPage(Page.SETTINGS));
        topBar.addView(more, new LinearLayout.LayoutParams(kit.dp(42), kit.dp(42)));

        ImageButton add = kit.circleIconButton(R.drawable.ic_plus);
        add.setContentDescription("New chat");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(kit.dp(42), kit.dp(42));
        addParams.setMargins(kit.dp(10), 0, 0, 0);
        add.setOnClickListener(view -> new AlertDialog.Builder(surface.activity)
                .setTitle("New chat")
                .setMessage("Starting a new chat from Moth Market is coming next. For now, open a person from the list.")
                .setPositiveButton("OK", null)
                .show());
        topBar.addView(add, addParams);
        content.addView(topBar, kit.matchWrap());

        LinearLayout searchRow = new LinearLayout(surface.activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(kit.dp(18), kit.dp(12), kit.dp(18), kit.dp(12));
        searchRow.setBackground(MothMarketTheme.roundRect(MothMarketTheme.SEARCH, 26f, surface.activity));
        ImageView searchIcon = new ImageView(surface.activity);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setContentDescription("Search");
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)));
        searchField = new EditText(surface.activity);
        searchField.setHint("Search");
        searchField.setHintTextColor(UiKit.MUTED);
        searchField.setTextColor(UiKit.INK);
        searchField.setBackground(new ColorDrawable(UiKit.TRANSPARENT));
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setPadding(kit.dp(10), kit.dp(2), kit.dp(4), kit.dp(2));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChatList(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams searchParams = kit.matchWrap();
        searchParams.setMargins(0, kit.dp(22), 0, kit.dp(22));
        content.addView(searchRow, searchParams);

        chatListContainer = new LinearLayout(surface.activity);
        chatListContainer.setOrientation(LinearLayout.VERTICAL);
        chatListContainer.setClipChildren(false);
        chatListContainer.setClipToPadding(false);
        content.addView(chatListContainer, kit.matchWrap());

        chatsEmpty = kit.text("No conversations yet.", 16, UiKit.MUTED, false);
        chatsEmpty.setGravity(Gravity.CENTER);
        chatsEmpty.setPadding(kit.dp(12), kit.dp(28), kit.dp(12), kit.dp(28));
        chatsEmpty.setVisibility(View.GONE);

        // Keep legacy action widgets attached but hidden so Core/voice paths stay safe.
        attachHiddenLegacyControls(content);

        chatsPage = kit.scrollPage(content);
        return chatsPage;
    }

    private void attachHiddenLegacyControls(LinearLayout content) {
        UiKit kit = surface.kit;
        legacy.eyeView = new EyeOfHorusView(surface.activity);
        legacy.eyeView.setVisibility(View.GONE);
        content.addView(legacy.eyeView, new LinearLayout.LayoutParams(1, 1));

        legacy.attentionPanel = kit.surface(kit.dp(30));
        legacy.attentionPanel.setVisibility(View.GONE);
        legacy.attentionMeta = kit.text("", 14, UiKit.MUTED, true);
        legacy.attentionPanel.addView(legacy.attentionMeta);
        legacy.attentionBody = kit.text("", 20, UiKit.INK, false);
        legacy.attentionPanel.addView(legacy.attentionBody);
        legacy.attentionReason = kit.text("", 13, UiKit.MUTED, false);
        legacy.attentionPanel.addView(legacy.attentionReason);
        content.addView(legacy.attentionPanel, kit.matchWrap());

        legacy.responsePanel = kit.surface(kit.dp(32));
        legacy.responsePanel.setVisibility(View.GONE);
        legacy.responseSmile = kit.text("🙂", 42, UiKit.INK, false);
        legacy.responsePanel.addView(legacy.responseSmile);
        legacy.responseTitle = kit.text("Response", 14, UiKit.MUTED, true);
        legacy.responsePanel.addView(legacy.responseTitle);
        legacy.responseEditor = new EditText(surface.activity);
        legacy.responseEditor.setVisibility(View.GONE);
        legacy.responsePanel.addView(legacy.responseEditor);
        legacy.responseOptions = new LinearLayout(surface.activity);
        legacy.responseOptions.setOrientation(LinearLayout.HORIZONTAL);
        legacy.responseOptions.setVisibility(View.GONE);
        legacy.responsePanel.addView(legacy.responseOptions);
        legacy.responseStatus = kit.text("", 13, UiKit.MUTED, false);
        legacy.responseStatus.setVisibility(View.GONE);
        legacy.responsePanel.addView(legacy.responseStatus);
        legacy.talkButton = kit.compactButton("Talk", UiKit.TEAL);
        legacy.talkButton.setVisibility(View.GONE);
        legacy.talkButton.setOnClickListener(view -> surface.voice.startVoiceTurn(view));
        legacy.responsePanel.addView(legacy.talkButton);
        legacy.confirmButton = kit.compactButton("Confirm", UiKit.AMBER);
        legacy.confirmButton.setVisibility(View.GONE);
        legacy.confirmButton.setOnClickListener(view -> surface.conversation.requestConfirmation(view));
        legacy.responsePanel.addView(legacy.confirmButton);
        legacy.cancelButton = kit.compactButton("Cancel", UiKit.CRIMSON);
        legacy.cancelButton.setVisibility(View.GONE);
        legacy.cancelButton.setOnClickListener(view -> surface.conversation.requestCancellation(view));
        legacy.responsePanel.addView(legacy.cancelButton);
        content.addView(legacy.responsePanel, kit.matchWrap());
    }

    public void renderPeople(List<StoredNotificationEvent> events) {
        rebuildPeople(events);
    }

    public void renderPeopleIfChanged(List<StoredNotificationEvent> events) {
        String signature = ChatListPresenter.eventSignature(events);
        if (signature.equals(peopleSignature)) return;
        peopleSignature = signature;
        rebuildPeople(events);
    }

    private void rebuildPeople(List<StoredNotificationEvent> events) {
        List<PersonTimeline> ordered = ChatListPresenter.groupPeople(events, liveEventIds());
        if (ordered.isEmpty()) {
            ordered.add(ChatListPresenter.demoAlex(System.currentTimeMillis()));
        }
        cachedPeople.clear();
        cachedPeople.addAll(ordered);
        filterChatList(searchField == null ? "" : searchField.getText().toString());
    }

    private Set<String> liveEventIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (StoredNotificationEvent event : surface.runtime().notifications().getLiveEvents()) {
            ids.add(event.getEventId());
        }
        return ids;
    }

    public void filterChatList(String query) {
        if (chatListContainer == null) return;
        UiKit kit = surface.kit;
        surface.lights.clearGlowRings();
        chatListContainer.removeAllViews();
        List<PersonTimeline> visible = ChatListPresenter.filterPeople(cachedPeople, query);
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.US);
        if (visible.isEmpty()) {
            chatsEmpty.setText(needle.isEmpty() ? "No conversations yet." : "No matches.");
            chatListContainer.addView(chatsEmpty, kit.matchWrap());
            chatsEmpty.setVisibility(View.VISIBLE);
            surface.updateReflectionLights();
            return;
        }
        chatsEmpty.setVisibility(View.GONE);
        // Keep empty slots aligned with the card body (avatar only peeks past the left edge).
        final int avatarOverhang = kit.dp(8);
        for (PersonTimeline person : visible) {
            LinearLayout.LayoutParams rowParams = kit.wideWithBottom(kit.dp(14));
            chatListContainer.addView(personRow(person), rowParams);
        }
        int placeholders = Math.max(0, 5 - visible.size());
        for (int i = 0; i < placeholders; i++) {
            View slot = new View(surface.activity);
            slot.setBackground(MothMarketTheme.placeholderSlot(surface.activity));
            slot.setAlpha(0.72f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, kit.dp(76));
            params.setMargins(avatarOverhang, 0, 0, kit.dp(14));
            chatListContainer.addView(slot, params);
        }
        chatListContainer.post(surface::updateReflectionLights);
    }

    private View personRow(PersonTimeline person) {
        UiKit kit = surface.kit;
        final int avatarSize = kit.dp(56);
        // Card stretches under most of the avatar; only a small slice peeks past the left edge.
        final int overhang = kit.dp(8);

        FrameLayout shell = new FrameLayout(surface.activity);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        shell.setPadding(0, kit.dp(4), 0, kit.dp(4));

        LinearLayout card = new LinearLayout(surface.activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        // Space inside the card for the avatar overlap + gap before the name.
        card.setPadding(avatarSize - overhang + kit.dp(10), kit.dp(14), kit.dp(14), kit.dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(person.name + ", " + person.latestBody);
        card.setOnClickListener(view -> surface.conversation.openConversation(person));
        surface.textureEngine.attachGlassControl(card);

        String glowPackage = person.unreadCount > 0
                ? (person.latestUnreadPackage == null || person.latestUnreadPackage.isEmpty()
                    ? person.latestPackage : person.latestUnreadPackage)
                : null;
        String glowLabel = person.unreadCount > 0
                ? (person.latestUnreadAppLabel == null || person.latestUnreadAppLabel.isEmpty()
                    ? person.latestAppLabel : person.latestUnreadAppLabel)
                : null;
        if (glowPackage != null) {
            int glowColor = MothMarketTheme.glowForPackage(glowPackage, glowLabel);
            MothMarketTheme.applyGlow(card, surface.activity, glowColor);
            surface.lights.registerGlowRing(card, glowColor);
        } else {
            card.setBackground(MothMarketTheme.quietCard(surface.activity));
            card.setElevation(kit.dp(2));
        }

        LinearLayout labels = new LinearLayout(surface.activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = kit.text(person.name, 18, UiKit.INK, true);
        labels.addView(name);
        TextView snippet = kit.text(person.latestBody, 14, UiKit.INK, false);
        snippet.setSingleLine(true);
        snippet.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(snippet, kit.topMargin(kit.dp(4)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(0, 0, kit.dp(10), 0);
        card.addView(labels, labelParams);

        LinearLayout trailing = new LinearLayout(surface.activity);
        trailing.setOrientation(LinearLayout.VERTICAL);
        trailing.setGravity(Gravity.END);
        TextView time = kit.text(ChatListPresenter.clockTime(person.latestAt), 12, UiKit.INK, false);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER);
        time.setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(5));
        time.setBackground(MothMarketTheme.roundRect(MothMarketTheme.CHIP, 14f, surface.activity));
        trailing.addView(time, new LinearLayout.LayoutParams(-2, -2));
        if (person.unreadCount > 0) {
            TextView badge = kit.text(String.valueOf(Math.min(person.unreadCount, 99)), 12, UiKit.INK, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(MothMarketTheme.circle(MothMarketTheme.CHIP));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(kit.dp(22), kit.dp(22));
            badgeParams.setMargins(0, kit.dp(8), 0, 0);
            badgeParams.gravity = Gravity.END;
            trailing.addView(badge, badgeParams);
        }
        card.addView(trailing, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2);
        cardParams.setMargins(overhang, 0, 0, 0);
        cardParams.gravity = Gravity.CENTER_VERTICAL;
        shell.addView(card, cardParams);

        ImageView avatar = new ImageView(surface.activity);
        avatar.setImageResource(R.drawable.ic_person_avatar);
        avatar.setContentDescription(person.name + " photo");
        avatar.setElevation(kit.dp(6));
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        shell.addView(avatar, avatarParams);

        return shell;
    }
}
