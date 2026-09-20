package com.textureflow.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.textureflow.BuildConfig;
import com.textureflow.R;
import com.textureflow.connection.ConnectionConfigStore;
import com.textureflow.connection.ConnectionConfig;
import com.textureflow.connection.ConnectionStatusStore;
import com.textureflow.connection.CoreActionClient;
import com.textureflow.connection.LocalCoreActionClient;
import com.textureflow.connection.TextureFlowConnectionController;
import com.textureflow.actions.ActionType;
import com.textureflow.data.ListenerHealthStore;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.notifications.ListenerHealthPolicy;
import com.textureflow.notifications.NotificationHealthJobService;
import com.textureflow.notifications.NotificationRuntime;
import com.textureflow.notifications.NotificationWatchdogScheduler;
import com.textureflow.notifications.TextureNotificationListenerService;
import com.textureflow.policy.AttentionQueuePolicy;
import com.textureflow.texture.SensoryProfile;
import com.textureflow.texture.TextureCue;
import com.textureflow.texture.TextureCueScheduler;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The deliberately small TextureFlow phone surface. */
public final class MainActivity extends Activity {
    public enum ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, STALE }

    public enum SessionState {
        IDLE, LISTENING, THINKING, PRESENTING, AWAITING_CONFIRMATION,
        EXECUTING, RECEIPT, CANCELLED, FAILED
    }

    public enum ReceiptState { DISPATCHED, FAILED, EXPIRED, STALE }

    public interface ActionRequestListener {
        void onConfirmRequested(String proposalId);
        void onCancelRequested(String proposalId);
    }

    private enum Page { CHATS, CHAT, SETTINGS, FLOWS }

    private static final int INK = MothMarketTheme.INK;
    private static final int MUTED = MothMarketTheme.MUTED;
    private static final int TEAL = Color.rgb(38, 118, 110);
    private static final int AMBER = Color.rgb(178, 124, 52);
    private static final int CRIMSON = Color.rgb(142, 61, 58);
    private static final int TRANSPARENT = Color.TRANSPARENT;
    private static final String PREFERENCES = "texture_surface_preferences";
    private static final long SURFACE_REFRESH_MS = 2_000L;
    private static final int AUDIO_PERMISSION_REQUEST = 1002;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HapticTextureEngine textureEngine;
    private TextureBackgroundView backgroundView;
    private ShakeUrgencyController shakeController;
    private ConversationalVoiceController voiceController;
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout chatsPage;
    private FrameLayout chatThreadPage;
    private FrameLayout settingsPage;
    private FrameLayout flowsPage;
    private LinearLayout chatsTab;
    private LinearLayout flowsTab;
    private LinearLayout chatListContainer;
    private EditText searchField;
    private TextView chatsEmpty;
    private final List<PersonTimeline> cachedPeople = new ArrayList<>();
    private String activeConversationKey;

    private EyeOfHorusView eyeView;
    private LinearLayout attentionPanel;
    private TextView attentionMeta;
    private TextView attentionBody;
    private TextView attentionReason;
    private LinearLayout responsePanel;
    private TextView responseSmile;
    private TextView responseTitle;
    private EditText responseEditor;
    private LinearLayout responseOptions;
    private TextView responseStatus;
    private Button talkButton;
    private Button confirmButton;
    private Button cancelButton;

    private LinearLayout conversationMessages;
    private TextView conversationTitle;

    private TextView connectionStatus;
    private TextView sessionStatus;
    private TextView notificationStatus;
    private Button notificationAccessButton;
    private Button coreLinkButton;
    private TextView receiptStatus;
    private TextView receiptDetail;
    private Button profileButton;
    private TextView sensorySummary;
    private Switch audioSwitch;
    private Switch hapticsSwitch;
    private Switch shakeSwitch;
    private Switch reducedTextureSwitch;

    private Page currentPage = Page.CHATS;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private SessionState sessionState = SessionState.IDLE;
    private StoredNotificationEvent currentAttention;
    private final Set<String> handledAttentionKeys = new LinkedHashSet<>();
    private final Map<String, Long> hiddenEventUntil = new LinkedHashMap<>();
    private int activeQueueSize;
    private String currentProposalId;
    private CoreActionClient.Proposal activePhoneProposal;
    private LocalCoreActionClient localActionClient;
    private StoredNotificationEvent activeProposalEvent;
    private boolean phoneActionBusy;
    private ActionRequestListener actionRequestListener;
    private boolean suppressPreferenceCallbacks;
    private boolean coreStartIssued;
    private boolean notificationPermissionRequested;
    private String peopleSignature = "";

    private final Runnable surfaceRefresh = new Runnable() {
        @Override
        public void run() {
            refreshCoreStatus();
            refreshLocalSurface();
            mainHandler.postDelayed(this, SURFACE_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textureEngine = new HapticTextureEngine(this);
        initializeVoice();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MothMarketTheme.BG);
        backgroundView = new TextureBackgroundView(this);
        backgroundView.setReducedTexture(true);
        backgroundView.setAlpha(0f);
        root.addView(backgroundView, matchFrame());

        FrameLayout content = new FrameLayout(this);
        FrameLayout.LayoutParams contentParams = matchFrame();
        contentParams.setMargins(0, 0, 0, dp(104));
        root.addView(content, contentParams);

        chatsPage = buildChatsPage();
        chatThreadPage = buildChatThreadPage();
        settingsPage = buildSettingsPage();
        flowsPage = buildFlowsPage();
        content.addView(chatsPage, matchFrame());
        content.addView(chatThreadPage, matchFrame());
        content.addView(settingsPage, matchFrame());
        content.addView(flowsPage, matchFrame());
        root.addView(buildNavigation(), navigationParams());

        textureEngine.setListener(new TextureCueScheduler.Listener() {
            @Override
            public void onCueStarted(TextureCue cue, String correlationId) {
                backgroundView.showCue(cue, reducedTextureSwitch == null
                        || reducedTextureSwitch.isChecked()
                        || textureEngine.getProfile().reducesContinuousTexture());
                EyeOfHorusView.State state = eyeStateForCue(cue);
                if (state != null && eyeView != null) eyeView.setState(state);
            }

            @Override
            public void onCueFinished(TextureCue cue, String correlationId, boolean cancelled) {
                if (eyeView != null) eyeView.setState(eyeStateForSurface());
            }
        });

        setContentView(root);
        restoreSensoryPreferences();
        shakeController = new ShakeUrgencyController(this, this::speakUrgentItem);
        showPage(Page.CHATS);
        renderConnection(ConnectionState.DISCONNECTED, "Waiting for the Core link");
        renderPeople(Collections.emptyList());
    }

    private FrameLayout buildChatsPage() {
        LinearLayout content = pageColumn();
        content.setPadding(dp(20), dp(28), dp(20), dp(12));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.moth_logo);
        logo.setContentDescription("Moth Market");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topBar.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageButton more = circleIconButton(R.drawable.ic_more_vert);
        more.setContentDescription("Settings");
        more.setOnClickListener(view -> showPage(Page.SETTINGS));
        topBar.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageButton add = circleIconButton(R.drawable.ic_plus);
        add.setContentDescription("New chat");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        addParams.setMargins(dp(10), 0, 0, 0);
        add.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("New chat")
                .setMessage("Starting a new chat from Moth Market is coming next. For now, open a person from the list.")
                .setPositiveButton("OK", null)
                .show());
        topBar.addView(add, addParams);
        content.addView(topBar, matchWrap());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(18), dp(12), dp(18), dp(12));
        searchRow.setBackground(MothMarketTheme.roundRect(MothMarketTheme.SEARCH, 26f, this));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setContentDescription("Search");
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        searchField = new EditText(this);
        searchField.setHint("Search");
        searchField.setHintTextColor(MUTED);
        searchField.setTextColor(INK);
        searchField.setBackground(new ColorDrawable(TRANSPARENT));
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setPadding(dp(10), dp(2), dp(4), dp(2));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChatList(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.setMargins(0, dp(22), 0, dp(22));
        content.addView(searchRow, searchParams);

        chatListContainer = new LinearLayout(this);
        chatListContainer.setOrientation(LinearLayout.VERTICAL);
        chatListContainer.setClipChildren(false);
        chatListContainer.setClipToPadding(false);
        content.addView(chatListContainer, matchWrap());

        chatsEmpty = text("No conversations yet.", 16, MUTED, false);
        chatsEmpty.setGravity(Gravity.CENTER);
        chatsEmpty.setPadding(dp(12), dp(28), dp(12), dp(28));
        chatsEmpty.setVisibility(View.GONE);

        // Keep legacy action widgets attached but hidden so Core/voice paths stay safe.
        attachHiddenLegacyControls(content);

        return scrollPage(content);
    }

    private void attachHiddenLegacyControls(LinearLayout content) {
        eyeView = new EyeOfHorusView(this);
        eyeView.setVisibility(View.GONE);
        content.addView(eyeView, new LinearLayout.LayoutParams(1, 1));

        attentionPanel = surface(dp(30));
        attentionPanel.setVisibility(View.GONE);
        attentionMeta = text("", 14, MUTED, true);
        attentionPanel.addView(attentionMeta);
        attentionBody = text("", 20, INK, false);
        attentionPanel.addView(attentionBody);
        attentionReason = text("", 13, MUTED, false);
        attentionPanel.addView(attentionReason);
        content.addView(attentionPanel, matchWrap());

        responsePanel = surface(dp(32));
        responsePanel.setVisibility(View.GONE);
        responseSmile = text("🙂", 42, INK, false);
        responsePanel.addView(responseSmile);
        responseTitle = text("Response", 14, MUTED, true);
        responsePanel.addView(responseTitle);
        responseEditor = new EditText(this);
        responseEditor.setVisibility(View.GONE);
        responsePanel.addView(responseEditor);
        responseOptions = new LinearLayout(this);
        responseOptions.setOrientation(LinearLayout.HORIZONTAL);
        responseOptions.setVisibility(View.GONE);
        responsePanel.addView(responseOptions);
        responseStatus = text("", 13, MUTED, false);
        responseStatus.setVisibility(View.GONE);
        responsePanel.addView(responseStatus);
        talkButton = compactButton("Talk", TEAL);
        talkButton.setVisibility(View.GONE);
        talkButton.setOnClickListener(this::startVoiceTurn);
        responsePanel.addView(talkButton);
        confirmButton = compactButton("Confirm", AMBER);
        confirmButton.setVisibility(View.GONE);
        confirmButton.setOnClickListener(this::requestConfirmation);
        responsePanel.addView(confirmButton);
        cancelButton = compactButton("Cancel", CRIMSON);
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(this::requestCancellation);
        responsePanel.addView(cancelButton);
        content.addView(responsePanel, matchWrap());
    }

    private FrameLayout buildChatThreadPage() {
        LinearLayout content = pageColumn();
        content.setPadding(dp(14), dp(14), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = compactButton("Back", TEAL);
        back.setOnClickListener(view -> closeConversation());
        header.addView(back, narrowStart());
        conversationTitle = text("", 22, INK, true);
        conversationTitle.setAccessibilityHeading(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        header.addView(conversationTitle, titleParams);
        content.addView(header, matchWrap());

        conversationMessages = new LinearLayout(this);
        conversationMessages.setOrientation(LinearLayout.VERTICAL);
        conversationMessages.setPadding(0, dp(12), 0, dp(12));
        content.addView(conversationMessages, matchWrap());
        return scrollPage(content);
    }

    private FrameLayout buildFlowsPage() {
        LinearLayout content = pageColumn();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(48), dp(24), dp(24));
        ImageView moth = new ImageView(this);
        moth.setImageResource(R.drawable.moth_logo);
        moth.setContentDescription("Moth Market Flows");
        content.addView(moth, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView title = text("Flows", 28, INK, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title, topMargin(dp(16)));
        TextView body = text("Flows will live here next. For now, Chats is ready.", 16, MUTED, false);
        body.setGravity(Gravity.CENTER);
        content.addView(body, topMargin(dp(10)));
        return scrollPage(content);
    }

    private ImageButton circleIconButton(int iconRes) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setBackground(MothMarketTheme.circle(MothMarketTheme.CHIP));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        textureEngine.attachGlassControl(button);
        return button;
    }

    private FrameLayout buildSettingsPage() {
        LinearLayout content = pageColumn();
        Button back = compactButton("Back to chats", TEAL);
        back.setOnClickListener(view -> showPage(Page.CHATS));
        content.addView(back, narrowStart());
        TextView title = text("Settings", 28, INK, true);
        title.setAccessibilityHeading(true);
        content.addView(title, topMargin(dp(8)));

        LinearLayout connectionPanel = surface(dp(28));
        connectionPanel.addView(sectionHeading("Connection"));
        connectionStatus = value("Disconnected");
        connectionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(connectionStatus);
        sessionStatus = supportingValue("Ready");
        sessionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(sessionStatus, topMargin(dp(5)));
        notificationStatus = supportingValue("Notification access has not been checked.");
        notificationStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(notificationStatus, topMargin(dp(8)));
        notificationAccessButton = button("Notification access", TEAL);
        notificationAccessButton.setOnClickListener(this::openNotificationAccessSettings);
        connectionPanel.addView(notificationAccessButton, topMargin(dp(12)));
        coreLinkButton = button("Core connection", TEAL);
        coreLinkButton.setOnClickListener(this::openCoreSetup);
        connectionPanel.addView(coreLinkButton);
        content.addView(connectionPanel, wideWithTop(dp(18)));

        LinearLayout sensoryPanel = surface(dp(28));
        sensoryPanel.addView(sectionHeading("Sensory profile"));
        profileButton = button("Profile: Balanced", TEAL);
        profileButton.setOnClickListener(this::cycleProfile);
        sensoryPanel.addView(profileButton, topMargin(dp(5)));

        audioSwitch = settingSwitch("Texture audio");
        audioSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                textureEngine.setAudioEnabled(checked);
                updateSensorySummary();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(audioSwitch);

        hapticsSwitch = settingSwitch("Haptic texture");
        hapticsSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                textureEngine.setHapticsEnabled(checked);
                updateSensorySummary();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(hapticsSwitch);

        shakeSwitch = settingSwitch("Shake for urgent items");
        shakeSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                updateShakeLifecycle();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(shakeSwitch);

        reducedTextureSwitch = settingSwitch("Reduced visual texture");
        reducedTextureSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                backgroundView.setReducedTexture(checked);
                eyeView.setReducedMotion(checked
                        || textureEngine.getProfile().reducesContinuousTexture());
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(reducedTextureSwitch);

        sensorySummary = supportingValue("");
        sensorySummary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        sensoryPanel.addView(sensorySummary, topMargin(dp(8)));
        Button testTextures = button("Test textures", TEAL);
        testTextures.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_media_play, 0, 0, 0);
        testTextures.setOnClickListener(view -> {
            textureEngine.playCarpetScroll(view);
            mainHandler.postDelayed(() -> textureEngine.playGlassTouch(view), 650L);
        });
        sensoryPanel.addView(testTextures, topMargin(dp(10)));
        content.addView(sensoryPanel, wideWithTop(dp(12)));

        LinearLayout receiptPanel = surface(dp(28));
        receiptPanel.addView(sectionHeading("Latest action"));
        receiptStatus = value("No execution receipt");
        receiptStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        receiptPanel.addView(receiptStatus);
        receiptDetail = supportingValue("Only Android-confirmed results appear here.");
        receiptPanel.addView(receiptDetail, topMargin(dp(6)));
        content.addView(receiptPanel, wideWithTop(dp(12)));
        return scrollPage(content);
    }

    private LinearLayout buildNavigation() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(10), dp(6), dp(10), dp(6));
        shell.setBackground(MothMarketTheme.roundRect(MothMarketTheme.NAV, 30f, this));
        shell.setElevation(dp(8));
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        chatsTab = navTab("chat", R.drawable.ic_chats, Page.CHATS, true);
        flowsTab = navTab("flow", R.drawable.moth_logo, Page.FLOWS, false);
        shell.addView(chatsTab, navItemParams());
        shell.addView(flowsTab, navItemParams());
        return shell;
    }

    private LinearLayout navTab(String label, int icon, Page page, boolean selected) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setPadding(dp(18), dp(8), dp(18), dp(8));
        tab.setBackground(MothMarketTheme.navPill(this, selected));
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setSelected(selected);
        tab.setOnClickListener(view -> showPage(page));
        tab.setClipChildren(false);
        tab.setClipToPadding(false);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setContentDescription(label);
        tab.addView(iconView, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView caption = text(label, 11, selected ? INK : MUTED, selected);
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.topMargin = dp(3);
        captionParams.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(caption, captionParams);
        textureEngine.attachGlassControl(tab);
        return tab;
    }

    @Override
    protected void onResume() {
        super.onResume();
        textureEngine.setForeground(true);
        voiceController.start();
        refreshNotificationReader();
        updateShakeLifecycle();
        mainHandler.removeCallbacks(surfaceRefresh);
        mainHandler.post(surfaceRefresh);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (currentPage == Page.CHAT) {
            closeConversation();
            return;
        }
        if (currentPage == Page.SETTINGS || currentPage == Page.FLOWS) {
            showPage(Page.CHATS);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (shakeController != null) shakeController.stop();
        voiceController.stop();
        textureEngine.setForeground(false);
        mainHandler.removeCallbacks(surfaceRefresh);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        updateShakeLifecycle();
    }

    @Override
    protected void onDestroy() {
        if (shakeController != null) shakeController.release();
        voiceController.release();
        actionExecutor.shutdownNow();
        textureEngine.release();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void setActionRequestListener(ActionRequestListener listener) {
        actionRequestListener = listener;
    }

    private void showPage(Page page) {
        currentPage = page;
        chatsPage.setVisibility(page == Page.CHATS ? View.VISIBLE : View.GONE);
        chatThreadPage.setVisibility(page == Page.CHAT ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == Page.SETTINGS ? View.VISIBLE : View.GONE);
        flowsPage.setVisibility(page == Page.FLOWS ? View.VISIBLE : View.GONE);
        selectNavTab(chatsTab, page == Page.CHATS || page == Page.CHAT);
        selectNavTab(flowsTab, page == Page.FLOWS);
        View bump = page == Page.FLOWS ? flowsTab : chatsTab;
        textureEngine.playBoundaryBump(bump);
    }

    private void selectNavTab(LinearLayout tab, boolean selected) {
        tab.setSelected(selected);
        tab.setBackground(MothMarketTheme.navPill(this, selected));
        if (tab.getChildCount() > 1 && tab.getChildAt(1) instanceof TextView caption) {
            caption.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            caption.setTextColor(selected ? INK : MUTED);
        }
    }

    private void refreshNotificationReader() {
        boolean granted = hasNotificationAccess();
        notificationAccessButton.setText(granted ? "Review notification access" : "Enable notification access");
        long now = System.currentTimeMillis();
        ListenerHealthStore.Snapshot health = NotificationRuntime.get(this).health().read();
        boolean live = TextureNotificationListenerService.hasLiveConnection();
        notificationStatus.setText(
                ListenerHealthPolicy.statusLabel(health, now, granted, live));
        if (!granted) {
            renderConnection(ConnectionState.DISCONNECTED, "Notification access required");
            clearAttention();
            return;
        }

        NotificationHealthJobService.schedule(this);
        NotificationWatchdogScheduler.schedule(this);
        TextureNotificationListenerService.requestRebindNow(this);
        startCoreLink();
        requestNotificationPermissionIfNeeded();
        NotificationRuntime runtime = NotificationRuntime.get(this);
        coreLinkButton.setText(ConnectionConfigStore.isStub(this, runtime.getDeviceId())
                ? "Local only (Convex stubbed)"
                : ConnectionConfigStore.isConfigured(this, runtime.getDeviceId())
                ? "Reconnect Core" : "Configure Core");
        if (ConnectionConfigStore.isStub(this, runtime.getDeviceId())) {
            connectionStatus.setText("Local stub · Convex and VoiceOS offline");
            sessionStatus.setText("On-device only");
        }
        refreshLocalSurface();
    }

    private void refreshLocalSurface() {
        NotificationRuntime runtime = NotificationRuntime.get(this);
        List<StoredNotificationEvent> live = runtime.notifications().getLiveEvents();
        List<StoredNotificationEvent> queue = attentionQueue(live);
        activeQueueSize = queue.size();
        StoredNotificationEvent attention = queue.isEmpty() ? null : queue.get(0);
        boolean actionInProgress = activePhoneProposal != null || phoneActionBusy
                || sessionState == SessionState.EXECUTING;
        if (!actionInProgress) {
            if (attention == null) {
                clearAttention();
            } else if (currentAttention == null
                    || !currentAttention.getEventId().equals(attention.getEventId())
                    || currentAttention.getVersion() != attention.getVersion()) {
                currentAttention = attention;
                renderAttention(attention.getEventId(), attention.getSenderName(), attention.getAppLabel(),
                        attention.getBody(), attention.getPriorityReason(),
                        "URGENT".equals(attention.getPriorityLevel()));
                renderResponseOptions(attention);
            }
        }

        List<StoredNotificationEvent> recent = runtime.notifications().getRecentEvents(100);
        String signature = eventSignature(recent);
        if (!signature.equals(peopleSignature)) {
            peopleSignature = signature;
            renderPeople(recent);
        }
    }

    private List<StoredNotificationEvent> attentionQueue(List<StoredNotificationEvent> events) {
        List<StoredNotificationEvent> queue = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (StoredNotificationEvent event : events) {
            if (!AttentionQueuePolicy.shouldSurface(
                    event.getPriorityLevel(), event.hasCapability("REPLY"))) continue;
            Long hiddenUntil = hiddenEventUntil.get(event.getEventId());
            if (hiddenUntil != null && hiddenUntil > now) continue;
            if (hiddenUntil != null) hiddenEventUntil.remove(event.getEventId());
            if (!handledAttentionKeys.contains(attentionKey(event))) queue.add(event);
        }
        queue.sort((left, right) -> {
            int urgency = Boolean.compare(
                    "URGENT".equals(right.getPriorityLevel()),
                    "URGENT".equals(left.getPriorityLevel()));
            if (urgency != 0) return urgency;
            int replyability = Boolean.compare(
                    right.hasCapability("REPLY"), left.hasCapability("REPLY"));
            if (replyability != 0) return replyability;
            int priority = Double.compare(right.getPriorityScore(), left.getPriorityScore());
            return priority != 0 ? priority : Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
        });
        return queue;
    }

    private void renderResponseOptions(StoredNotificationEvent event) {
        responseOptions.removeAllViews();
        List<String> actions = new ArrayList<>();
        if (event.hasCapability("REPLY")) {
            actions.add("Send");
            responseEditor.setEnabled(true);
            responseEditor.setHint("Edit suggested reply");
            responseEditor.setText(suggestReply(event));
            responseEditor.setSelection(responseEditor.length());
            responseTitle.setText("Suggested reply");
        } else {
            responseEditor.setVisibility(View.GONE);
            responseTitle.setText("Choose an action");
        }
        if (event.hasCapability("SNOOZE")) actions.add("Later");
        if (event.hasCapability("DISMISS")) actions.add("Done");
        for (String action : actions) {
            Button option = compactButton(action, "Send".equals(action) ? TEAL : AMBER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            option.setLayoutParams(params);
            option.setOnClickListener(view -> selectResponseAction(action));
            responseOptions.addView(option);
        }
    }

    private void selectResponseAction(String action) {
        responseStatus.setVisibility(View.VISIBLE);
        if (currentAttention == null || phoneActionBusy) return;
        String capability = "Send".equals(action) ? "REPLY"
                : "Later".equals(action) ? "SNOOZE" : "DISMISS";
        if (!currentAttention.hasCapability(capability)) {
            responseStatus.setText(action + " is not available for this notification");
            voiceController.speak(action + " is not available for this notification.");
            return;
        }
        if ("Send".equals(action) && responseEditor.getText().toString().trim().isEmpty()) {
            responseEditor.requestFocus();
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.showSoftInput(responseEditor, InputMethodManager.SHOW_IMPLICIT);
            responseStatus.setText("Write a reply first");
            return;
        }
        ActionType type = "Send".equals(action) ? ActionType.REPLY
                : "Later".equals(action) ? ActionType.SNOOZE : ActionType.DISMISS;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (type == ActionType.REPLY) payload.put("message", responseEditor.getText().toString().trim());
        if (type == ActionType.SNOOZE) payload.put("minutes", 60L);
        preparePhoneAction(currentAttention, type, payload);
        textureEngine.playGlassTouch(responsePanel);
    }

    private void preparePhoneAction(
            StoredNotificationEvent event, ActionType type, Map<String, Object> payload) {
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(
                    this, NotificationRuntime.get(this).getDeviceId());
        } catch (RuntimeException missingConfig) {
            TextureFlowConnectionController.useLocalStub(this);
            config = ConnectionConfigStore.load(
                    this, NotificationRuntime.get(this).getDeviceId());
        }

        phoneActionBusy = true;
        setResponseActionsEnabled(false);
        responseStatus.setText("Preparing exact preview");
        ConnectionConfig finalConfig = config;
        actionExecutor.execute(() -> {
            try {
                CoreActionClient.Proposal proposal;
                if (finalConfig.isStub()) {
                    if (localActionClient == null) {
                        localActionClient = new LocalCoreActionClient(this, finalConfig);
                    }
                    proposal = localActionClient.create(event, type, payload);
                } else {
                    String actionToken = ConnectionConfigStore.loadUserActionToken(this);
                    if (actionToken == null || actionToken.trim().isEmpty()) {
                        runOnUiThread(() -> {
                            finishPhoneActionFailure(
                                    "Add the User action token in Settings to use live Core.");
                            showPage(Page.SETTINGS);
                        });
                        return;
                    }
                    CoreActionClient client = new CoreActionClient(finalConfig, actionToken);
                    proposal = client.create(event, type, payload);
                }
                CoreActionClient.Proposal ready = proposal;
                runOnUiThread(() -> renderPhoneProposal(event, ready));
            } catch (Exception failure) {
                String reason = failure.getMessage();
                runOnUiThread(() -> finishPhoneActionFailure(reason == null
                        ? "Could not prepare that action."
                        : reason));
            }
        });
    }

    private static ReceiptState receiptState(String status) {
        if ("DISPATCHED".equals(status)) return ReceiptState.DISPATCHED;
        if ("EXPIRED".equals(status)) return ReceiptState.EXPIRED;
        if ("STALE".equals(status)) return ReceiptState.STALE;
        return ReceiptState.FAILED;
    }

    private void renderPhoneProposal(
            StoredNotificationEvent event, CoreActionClient.Proposal proposal) {
        phoneActionBusy = false;
        activePhoneProposal = proposal;
        activeProposalEvent = event;
        currentProposalId = proposal.proposalId();
        sessionState = SessionState.AWAITING_CONFIRMATION;
        responseOptions.setVisibility(View.GONE);
        responseStatus.setText("Exact preview: " + proposal.spokenPreview());
        responseStatus.setVisibility(View.VISIBLE);
        confirmButton.setText(proposal.actionType() == ActionType.REPLY ? "Send now" : "Confirm");
        confirmButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setText("Back to actions");
        confirmButton.setEnabled(true);
        cancelButton.setEnabled(true);
        if (proposal.actionType() == ActionType.REPLY) {
            responseTitle.setText("Proposed reply");
            responseEditor.setEnabled(true);
            responseEditor.setVisibility(View.VISIBLE);
        }
        eyeView.setState(EyeOfHorusView.State.AWAITING_CONFIRMATION);
        textureEngine.emit(TextureCue.PROPOSAL_READY, proposal.proposalId(), responsePanel);
        voiceController.speakAndListen(proposal.spokenPreview()
                + " Say confirm to authorize it, change it, or cancel.");
    }

    private void setResponseActionsEnabled(boolean enabled) {
        for (int index = 0; index < responseOptions.getChildCount(); index++) {
            responseOptions.getChildAt(index).setEnabled(enabled);
        }
    }

    private void finishPhoneActionFailure(String message) {
        phoneActionBusy = false;
        setResponseActionsEnabled(true);
        confirmButton.setEnabled(true);
        cancelButton.setEnabled(true);
        responseStatus.setText(message);
        responseStatus.setVisibility(View.VISIBLE);
        textureEngine.emit(TextureCue.ACTION_FAILED, "phone-action", responsePanel);
    }

    private static String suggestReply(StoredNotificationEvent event) {
        String body = emptyFallback(event.getBody(), "").toLowerCase(Locale.US);
        if (body.contains("where") || body.contains("when")) {
            return "Thanks for asking. Let me confirm and get back to you shortly.";
        }
        if (body.contains("can you") || body.contains("could you") || body.contains("?")) {
            return "Yes, that works for me. Thanks for checking.";
        }
        return "Thanks for letting me know.";
    }

    private void renderPeople(List<StoredNotificationEvent> events) {
        Map<String, PersonTimeline> people = new LinkedHashMap<>();
        Set<String> liveIds = liveEventIds();
        for (StoredNotificationEvent event : events) {
            if (!isConversationCandidate(event)) continue;
            String name = emptyFallback(event.getSenderName(), event.getConversationLabel());
            if (name == null || name.trim().isEmpty()) continue;
            if ("unknown sender".equalsIgnoreCase(name.trim())) continue;
            String key = normalizePersonKey(name);
            PersonTimeline person = people.get(key);
            if (person == null) {
                person = new PersonTimeline(name.trim());
                people.put(key, person);
            }
            person.events.add(event);
            person.apps.add(emptyFallback(event.getAppLabel(), event.getPackageName()));
            if (event.getUpdatedAt() >= person.latestAt) {
                person.latestAt = event.getUpdatedAt();
                person.latestPackage = event.getPackageName();
                person.latestAppLabel = event.getAppLabel();
                person.latestBody = emptyFallback(event.getBody(), "Notification");
            }
            if (liveIds.contains(event.getEventId())) {
                person.unreadCount++;
                if (event.getUpdatedAt() >= person.latestUnreadAt) {
                    person.latestUnreadAt = event.getUpdatedAt();
                    person.latestUnreadPackage = event.getPackageName();
                    person.latestUnreadAppLabel = event.getAppLabel();
                }
            }
        }
        List<PersonTimeline> ordered = new ArrayList<>(people.values());
        ordered.sort((left, right) -> Long.compare(right.latestAt, left.latestAt));
        if (ordered.isEmpty()) {
            ordered.add(demoAlex());
        }
        cachedPeople.clear();
        cachedPeople.addAll(ordered);
        filterChatList(searchField == null ? "" : searchField.getText().toString());
    }

    private boolean isConversationCandidate(StoredNotificationEvent event) {
        if (event.hasCapability("REPLY")) return true;
        String hay = ((event.getPackageName() == null ? "" : event.getPackageName()) + " "
                + (event.getAppLabel() == null ? "" : event.getAppLabel())).toLowerCase(Locale.US);
        return hay.contains("whatsapp")
                || hay.contains("telegram")
                || hay.contains("signal")
                || hay.contains("instagram")
                || hay.contains("messenger")
                || hay.contains("facebook.orca")
                || hay.contains("sms")
                || hay.contains("mms")
                || hay.contains("messaging")
                || hay.contains("imessage")
                || hay.contains("discord")
                || hay.contains("slack")
                || hay.contains("viber")
                || hay.contains("line");
    }

    private Set<String> liveEventIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (StoredNotificationEvent event
                : NotificationRuntime.get(this).notifications().getLiveEvents()) {
            ids.add(event.getEventId());
        }
        return ids;
    }

    private PersonTimeline demoAlex() {
        PersonTimeline alex = new PersonTimeline("Alex");
        alex.latestAt = System.currentTimeMillis();
        alex.latestBody = "😊 See you in 5 minutes";
        alex.latestPackage = "com.whatsapp";
        alex.latestAppLabel = "WhatsApp";
        alex.latestUnreadPackage = "com.whatsapp";
        alex.latestUnreadAppLabel = "WhatsApp";
        alex.latestUnreadAt = alex.latestAt;
        alex.unreadCount = 1;
        alex.apps.add("WhatsApp");
        alex.demo = true;
        alex.demoMessages.add(new ChatBubble(false, "Hey — still good for later?", alex.latestAt - 12 * 60_000L));
        alex.demoMessages.add(new ChatBubble(true, "Yes, heading over now.", alex.latestAt - 8 * 60_000L));
        alex.demoMessages.add(new ChatBubble(false, "😊 See you in 5 minutes", alex.latestAt));
        return alex;
    }

    private void filterChatList(String query) {
        if (chatListContainer == null) return;
        chatListContainer.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<PersonTimeline> visible = new ArrayList<>();
        for (PersonTimeline person : cachedPeople) {
            if (needle.isEmpty()
                    || person.name.toLowerCase(Locale.US).contains(needle)
                    || person.latestBody.toLowerCase(Locale.US).contains(needle)
                    || String.join(" ", person.apps).toLowerCase(Locale.US).contains(needle)) {
                visible.add(person);
            }
        }
        if (visible.isEmpty()) {
            chatsEmpty.setText(needle.isEmpty() ? "No conversations yet." : "No matches.");
            chatListContainer.addView(chatsEmpty, matchWrap());
            chatsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        chatsEmpty.setVisibility(View.GONE);
        // Keep empty slots aligned with the card body (avatar only peeks past the left edge).
        final int avatarOverhang = dp(8);
        for (PersonTimeline person : visible) {
            LinearLayout.LayoutParams rowParams = wideWithBottom(dp(14));
            chatListContainer.addView(personRow(person), rowParams);
        }
        int placeholders = Math.max(0, 5 - visible.size());
        for (int i = 0; i < placeholders; i++) {
            View slot = new View(this);
            slot.setBackground(MothMarketTheme.placeholderSlot(this));
            slot.setAlpha(0.72f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
            params.setMargins(avatarOverhang, 0, 0, dp(14));
            chatListContainer.addView(slot, params);
        }
    }

    private View personRow(PersonTimeline person) {
        final int avatarSize = dp(56);
        // Card stretches under most of the avatar; only a small slice peeks past the left edge.
        final int overhang = dp(8);

        FrameLayout shell = new FrameLayout(this);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        shell.setPadding(0, dp(4), 0, dp(4));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        // Space inside the card for the avatar overlap + gap before the name.
        card.setPadding(avatarSize - overhang + dp(10), dp(14), dp(14), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(person.name + ", " + person.latestBody);
        card.setOnClickListener(view -> openConversation(person));
        textureEngine.attachGlassControl(card);

        String glowPackage = person.unreadCount > 0
                ? (person.latestUnreadPackage == null || person.latestUnreadPackage.isEmpty()
                    ? person.latestPackage : person.latestUnreadPackage)
                : null;
        String glowLabel = person.unreadCount > 0
                ? (person.latestUnreadAppLabel == null || person.latestUnreadAppLabel.isEmpty()
                    ? person.latestAppLabel : person.latestUnreadAppLabel)
                : null;
        if (glowPackage != null) {
            MothMarketTheme.applyGlow(card, this,
                    MothMarketTheme.glowForPackage(glowPackage, glowLabel));
        } else {
            card.setBackground(MothMarketTheme.quietCard(this));
            card.setElevation(dp(2));
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(person.name, 18, INK, true);
        labels.addView(name);
        TextView snippet = text(person.latestBody, 14, INK, false);
        snippet.setSingleLine(true);
        snippet.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(snippet, topMargin(dp(4)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(0, 0, dp(10), 0);
        card.addView(labels, labelParams);

        LinearLayout trailing = new LinearLayout(this);
        trailing.setOrientation(LinearLayout.VERTICAL);
        trailing.setGravity(Gravity.END);
        TextView time = text(clockTime(person.latestAt), 12, INK, false);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER);
        time.setPadding(dp(10), dp(5), dp(10), dp(5));
        time.setBackground(MothMarketTheme.roundRect(MothMarketTheme.CHIP, 14f, this));
        trailing.addView(time, new LinearLayout.LayoutParams(-2, -2));
        if (person.unreadCount > 0) {
            TextView badge = text(String.valueOf(Math.min(person.unreadCount, 99)), 12, INK, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(MothMarketTheme.circle(MothMarketTheme.CHIP));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            badgeParams.setMargins(0, dp(8), 0, 0);
            badgeParams.gravity = Gravity.END;
            trailing.addView(badge, badgeParams);
        }
        card.addView(trailing, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2);
        cardParams.setMargins(overhang, 0, 0, 0);
        cardParams.gravity = Gravity.CENTER_VERTICAL;
        shell.addView(card, cardParams);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_person_avatar);
        avatar.setContentDescription(person.name + " photo");
        avatar.setElevation(dp(6));
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        shell.addView(avatar, avatarParams);

        return shell;
    }

    private void openConversation(PersonTimeline person) {
        activeConversationKey = normalizePersonKey(person.name);
        conversationTitle.setText(person.name);
        conversationMessages.removeAllViews();
        if (person.demo) {
            for (ChatBubble bubble : person.demoMessages) {
                conversationMessages.addView(messageBubble(bubble.outbound, bubble.body, bubble.at),
                        wideWithBottom(dp(8)));
            }
        } else {
            List<StoredNotificationEvent> events = new ArrayList<>(person.events);
            events.sort(Comparator.comparingLong(StoredNotificationEvent::getUpdatedAt));
            for (StoredNotificationEvent event : events) {
                String body = emptyFallback(event.getBody(), "Notification content unavailable");
                conversationMessages.addView(
                        messageBubble(false, body, event.getUpdatedAt()),
                        wideWithBottom(dp(8)));
            }
        }
        showPage(Page.CHAT);
        textureEngine.playBoundaryBump(conversationMessages);
    }

    private View messageBubble(boolean outbound, String body, long at) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(outbound ? Gravity.END : Gravity.START);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(8));
        bubble.setBackground(MothMarketTheme.roundRect(
                outbound ? MothMarketTheme.BUBBLE_OUT : MothMarketTheme.BUBBLE_IN, 18f, this));
        bubble.setElevation(dp(1));
        TextView bodyView = text(body, 16, INK, false);
        bodyView.setLineSpacing(dp(2), 1f);
        bubble.addView(bodyView);
        TextView meta = text(clockTime(at), 11, MUTED, false);
        meta.setGravity(outbound ? Gravity.END : Gravity.START);
        bubble.addView(meta, topMargin(dp(4)));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.gravity = outbound ? Gravity.END : Gravity.START;
        bubbleParams.setMargins(outbound ? dp(48) : 0, 0, outbound ? 0 : dp(48), 0);
        wrap.addView(bubble, bubbleParams);
        return wrap;
    }

    private void closeConversation() {
        activeConversationKey = null;
        showPage(Page.CHATS);
        textureEngine.playBoundaryBump(chatListContainer);
    }

    private void initializeVoice() {
        voiceController = new ConversationalVoiceController(this,
                new ConversationalVoiceController.Callback() {
                    @Override
                    public void onStateChanged(ConversationalVoiceController.State state) {
                        boolean speaking = state == ConversationalVoiceController.State.SPEAKING;
                        textureEngine.setSpeechActive(speaking);
                        if (talkButton == null) return;
                        switch (state) {
                            case LISTENING -> {
                                sessionState = SessionState.LISTENING;
                                sessionStatus.setText("Listening");
                                talkButton.setText("Done");
                                eyeView.setState(EyeOfHorusView.State.LISTENING);
                                textureEngine.emit(TextureCue.LISTENING_STARTED,
                                        "phone-voice", talkButton);
                            }
                            case PROCESSING -> {
                                sessionState = SessionState.THINKING;
                                sessionStatus.setText("Understanding");
                                talkButton.setText("Talk");
                                eyeView.setState(EyeOfHorusView.State.THINKING);
                            }
                            case SPEAKING -> {
                                sessionState = SessionState.PRESENTING;
                                sessionStatus.setText("Speaking · tap Talk to interrupt");
                                talkButton.setText("Interrupt");
                            }
                            case IDLE, STOPPED -> {
                                talkButton.setText("Talk");
                                if (sessionState == SessionState.LISTENING
                                        || sessionState == SessionState.THINKING) {
                                    sessionState = SessionState.IDLE;
                                }
                                eyeView.setState(eyeStateForSurface());
                            }
                            default -> talkButton.setText("Talk");
                        }
                    }

                    @Override
                    public void onPartialUtterance(String utterance) {
                        responseStatus.setText("Listening…");
                        responseStatus.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFinalUtterance(String utterance) {
                        handleVoiceUtterance(utterance);
                    }

                    @Override
                    public void onFailure(ConversationalVoiceController.Failure failure) {
                        if (failure == ConversationalVoiceController.Failure.RECORD_AUDIO_PERMISSION_REQUIRED) {
                            responseStatus.setText("Microphone permission is needed for conversation");
                            responseStatus.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void speakUrgentItem() {
        ConversationalVoiceController.State voiceState = voiceController.getState();
        if (voiceState == ConversationalVoiceController.State.SPEAKING
                || voiceState == ConversationalVoiceController.State.LISTENING
                || voiceState == ConversationalVoiceController.State.PROCESSING) {
            startVoiceTurn(talkButton);
            return;
        }
        List<StoredNotificationEvent> queue = attentionQueue(
                NotificationRuntime.get(this).notifications().getLiveEvents());
        StoredNotificationEvent urgent = queue.isEmpty() ? null : queue.get(0);
        String message;
        if (urgent == null) {
            message = "You are all caught up.";
        } else {
            message = "Urgent from " + emptyFallback(urgent.getSenderName(), "someone")
                    + " on " + emptyFallback(urgent.getAppLabel(), "your phone") + ". "
                    + emptyFallback(urgent.getBody(), "Open TextureFlow for details.");
            showPage(Page.CHATS);
            textureEngine.emit(TextureCue.ATTENTION_URGENT, urgent.getEventId(), attentionPanel);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) voiceController.speakAndListen(message);
        else voiceController.speak(message);
    }

    private void startVoiceTurn(View source) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
            return;
        }
        voiceController.tapToListenOrInterrupt();
        textureEngine.playGlassTouch(source == null ? responsePanel : source);
    }

    private void handleVoiceUtterance(String utterance) {
        String spoken = utterance == null ? "" : utterance.trim();
        if (spoken.isEmpty()) return;
        String normalized = spoken.toLowerCase(Locale.US)
                .replaceAll("[\\s.!?]+$", "").trim();

        String dictatedReply = VoiceCommandParser.replyDraft(spoken, normalized);
        if (dictatedReply != null && currentAttention != null) {
            if (!currentAttention.hasCapability("REPLY")) {
                voiceController.speakAndListen(
                        "This notification cannot accept a reply. Say snooze this, done, or next.");
                return;
            }
            responseEditor.setVisibility(View.VISIBLE);
            responseEditor.setText(dictatedReply);
            responseEditor.setSelection(responseEditor.length());
            responseTitle.setText(activePhoneProposal == null ? "Proposed reply" : "Revised reply");
            responseStatus.setText("Preparing the exact reply preview");
            responseStatus.setVisibility(View.VISIBLE);
            if (activePhoneProposal == null) selectResponseAction("Send");
            else confirmPhoneProposal();
            return;
        }

        if (activePhoneProposal != null && (normalized.equals("confirm")
                || normalized.equals("yes") || normalized.equals("go ahead")
                || normalized.equals("do it") || normalized.equals("send")
                || normalized.equals("send it") || normalized.equals("send that")
                || normalized.equals("send now") || normalized.equals("yes send")
                || normalized.equals("confirm and send")
                || normalized.equals("review changes"))) {
            requestConfirmation(confirmButton);
            return;
        }
        if (activePhoneProposal != null && (normalized.equals("cancel")
                || normalized.equals("never mind") || normalized.equals("stop"))) {
            requestCancellation(cancelButton);
            return;
        }
        if (activePhoneProposal != null && activePhoneProposal.actionType() == ActionType.REPLY
                && (normalized.startsWith("change it to ") || normalized.startsWith("make it "))) {
            int prefix = normalized.startsWith("change it to ") ? "change it to ".length()
                    : "make it ".length();
            String replacement = spoken.substring(Math.min(prefix, spoken.length())).trim();
            if (!replacement.isEmpty()) {
                responseEditor.setText(replacement);
                responseEditor.setSelection(responseEditor.length());
                voiceController.speakAndListen("I changed the proposed reply. Say send to confirm it, or keep editing.");
                return;
            }
        }
        if (activePhoneProposal == null && currentAttention != null
                && (normalized.equals("send") || normalized.equals("send it")
                || normalized.equals("send that") || normalized.equals("send now"))) {
            selectResponseAction("Send");
            return;
        }
        if (activePhoneProposal == null && currentAttention != null
                && (normalized.equals("later") || normalized.equals("snooze")
                || normalized.equals("snooze this")
                || normalized.startsWith("remind me later"))) {
            selectResponseAction("Later");
            return;
        }
        if (activePhoneProposal == null && currentAttention != null
                && (normalized.equals("done") || normalized.equals("dismiss")
                || normalized.equals("dismiss this") || normalized.equals("next"))) {
            selectResponseAction("Done");
            return;
        }
        if (looksLikeHistoryQuestion(normalized)) {
            voiceController.speakAndListen(answerFromLocalHistory(spoken));
            return;
        }
        if (currentAttention != null) {
            voiceController.speakAndListen(
                    "I did not catch the action. Say reply with your message, snooze this, done, or ask about the conversation.");
        } else {
            voiceController.speakAndListen("You are all caught up. Ask me about a recent person or message.");
        }
    }

    private static boolean looksLikeHistoryQuestion(String value) {
        return value.contains("what did") || value.contains("previous conversation")
                || value.contains("yesterday") || value.contains("last message")
                || value.contains("conversation with");
    }

    private String answerFromLocalHistory(String question) {
        String normalized = question.toLowerCase(Locale.US);
        List<StoredNotificationEvent> recent =
                NotificationRuntime.get(this).notifications().getRecentEvents(100);
        List<StoredNotificationEvent> matches = new ArrayList<>();
        for (StoredNotificationEvent event : recent) {
            String sender = emptyFallback(event.getSenderName(), "").toLowerCase(Locale.US);
            if (!sender.isEmpty() && normalized.contains(sender)) matches.add(event);
        }
        if (matches.isEmpty()) {
            return "I do not have a matching notification snapshot for that person yet.";
        }
        matches.sort(Comparator.comparingLong(StoredNotificationEvent::getUpdatedAt).reversed());
        StoredNotificationEvent match = matches.get(0);
        return "The recent notification history I have from "
                + emptyFallback(match.getSenderName(), "that person") + " says: "
                + emptyFallback(match.getBody(), "No message preview was retained.");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceController.stop();
            voiceController.start();
            mainHandler.postDelayed(voiceController::listen, 150L);
        }
    }

    private void updateShakeLifecycle() {
        if (shakeController == null) return;
        if (shakeSwitch.isChecked() && hasWindowFocus()) shakeController.start();
        else shakeController.stop();
    }

    private void startCoreLink() {
        NotificationRuntime runtime = NotificationRuntime.get(this);
        if (coreStartIssued) return;
        if (!ConnectionConfigStore.isConfigured(this, runtime.getDeviceId())
                || ConnectionConfigStore.isStub(this, runtime.getDeviceId())) {
            TextureFlowConnectionController.useLocalStub(this);
        }
        try {
            TextureFlowConnectionController.start(this);
            coreStartIssued = true;
            if (ConnectionConfigStore.isStub(this, runtime.getDeviceId())) {
                renderConnection(ConnectionState.CONNECTED, "Local stub · no Convex / VoiceOS");
            }
        } catch (RuntimeException unavailable) {
            coreStartIssued = false;
            renderConnection(ConnectionState.STALE, "Core link will retry");
        }
    }

    private void openNotificationAccessSettings(View ignored) {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openCoreSetup(View ignored) {
        boolean stubbed = ConnectionConfigStore.isStub(
                this, NotificationRuntime.get(this).getDeviceId());
        new AlertDialog.Builder(this)
                .setTitle(stubbed ? "Local mode" : "TextureFlow Core")
                .setMessage(stubbed
                        ? "Convex and VoiceOS are stubbed. The phone runs on-device only "
                        + "(notifications, bank, Moth Market UI). You can delete those "
                        + "integrations later; this mode is the default now."
                        : "Live Convex is configured. Prefer local stub unless you are "
                        + "actively restoring cloud sync.")
                .setPositiveButton(stubbed ? "Keep local stub" : "Switch to local stub",
                        (dialog, which) -> {
                            TextureFlowConnectionController.stop(this);
                            coreStartIssued = false;
                            TextureFlowConnectionController.useLocalStub(this);
                            mainHandler.postDelayed(this::startCoreLink, 250L);
                            renderConnection(ConnectionState.CONNECTED,
                                    "Local stub · no Convex / VoiceOS");
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private EditText setupField(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setText(value);
        return field;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || notificationPermissionRequested
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationPermissionRequested = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    private void refreshCoreStatus() {
        if (!hasNotificationAccess()) return;
        long now = System.currentTimeMillis();
        ListenerHealthStore.Snapshot health = NotificationRuntime.get(this).health().read();
        boolean live = TextureNotificationListenerService.hasLiveConnection();
        boolean readerOk = ListenerHealthPolicy.isFresh(health, now) && live;

        ConnectionStatusStore.Snapshot status = ConnectionStatusStore.read(this);
        long age = now - status.lastOnlineAtMillis();
        if ("ONLINE".equals(status.state()) && age >= 0L && age <= 45_000L) {
            renderConnection(ConnectionState.CONNECTED,
                    readerOk ? "Core and reader active" : "Core online · reader reconnecting");
        } else if ("STARTING".equals(status.state()) || "REGISTERING".equals(status.state())) {
            renderConnection(ConnectionState.CONNECTING, status.detail());
        } else if ("BACKING_OFF".equals(status.state()) || "DEGRADED".equals(status.state())
                || age > 45_000L) {
            renderConnection(ConnectionState.STALE, "Core link recovering");
            if (!coreStartIssued) startCoreLink();
        }
        notificationStatus.setText(
                ListenerHealthPolicy.statusLabel(health, now, true, live));
        if (!readerOk) {
            // Reconcile alone cannot heal a live-flag zombie; escalate when policy says locked out.
            if (ListenerHealthPolicy.needsForceRestart(health, now, live)) {
                TextureNotificationListenerService.forceStaleRebind(this);
            } else {
                TextureNotificationListenerService.requestHealthReconciliation(this);
            }
        }
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName expected = new ComponentName(this, TextureNotificationListenerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName candidate = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(candidate)) return true;
        }
        return false;
    }

    public void renderConnection(ConnectionState state, String detail) {
        runOnUiThread(() -> {
            connectionState = state == null ? ConnectionState.DISCONNECTED : state;
            String label = switch (connectionState) {
                case CONNECTED -> "Connected";
                case CONNECTING -> "Connecting";
                case STALE -> "Connection recovering";
                case DISCONNECTED -> "Disconnected";
            };
            connectionStatus.setText(joinStatus(label, detail));
            connectionStatus.setTextColor(connectionState == ConnectionState.CONNECTED ? TEAL
                    : connectionState == ConnectionState.STALE ? CRIMSON : INK);
            eyeView.setState(eyeStateForSurface());
        });
    }

    public void renderListening(String sessionId) {
        runOnUiThread(() -> {
            sessionState = SessionState.LISTENING;
            sessionStatus.setText("Listening");
            eyeView.setState(EyeOfHorusView.State.LISTENING);
            textureEngine.emit(TextureCue.LISTENING_STARTED, safeId(sessionId, "session"), eyeView);
        });
    }

    public void renderThinking(String detail) {
        runOnUiThread(() -> {
            sessionState = SessionState.THINKING;
            sessionStatus.setText(joinStatus("Understanding", detail));
            eyeView.setState(EyeOfHorusView.State.THINKING);
        });
    }

    public void renderIdle(String detail) {
        runOnUiThread(() -> {
            sessionState = SessionState.IDLE;
            sessionStatus.setText(joinStatus("Ready", detail));
            eyeView.setState(eyeStateForSurface());
        });
    }

    public void renderAttention(String eventId, String sender, String app, String body,
                                String priorityReason, boolean urgent) {
        runOnUiThread(() -> {
            sessionState = SessionState.PRESENTING;
            String source = joinStatus(emptyFallback(sender, "Unknown sender"),
                    emptyFallback(app, "Unknown app"));
            attentionMeta.setText(activeQueueSize > 1
                    ? source + " · 1 of " + activeQueueSize : source);
            attentionBody.setText(emptyFallback(body, "Notification content is unavailable."));
            attentionReason.setText(emptyFallback(priorityReason,
                    urgent ? "Urgent" : "Important"));
            attentionPanel.setBackground(urgent
                    ? TextureDrawableFactory.emphasizedPanel(this, dp(30), AMBER)
                    : TextureDrawableFactory.quietPanel(this, dp(30)));
            // Keep legacy attention chrome hidden on the Moth Market chat list.
            attentionPanel.setVisibility(View.GONE);
            responseSmile.setVisibility(View.GONE);
            responseTitle.setVisibility(View.VISIBLE);
            responseEditor.setVisibility(View.VISIBLE);
            responseOptions.setVisibility(View.VISIBLE);
            sessionStatus.setText(urgent ? "Urgent item present" : "Important item present");
            if (urgent) textureEngine.emit(TextureCue.ATTENTION_URGENT,
                    safeId(eventId, "event"), chatsPage);
        });
    }

    public void clearAttention() {
        runOnUiThread(() -> {
            currentAttention = null;
            activePhoneProposal = null;
            activeProposalEvent = null;
            phoneActionBusy = false;
            attentionPanel.setVisibility(View.GONE);
            responseSmile.setVisibility(View.VISIBLE);
            responseTitle.setVisibility(View.GONE);
            responseEditor.setVisibility(View.GONE);
            responseEditor.setText("");
            responseOptions.setVisibility(View.GONE);
            responseOptions.removeAllViews();
            responseStatus.setVisibility(View.GONE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            currentProposalId = null;
        });
    }

    public void renderProposal(String proposalId, String spokenPreview, String expiryDescription) {
        runOnUiThread(() -> {
            currentProposalId = proposalId;
            sessionState = SessionState.AWAITING_CONFIRMATION;
            responseSmile.setVisibility(View.GONE);
            responseTitle.setVisibility(View.VISIBLE);
            responseTitle.setText("Proposed response");
            responseEditor.setVisibility(View.VISIBLE);
            responseEditor.setText(emptyFallback(spokenPreview, "Proposal preview unavailable."));
            responseEditor.setEnabled(false);
            responseOptions.setVisibility(View.GONE);
            responseStatus.setText(joinStatus("Awaiting confirmation", expiryDescription));
            responseStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.VISIBLE);
            confirmButton.setEnabled(true);
            cancelButton.setEnabled(true);
            eyeView.setState(EyeOfHorusView.State.PROPOSAL);
            // Proposal chrome stays on the hidden legacy panel until chat actions land.
            textureEngine.emit(TextureCue.PROPOSAL_READY, safeId(proposalId, "proposal"), responsePanel);
        });
    }

    public void renderExecution(String commandId, String detail) {
        runOnUiThread(() -> {
            sessionState = SessionState.EXECUTING;
            responseStatus.setText(joinStatus("Executing confirmed action", detail));
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            eyeView.setState(EyeOfHorusView.State.EXECUTING);
            textureEngine.emit(TextureCue.EXECUTION_STARTED, safeId(commandId, "command"), responsePanel);
        });
    }

    public void renderReceipt(String receiptId, ReceiptState state, String message) {
        runOnUiThread(() -> {
            ReceiptState safeState = state == null ? ReceiptState.FAILED : state;
            boolean dispatched = safeState == ReceiptState.DISPATCHED;
            sessionState = dispatched ? SessionState.RECEIPT : SessionState.FAILED;
            String status = switch (safeState) {
                case DISPATCHED -> "Dispatched";
                case EXPIRED -> "Command expired";
                case STALE -> "Notification changed";
                case FAILED -> "Action failed";
            };
            receiptStatus.setText(status);
            receiptStatus.setTextColor(dispatched ? TEAL : CRIMSON);
            receiptDetail.setText(emptyFallback(message,
                    dispatched ? "Android dispatched the action." : "Android did not dispatch the action."));
            responseStatus.setText(status);
            responseStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            currentProposalId = null;
            cancelButton.setText("Cancel");
            textureEngine.emit(dispatched ? TextureCue.ACTION_DISPATCHED : TextureCue.ACTION_FAILED,
                    safeId(receiptId, "receipt"), responsePanel);
            if (!dispatched && currentAttention != null) {
                responseEditor.setEnabled(true);
                renderResponseOptions(currentAttention);
                responseOptions.setVisibility(View.VISIBLE);
            }
            mainHandler.postDelayed(this::refreshLocalSurface, 350L);
        });
    }

    public void renderCancelled(String proposalId, String message) {
        runOnUiThread(() -> {
            sessionState = SessionState.CANCELLED;
            responseStatus.setText(joinStatus("Cancelled", message));
            responseStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            responseEditor.setEnabled(true);
            currentProposalId = null;
            textureEngine.emit(TextureCue.CANCELLED, safeId(proposalId, "proposal"), responsePanel);
        });
    }

    public void setSpeechActive(boolean active) {
        textureEngine.setSpeechActive(active);
    }

    private void requestConfirmation(View source) {
        if (activePhoneProposal != null) {
            confirmPhoneProposal();
            return;
        }
        if (currentProposalId == null || actionRequestListener == null) {
            responseStatus.setText("No pending proposal to confirm");
            responseStatus.setVisibility(View.VISIBLE);
            return;
        }
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        responseStatus.setText("Confirmation requested");
        actionRequestListener.onConfirmRequested(currentProposalId);
    }

    private void requestCancellation(View source) {
        if (activePhoneProposal != null) {
            cancelPhoneProposal();
            return;
        }
        if (currentProposalId == null || actionRequestListener == null) {
            responseStatus.setText("No pending proposal to cancel");
            responseStatus.setVisibility(View.VISIBLE);
            return;
        }
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        responseStatus.setText("Cancellation requested");
        actionRequestListener.onCancelRequested(currentProposalId);
    }

    private void confirmPhoneProposal() {
        if (phoneActionBusy || activePhoneProposal == null || activeProposalEvent == null) return;
        CoreActionClient.Proposal proposal = activePhoneProposal;
        StoredNotificationEvent event = activeProposalEvent;
        String latestReply = proposal.actionType() == ActionType.REPLY
                ? responseEditor.getText().toString().trim() : "";
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(this, NotificationRuntime.get(this).getDeviceId());
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Local configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        ConnectionConfig finalConfig = config;
        actionExecutor.execute(() -> {
            try {
                if (finalConfig.isStub()) {
                    if (localActionClient == null) {
                        localActionClient = new LocalCoreActionClient(this, finalConfig);
                    }
                    CoreActionClient.Proposal working = proposal;
                    if (proposal.actionType() == ActionType.REPLY
                            && !latestReply.equals(proposal.replyMessage())) {
                        CoreActionClient.Proposal revised =
                                localActionClient.reviseReply(proposal, event, latestReply);
                        runOnUiThread(() -> {
                            renderPhoneProposal(event, revised);
                            responseStatus.setText("Reply changed. Confirm the revised exact preview: "
                                    + revised.spokenPreview());
                        });
                        return;
                    }
                    final CoreActionClient.Proposal toConfirm = working;
                    runOnUiThread(() -> renderExecution(toConfirm.proposalId(),
                            "Executing on-device (stub)"));
                    CoreActionClient.Confirmation confirmation = localActionClient.confirm(toConfirm);
                    CoreActionClient.Receipt receipt = confirmation.receipt();
                    runOnUiThread(() -> finishPhoneConfirmation(toConfirm, event, receipt));
                    return;
                }

                CoreActionClient client = new CoreActionClient(
                        finalConfig, ConnectionConfigStore.loadUserActionToken(this));
                if (proposal.actionType() == ActionType.REPLY) {
                    if (!latestReply.equals(proposal.replyMessage())) {
                        CoreActionClient.Proposal revised =
                                client.reviseReply(proposal, event, latestReply);
                        runOnUiThread(() -> {
                            renderPhoneProposal(event, revised);
                            responseStatus.setText("Reply changed. Confirm the revised exact preview: "
                                    + revised.spokenPreview());
                        });
                        return;
                    }
                }
                CoreActionClient.Confirmation confirmation = client.confirm(proposal);
                runOnUiThread(() -> renderExecution(confirmation.commandId(),
                        "Waiting for Android receipt"));
                CoreActionClient.Receipt receipt = confirmation.receipt() == null
                        ? client.awaitReceipt(confirmation.commandId(), 65_000L)
                        : confirmation.receipt();
                runOnUiThread(() -> finishPhoneConfirmation(proposal, event, receipt));
            } catch (Exception failure) {
                runOnUiThread(() -> finishPhoneActionFailure(
                        "Action was not confirmed. It remains unsent."));
            }
        });
    }

    private void finishPhoneConfirmation(
            CoreActionClient.Proposal proposal,
            StoredNotificationEvent event,
            CoreActionClient.Receipt receipt) {
        phoneActionBusy = false;
        if (receipt == null) {
            sessionState = SessionState.FAILED;
            responseStatus.setText(
                    "No Android receipt arrived before expiry. Check the source app before retrying.");
            responseStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            activePhoneProposal = null;
            activeProposalEvent = null;
            currentProposalId = null;
            if (currentAttention != null) {
                renderResponseOptions(currentAttention);
                responseOptions.setVisibility(View.VISIBLE);
            }
            return;
        }
        ReceiptState state = receiptState(receipt.status());
        if (state == ReceiptState.DISPATCHED) {
            handledAttentionKeys.add(attentionKey(event));
            hiddenEventUntil.put(event.getEventId(), System.currentTimeMillis() + 30_000L);
            if (proposal.actionType() == ActionType.REPLY) responseEditor.setText("");
            TextureNotificationListenerService.requestHealthReconciliation(this);
        }
        activePhoneProposal = null;
        activeProposalEvent = null;
        renderReceipt(receipt.receiptId(), state, receipt.message());
        voiceController.speakAndListen(state == ReceiptState.DISPATCHED
                ? "Done. I moved to the next item. You can reply, snooze, dismiss, or ask about the conversation."
                : "That action was not dispatched. You can retry, change it, or choose another action.");
    }

    private void cancelPhoneProposal() {
        if (phoneActionBusy || activePhoneProposal == null) return;
        CoreActionClient.Proposal proposal = activePhoneProposal;
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(this, NotificationRuntime.get(this).getDeviceId());
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Local configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        actionExecutor.execute(() -> {
            try {
                if (config.isStub()) {
                    if (localActionClient != null) localActionClient.cancel(proposal);
                } else {
                    new CoreActionClient(config, ConnectionConfigStore.loadUserActionToken(this))
                            .cancel(proposal);
                }
                runOnUiThread(() -> {
                    phoneActionBusy = false;
                    activePhoneProposal = null;
                    activeProposalEvent = null;
                    renderCancelled(proposal.proposalId(), "Nothing was executed.");
                    if (currentAttention != null) {
                        renderResponseOptions(currentAttention);
                        responseOptions.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> finishPhoneActionFailure(
                        "Could not cancel the proposal yet."));
            }
        });
    }

    private void cycleProfile(View source) {
        SensoryProfile next = textureEngine.getProfile().next();
        textureEngine.setProfile(next);
        profileButton.setText("Profile: " + next.displayName());
        eyeView.setReducedMotion(reducedTextureSwitch.isChecked() || next.reducesContinuousTexture());
        updateSensorySummary();
        persistSensoryPreferences();
        textureEngine.playBoundaryBump(source);
    }

    private void updateSensorySummary() {
        SensoryProfile profile = textureEngine.getProfile();
        String audio = textureEngine.isAudioEnabled() && profile != SensoryProfile.VISUAL_ONLY
                ? "audio on" : "audio off";
        String haptics = textureEngine.isHapticsEnabled() && profile != SensoryProfile.VISUAL_ONLY
                ? "haptics on" : "haptics off";
        sensorySummary.setText(profile.displayName() + ": " + audio + ", " + haptics + ".");
    }

    private void restoreSensoryPreferences() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        SensoryProfile profile;
        try {
            profile = SensoryProfile.valueOf(
                    preferences.getString("profile", SensoryProfile.BALANCED.name()));
        } catch (IllegalArgumentException exception) {
            profile = SensoryProfile.BALANCED;
        }
        suppressPreferenceCallbacks = true;
        textureEngine.setProfile(profile);
        textureEngine.setAudioEnabled(preferences.getBoolean("audio", true));
        textureEngine.setHapticsEnabled(preferences.getBoolean("haptics", true));
        audioSwitch.setChecked(textureEngine.isAudioEnabled());
        hapticsSwitch.setChecked(textureEngine.isHapticsEnabled());
        shakeSwitch.setChecked(preferences.getBoolean("shake", true));
        boolean reduced = preferences.getBoolean("reduced_texture", false);
        reducedTextureSwitch.setChecked(reduced);
        backgroundView.setReducedTexture(reduced);
        eyeView.setReducedMotion(reduced || profile.reducesContinuousTexture());
        profileButton.setText("Profile: " + profile.displayName());
        suppressPreferenceCallbacks = false;
        updateSensorySummary();
    }

    private void persistSensoryPreferences() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString("profile", textureEngine.getProfile().name())
                .putBoolean("audio", textureEngine.isAudioEnabled())
                .putBoolean("haptics", textureEngine.isHapticsEnabled())
                .putBoolean("shake", shakeSwitch.isChecked())
                .putBoolean("reduced_texture", reducedTextureSwitch.isChecked())
                .apply();
    }

    private EyeOfHorusView.State eyeStateForSurface() {
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.STALE) {
            return EyeOfHorusView.State.DISCONNECTED;
        }
        return switch (sessionState) {
            case LISTENING -> EyeOfHorusView.State.LISTENING;
            case THINKING -> EyeOfHorusView.State.THINKING;
            case AWAITING_CONFIRMATION -> EyeOfHorusView.State.AWAITING_CONFIRMATION;
            case EXECUTING -> EyeOfHorusView.State.EXECUTING;
            case RECEIPT -> EyeOfHorusView.State.SUCCESS;
            case FAILED -> EyeOfHorusView.State.FAILURE;
            default -> EyeOfHorusView.State.CONNECTED;
        };
    }

    private static EyeOfHorusView.State eyeStateForCue(TextureCue cue) {
        return switch (cue) {
            case LISTENING_STARTED -> EyeOfHorusView.State.LISTENING;
            case PROPOSAL_READY -> EyeOfHorusView.State.PROPOSAL;
            case CONFIRMATION_REQUIRED -> EyeOfHorusView.State.AWAITING_CONFIRMATION;
            case EXECUTION_STARTED -> EyeOfHorusView.State.EXECUTING;
            case ACTION_DISPATCHED -> EyeOfHorusView.State.SUCCESS;
            case ACTION_FAILED -> EyeOfHorusView.State.FAILURE;
            default -> null;
        };
    }

    private FrameLayout scrollPage(LinearLayout content) {
        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(dp(4), dp(4), dp(4), dp(20));
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        textureEngine.attachScrollTexture(scroll, backgroundView::setScrollOffset);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, matchFrame());
        return frame;
    }

    private LinearLayout pageColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, dp(18));
        return layout;
    }

    private LinearLayout surface(float radius) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(17), dp(18), dp(17));
        panel.setBackground(TextureDrawableFactory.quietPanel(this, radius));
        return panel;
    }

    private Button button(String label, int accent) {
        Button button = compactButton(label, accent);
        button.setMinHeight(dp(54));
        button.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private Button compactButton(String label, int accent) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setBackground(TextureDrawableFactory.glassButton(this, dp(24), accent));
        textureEngine.attachGlassControl(button);
        return button;
    }

    private Switch settingSwitch(String label) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(16);
        control.setTextColor(INK);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinHeight(dp(52));
        control.setPadding(dp(2), dp(4), dp(2), dp(4));
        return control;
    }

    private TextView sectionHeading(String value) {
        TextView heading = text(value, 14, MUTED, true);
        heading.setAllCaps(true);
        heading.setAccessibilityHeading(true);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        heading.setLayoutParams(params);
        return heading;
    }

    private TextView value(String value) { return text(value, 18, INK, true); }
    private TextView supportingValue(String value) { return text(value, 14, MUTED, false); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private FrameLayout.LayoutParams navigationParams() {
        // WRAP_CONTENT so icon + "chat"/"flow" captions are not clipped.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(18), 0, dp(18), dp(18));
        return params;
    }

    private LinearLayout.LayoutParams navItemParams() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private FrameLayout.LayoutParams matchFrame() { return new FrameLayout.LayoutParams(-1, -1); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }

    private LinearLayout.LayoutParams wideWithTop(int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, top, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams wideWithBottom(int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, bottom);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, top, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams narrowStart() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String joinStatus(String primary, String detail) {
        if (detail == null || detail.trim().isEmpty()) return primary;
        return primary + " · " + detail.trim();
    }

    private static String safeId(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String normalizePersonKey(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "").trim();
    }

    private static String eventSignature(List<StoredNotificationEvent> events) {
        StringBuilder value = new StringBuilder();
        for (StoredNotificationEvent event : events) {
            value.append(event.getEventId()).append(':').append(event.getVersion())
                    .append(':').append(event.getStatus()).append('|');
        }
        return value.toString();
    }

    private static String attentionKey(StoredNotificationEvent event) {
        return event.getEventId() + ':' + event.getVersion();
    }

    private static String shortTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(millis));
    }

    private static String clockTime(long millis) {
        // Always show a complete wall-clock time, e.g. "4:55 PM".
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    private static final class ChatBubble {
        final boolean outbound;
        final String body;
        final long at;

        ChatBubble(boolean outbound, String body, long at) {
            this.outbound = outbound;
            this.body = body;
            this.at = at;
        }
    }

    private static final class PersonTimeline {
        final String name;
        final Set<String> apps = new LinkedHashSet<>();
        final List<StoredNotificationEvent> events = new ArrayList<>();
        final List<ChatBubble> demoMessages = new ArrayList<>();
        long latestAt;
        long latestUnreadAt;
        String latestBody = "";
        String latestPackage = "";
        String latestAppLabel = "";
        String latestUnreadPackage = "";
        String latestUnreadAppLabel = "";
        int unreadCount;
        boolean demo;

        PersonTimeline(String name) { this.name = name; }
    }
}
