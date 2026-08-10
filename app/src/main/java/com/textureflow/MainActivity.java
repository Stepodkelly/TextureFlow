package com.textureflow;

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
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.textureflow.connection.ConnectionConfigStore;
import com.textureflow.connection.ConnectionConfig;
import com.textureflow.connection.ConnectionStatusStore;
import com.textureflow.connection.CoreActionClient;
import com.textureflow.connection.TextureFlowConnectionController;
import com.textureflow.actions.ActionType;
import com.textureflow.data.ListenerHealthStore;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.notifications.NotificationHealthJobService;
import com.textureflow.notifications.NotificationRuntime;
import com.textureflow.notifications.TextureNotificationListenerService;
import com.textureflow.texture.SensoryProfile;
import com.textureflow.texture.TextureCue;
import com.textureflow.texture.TextureCueScheduler;

import java.text.DateFormat;
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

    private enum Page { HOME, PEOPLE, SETTINGS }

    private static final int INK = Color.rgb(30, 36, 33);
    private static final int MUTED = Color.rgb(78, 88, 82);
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

    private FrameLayout homePage;
    private FrameLayout peoplePage;
    private FrameLayout settingsPage;
    private Button homeTab;
    private Button peopleTab;
    private Button settingsTab;

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

    private LinearLayout peopleList;
    private LinearLayout conversationView;
    private TextView peopleEmpty;
    private TextView conversationTitle;
    private LinearLayout conversationMessages;

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

    private Page currentPage = Page.HOME;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private SessionState sessionState = SessionState.IDLE;
    private StoredNotificationEvent currentAttention;
    private final Set<String> handledAttentionKeys = new LinkedHashSet<>();
    private int activeQueueSize;
    private String currentProposalId;
    private CoreActionClient.Proposal activePhoneProposal;
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
        backgroundView = new TextureBackgroundView(this);
        root.addView(backgroundView, matchFrame());

        FrameLayout content = new FrameLayout(this);
        FrameLayout.LayoutParams contentParams = matchFrame();
        contentParams.setMargins(0, 0, 0, dp(104));
        root.addView(content, contentParams);

        homePage = buildHomePage();
        peoplePage = buildPeoplePage();
        settingsPage = buildSettingsPage();
        content.addView(homePage, matchFrame());
        content.addView(peoplePage, matchFrame());
        content.addView(settingsPage, matchFrame());
        root.addView(buildNavigation(), navigationParams());

        textureEngine.setListener(new TextureCueScheduler.Listener() {
            @Override
            public void onCueStarted(TextureCue cue, String correlationId) {
                backgroundView.showCue(cue, reducedTextureSwitch.isChecked()
                        || textureEngine.getProfile().reducesContinuousTexture());
                EyeOfHorusView.State state = eyeStateForCue(cue);
                if (state != null) eyeView.setState(state);
            }

            @Override
            public void onCueFinished(TextureCue cue, String correlationId, boolean cancelled) {
                eyeView.setState(eyeStateForSurface());
            }
        });

        setContentView(root);
        restoreSensoryPreferences();
        shakeController = new ShakeUrgencyController(this, this::speakUrgentItem);
        showPage(Page.HOME);
        renderConnection(ConnectionState.DISCONNECTED, "Waiting for the Core link");
    }

    private FrameLayout buildHomePage() {
        LinearLayout content = pageColumn();
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        eyeView = new EyeOfHorusView(this);
        LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        eyeParams.setMargins(0, dp(4), 0, dp(4));
        content.addView(eyeView, eyeParams);

        TextView title = text("TextureFlow", 24, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setAccessibilityHeading(true);
        content.addView(title, matchWrap());

        attentionPanel = surface(dp(30));
        attentionPanel.setVisibility(View.GONE);
        attentionMeta = text("", 14, MUTED, true);
        attentionPanel.addView(attentionMeta);
        LinearLayout notificationContents = new LinearLayout(this);
        notificationContents.setOrientation(LinearLayout.VERTICAL);
        attentionBody = text("", 20, INK, false);
        attentionBody.setLineSpacing(dp(3), 1f);
        notificationContents.addView(attentionBody, topMargin(dp(9)));
        attentionReason = text("", 13, MUTED, false);
        notificationContents.addView(attentionReason, topMargin(dp(10)));
        ScrollView notificationWindow = new ScrollView(this);
        notificationWindow.setFillViewport(true);
        notificationWindow.setVerticalScrollBarEnabled(true);
        notificationWindow.setScrollbarFadingEnabled(false);
        notificationWindow.addView(notificationContents, new ScrollView.LayoutParams(-1, -2));
        textureEngine.attachScrollTexture(notificationWindow, backgroundView::setScrollOffset);
        LinearLayout.LayoutParams notificationWindowParams = new LinearLayout.LayoutParams(-1, dp(154));
        attentionPanel.addView(notificationWindow, notificationWindowParams);
        LinearLayout.LayoutParams attentionParams = new LinearLayout.LayoutParams(-1, dp(226));
        attentionParams.setMargins(0, dp(22), 0, 0);
        content.addView(attentionPanel, attentionParams);

        responsePanel = surface(dp(32));
        responsePanel.setGravity(Gravity.CENTER);
        responseSmile = text("🙂", 42, INK, false);
        responseSmile.setGravity(Gravity.CENTER);
        responseSmile.setContentDescription("All caught up");
        responsePanel.addView(responseSmile, matchWrap());

        responseTitle = text("Response", 14, MUTED, true);
        responseTitle.setVisibility(View.GONE);
        responsePanel.addView(responseTitle, topMargin(dp(2)));

        responseEditor = new EditText(this);
        responseEditor.setTextColor(INK);
        responseEditor.setHintTextColor(MUTED);
        responseEditor.setTextSize(18);
        responseEditor.setHint("Write a response");
        responseEditor.setMinHeight(dp(78));
        responseEditor.setMaxHeight(dp(108));
        responseEditor.setVerticalScrollBarEnabled(true);
        responseEditor.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        responseEditor.setGravity(Gravity.TOP | Gravity.START);
        responseEditor.setPadding(dp(16), dp(14), dp(16), dp(14));
        responseEditor.setBackground(TextureDrawableFactory.texturedField(this, dp(22)));
        responseEditor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        responseEditor.setVisibility(View.GONE);
        textureEngine.attachGlassControl(responseEditor);
        textureEngine.attachScrollTexture(responseEditor);
        responsePanel.addView(responseEditor, topMargin(dp(8)));

        responseOptions = new LinearLayout(this);
        responseOptions.setOrientation(LinearLayout.HORIZONTAL);
        responseOptions.setGravity(Gravity.CENTER);
        responseOptions.setVisibility(View.GONE);
        responsePanel.addView(responseOptions, topMargin(dp(12)));

        responseStatus = text("", 13, MUTED, false);
        responseStatus.setGravity(Gravity.CENTER);
        responseStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        responseStatus.setVisibility(View.GONE);
        responsePanel.addView(responseStatus, topMargin(dp(8)));

        talkButton = compactButton("Talk", TEAL);
        talkButton.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_btn_speak_now, 0, 0, 0);
        talkButton.setCompoundDrawablePadding(dp(6));
        talkButton.setContentDescription("Interrupt speech or start a voice turn");
        talkButton.setOnClickListener(this::startVoiceTurn);
        responsePanel.addView(talkButton, topMargin(dp(10)));

        confirmButton = compactButton("Confirm", AMBER);
        confirmButton.setFilterTouchesWhenObscured(true);
        confirmButton.setOnClickListener(this::requestConfirmation);
        confirmButton.setVisibility(View.GONE);
        responsePanel.addView(confirmButton, topMargin(dp(10)));

        cancelButton = compactButton("Cancel", CRIMSON);
        cancelButton.setFilterTouchesWhenObscured(true);
        cancelButton.setOnClickListener(this::requestCancellation);
        cancelButton.setVisibility(View.GONE);
        responsePanel.addView(cancelButton);

        content.addView(responsePanel, wideWithTop(dp(14)));
        return scrollPage(content);
    }

    private FrameLayout buildPeoplePage() {
        LinearLayout content = pageColumn();
        TextView title = text("People", 28, INK, true);
        title.setAccessibilityHeading(true);
        content.addView(title, topMargin(dp(8)));

        peopleList = new LinearLayout(this);
        peopleList.setOrientation(LinearLayout.VERTICAL);
        content.addView(peopleList, wideWithTop(dp(16)));

        peopleEmpty = text("No conversations yet.", 18, MUTED, false);
        peopleEmpty.setGravity(Gravity.CENTER);
        peopleEmpty.setPadding(dp(20), dp(50), dp(20), dp(50));
        peopleEmpty.setBackground(TextureDrawableFactory.quietPanel(this, dp(28)));
        peopleList.addView(peopleEmpty, matchWrap());

        conversationView = new LinearLayout(this);
        conversationView.setOrientation(LinearLayout.VERTICAL);
        conversationView.setVisibility(View.GONE);
        Button back = compactButton("Back", TEAL);
        back.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_media_previous, 0, 0, 0);
        back.setOnClickListener(view -> closeConversation());
        conversationView.addView(back, narrowStart());
        conversationTitle = text("", 26, INK, true);
        conversationTitle.setAccessibilityHeading(true);
        conversationView.addView(conversationTitle, topMargin(dp(14)));
        conversationMessages = new LinearLayout(this);
        conversationMessages.setOrientation(LinearLayout.VERTICAL);
        conversationView.addView(conversationMessages, wideWithTop(dp(14)));
        content.addView(conversationView, matchWrap());
        return scrollPage(content);
    }

    private FrameLayout buildSettingsPage() {
        LinearLayout content = pageColumn();
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
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(7), dp(7), dp(7), dp(7));
        nav.setBackground(TextureDrawableFactory.glassButton(this, dp(34), TEAL));
        nav.setElevation(dp(12));

        homeTab = navButton("Home", android.R.drawable.ic_menu_view, Page.HOME);
        peopleTab = navButton("People", android.R.drawable.ic_menu_myplaces, Page.PEOPLE);
        settingsTab = navButton("Settings", android.R.drawable.ic_menu_preferences, Page.SETTINGS);
        nav.addView(homeTab, navItemParams());
        nav.addView(peopleTab, navItemParams());
        nav.addView(settingsTab, navItemParams());
        return nav;
    }

    private Button navButton(String label, int icon, Page page) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        button.setCompoundDrawablePadding(dp(2));
        button.setPadding(dp(3), dp(3), dp(3), dp(3));
        button.setMinHeight(dp(56));
        button.setBackground(new ColorDrawable(TRANSPARENT));
        button.setOnClickListener(view -> showPage(page));
        textureEngine.attachGlassControl(button);
        return button;
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
        homePage.setVisibility(page == Page.HOME ? View.VISIBLE : View.GONE);
        peoplePage.setVisibility(page == Page.PEOPLE ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == Page.SETTINGS ? View.VISIBLE : View.GONE);
        selectTab(homeTab, page == Page.HOME);
        selectTab(peopleTab, page == Page.PEOPLE);
        selectTab(settingsTab, page == Page.SETTINGS);
        textureEngine.playBoundaryBump(page == Page.HOME ? homeTab
                : page == Page.PEOPLE ? peopleTab : settingsTab);
    }

    private void selectTab(Button button, boolean selected) {
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(selected ? TEAL : INK);
        button.setBackground(selected
                ? TextureDrawableFactory.glassButton(this, dp(26), TEAL)
                : new ColorDrawable(TRANSPARENT));
        button.setSelected(selected);
    }

    private void refreshNotificationReader() {
        boolean granted = hasNotificationAccess();
        notificationAccessButton.setText(granted ? "Review notification access" : "Enable notification access");
        if (!granted) {
            notificationStatus.setText("Notification access is off");
            renderConnection(ConnectionState.DISCONNECTED, "Notification access required");
            clearAttention();
            return;
        }

        NotificationHealthJobService.schedule(this);
        TextureNotificationListenerService.requestRebindNow(this);
        startCoreLink();
        requestNotificationPermissionIfNeeded();
        NotificationRuntime runtime = NotificationRuntime.get(this);
        coreLinkButton.setText(ConnectionConfigStore.isConfigured(this, runtime.getDeviceId())
                ? "Reconnect Core" : "Configure Core");
        ListenerHealthStore.Snapshot health = runtime.health().read();
        notificationStatus.setText(health.connected
                ? "Notification reader active"
                : "Notification reader reconnecting");
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
        for (StoredNotificationEvent event : events) {
            if (!"IMPORTANT".equals(event.getPriorityLevel())
                    && !"URGENT".equals(event.getPriorityLevel())) continue;
            if (!handledAttentionKeys.contains(attentionKey(event))) queue.add(event);
        }
        queue.sort((left, right) -> {
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
            String actionToken = ConnectionConfigStore.loadUserActionToken(this);
            if (actionToken == null || actionToken.trim().isEmpty()) {
                responseStatus.setText("Add the User action token in Settings to use phone buttons");
                showPage(Page.SETTINGS);
                return;
            }
        } catch (RuntimeException missingConfig) {
            responseStatus.setText("Connect TextureFlow Core in Settings first");
            showPage(Page.SETTINGS);
            return;
        }

        phoneActionBusy = true;
        setResponseActionsEnabled(false);
        responseStatus.setText("Preparing exact preview");
        actionExecutor.execute(() -> {
            try {
                CoreActionClient client = new CoreActionClient(
                        config, ConnectionConfigStore.loadUserActionToken(this));
                CoreActionClient.Proposal proposal = client.create(event, type, payload);
                runOnUiThread(() -> renderPhoneProposal(event, proposal));
            } catch (Exception failure) {
                runOnUiThread(() -> finishPhoneActionFailure(
                        "Core could not prepare that action. Check the connection and User token."));
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
        peopleList.removeAllViews();
        Map<String, PersonTimeline> people = new LinkedHashMap<>();
        for (StoredNotificationEvent event : events) {
            String name = emptyFallback(event.getSenderName(), event.getConversationLabel());
            if (name == null || name.trim().isEmpty()) continue;
            String key = normalizePersonKey(name);
            PersonTimeline person = people.get(key);
            if (person == null) {
                person = new PersonTimeline(name.trim());
                people.put(key, person);
            }
            person.events.add(event);
            person.apps.add(emptyFallback(event.getAppLabel(), event.getPackageName()));
            person.latestAt = Math.max(person.latestAt, event.getUpdatedAt());
        }
        List<PersonTimeline> ordered = new ArrayList<>(people.values());
        ordered.sort((left, right) -> Long.compare(right.latestAt, left.latestAt));
        if (ordered.isEmpty()) {
            peopleList.addView(peopleEmpty, matchWrap());
            return;
        }
        for (PersonTimeline person : ordered) {
            peopleList.addView(personRow(person), wideWithBottom(dp(9)));
        }
    }

    private View personRow(PersonTimeline person) {
        LinearLayout row = surface(dp(26));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(person.name + ", " + String.join(", ", person.apps));
        row.setOnClickListener(view -> openConversation(person));
        textureEngine.attachGlassControl(row);

        TextView initial = text(person.name.substring(0, 1).toUpperCase(Locale.getDefault()), 20, TEAL, true);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(TextureDrawableFactory.glassButton(this, dp(24), TEAL));
        row.addView(initial, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(person.name, 18, INK, true);
        labels.addView(name);
        TextView apps = text(String.join(" · ", person.apps), 13, MUTED, false);
        apps.setSingleLine(true);
        apps.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(apps, topMargin(dp(3)));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(dp(14), 0, dp(6), 0);
        row.addView(labels, labelParams);
        TextView chevron = text("›", 30, MUTED, false);
        chevron.setContentDescription("Open conversation");
        row.addView(chevron);
        return row;
    }

    private void openConversation(PersonTimeline person) {
        peopleList.setVisibility(View.GONE);
        conversationView.setVisibility(View.VISIBLE);
        conversationTitle.setText(person.name);
        conversationMessages.removeAllViews();
        List<StoredNotificationEvent> events = new ArrayList<>(person.events);
        events.sort(Comparator.comparingLong(StoredNotificationEvent::getUpdatedAt));
        for (StoredNotificationEvent event : events) {
            LinearLayout bubble = surface(dp(24));
            TextView meta = text(emptyFallback(event.getAppLabel(), event.getPackageName())
                    + " · " + shortTime(event.getUpdatedAt()), 12, MUTED, true);
            bubble.addView(meta);
            TextView body = text(emptyFallback(event.getBody(), "Notification content unavailable"),
                    17, INK, false);
            body.setLineSpacing(dp(2), 1f);
            bubble.addView(body, topMargin(dp(7)));
            conversationMessages.addView(bubble, wideWithBottom(dp(9)));
        }
        textureEngine.playBoundaryBump(conversationView);
    }

    private void closeConversation() {
        conversationView.setVisibility(View.GONE);
        peopleList.setVisibility(View.VISIBLE);
        textureEngine.playBoundaryBump(peopleList);
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
            showPage(Page.HOME);
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
        String normalized = spoken.toLowerCase(Locale.US);

        if (activePhoneProposal != null && (normalized.equals("confirm")
                || normalized.equals("send") || normalized.equals("send it"))) {
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
                && (normalized.equals("send") || normalized.equals("send it"))) {
            selectResponseAction("Send");
            return;
        }
        if (activePhoneProposal == null && currentAttention != null
                && (normalized.equals("later") || normalized.startsWith("remind me later"))) {
            selectResponseAction("Later");
            return;
        }
        if (activePhoneProposal == null && currentAttention != null
                && (normalized.equals("done") || normalized.equals("dismiss"))) {
            selectResponseAction("Done");
            return;
        }
        if (looksLikeHistoryQuestion(normalized)) {
            voiceController.speakAndListen(answerFromLocalHistory(spoken));
            return;
        }
        if (currentAttention != null && (normalized.startsWith("reply ")
                || normalized.startsWith("say ") || normalized.startsWith("tell them "))) {
            String draft = spoken.substring(spoken.indexOf(' ') + 1).trim();
            if (!draft.isEmpty()) {
                responseEditor.setVisibility(View.VISIBLE);
                responseEditor.setText(draft);
                responseEditor.setSelection(responseEditor.length());
                responseTitle.setText("Proposed reply");
                responseStatus.setText("Review the draft, then tap Send or say send");
                responseStatus.setVisibility(View.VISIBLE);
                voiceController.speakAndListen("I drafted that reply. You can change it, or say send.");
                return;
            }
        }
        if (currentAttention != null) {
            voiceController.speakAndListen("The current message from "
                    + emptyFallback(currentAttention.getSenderName(), "this person") + " says: "
                    + emptyFallback(currentAttention.getBody(), "No preview is available.")
                    + " You can ask for a reply, later, or done.");
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
        if (coreStartIssued || !ConnectionConfigStore.isConfigured(this, runtime.getDeviceId())) return;
        try {
            TextureFlowConnectionController.start(this);
            coreStartIssued = true;
        } catch (RuntimeException unavailable) {
            coreStartIssued = false;
            renderConnection(ConnectionState.STALE, "Core link will retry");
        }
    }

    private void openNotificationAccessSettings(View ignored) {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openCoreSetup(View ignored) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(6), dp(22), 0);

        EditText url = setupField("Convex deployment URL", BuildConfig.CONVEX_URL,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(url, matchWrap());
        EditText owner = setupField("Owner ID", BuildConfig.TEXTUREFLOW_OWNER_ID,
                InputType.TYPE_CLASS_TEXT);
        form.addView(owner, matchWrap());
        EditText token = setupField("Device enrollment token", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(token, matchWrap());
        EditText userToken = setupField("User action token", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(userToken, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("Connect TextureFlow Core")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (dialog, which) -> {
                    try {
                        String deviceToken = token.getText().toString();
                        String actionToken = userToken.getText().toString();
                        String oidcToken = null;
                        try {
                            ConnectionConfig existing = ConnectionConfigStore.load(
                                    this, NotificationRuntime.get(this).getDeviceId());
                            if (deviceToken.trim().isEmpty()) deviceToken = existing.deviceActorToken();
                            oidcToken = existing.oidcToken();
                            if (actionToken.trim().isEmpty()) {
                                actionToken = ConnectionConfigStore.loadUserActionToken(this);
                            }
                        } catch (RuntimeException noExistingConfiguration) {
                            // Both credentials are required on first setup.
                        }
                        TextureFlowConnectionController.configure(this,
                                url.getText().toString().trim(),
                                owner.getText().toString().trim(),
                                deviceToken, oidcToken, "TextureFlow Android");
                        ConnectionConfigStore.saveUserActionToken(this, actionToken);
                        TextureFlowConnectionController.stop(this);
                        coreStartIssued = false;
                        mainHandler.postDelayed(this::startCoreLink, 250L);
                        renderConnection(ConnectionState.CONNECTING, "Core link starting");
                    } catch (RuntimeException invalid) {
                        renderConnection(ConnectionState.STALE, "Core configuration is incomplete");
                    }
                })
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
        ConnectionStatusStore.Snapshot status = ConnectionStatusStore.read(this);
        long age = System.currentTimeMillis() - status.lastOnlineAtMillis();
        if ("ONLINE".equals(status.state()) && age >= 0L && age <= 45_000L) {
            renderConnection(ConnectionState.CONNECTED, "Core and reader active");
        } else if ("STARTING".equals(status.state()) || "REGISTERING".equals(status.state())) {
            renderConnection(ConnectionState.CONNECTING, status.detail());
        } else if ("BACKING_OFF".equals(status.state()) || "DEGRADED".equals(status.state())
                || age > 45_000L) {
            renderConnection(ConnectionState.STALE, "Core link recovering");
            if (!coreStartIssued) startCoreLink();
        }
        ListenerHealthStore.Snapshot health = NotificationRuntime.get(this).health().read();
        if (!health.connected) {
            notificationStatus.setText("Notification reader reconnecting");
            TextureNotificationListenerService.requestHealthReconciliation(this);
        } else {
            notificationStatus.setText("Notification reader active");
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
            attentionPanel.setVisibility(View.VISIBLE);
            responseSmile.setVisibility(View.GONE);
            responseTitle.setVisibility(View.VISIBLE);
            responseEditor.setVisibility(View.VISIBLE);
            responseOptions.setVisibility(View.VISIBLE);
            sessionStatus.setText(urgent ? "Urgent item present" : "Important item present");
            if (urgent) textureEngine.emit(TextureCue.ATTENTION_URGENT,
                    safeId(eventId, "event"), attentionPanel);
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
            showPage(Page.HOME);
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
            textureEngine.emit(dispatched ? TextureCue.ACTION_DISPATCHED : TextureCue.ACTION_FAILED,
                    safeId(receiptId, "receipt"), responsePanel);
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
            responseStatus.setText("VoiceOS confirmation link unavailable");
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
            responseStatus.setText("VoiceOS cancellation link unavailable");
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
            finishPhoneActionFailure("Core configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        actionExecutor.execute(() -> {
            try {
                CoreActionClient client = new CoreActionClient(
                        config, ConnectionConfigStore.loadUserActionToken(this));
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
                        ? client.awaitReceipt(confirmation.commandId(), 20_000L)
                        : confirmation.receipt();
                runOnUiThread(() -> finishPhoneConfirmation(proposal, event, receipt));
            } catch (Exception failure) {
                runOnUiThread(() -> finishPhoneActionFailure(
                        "Core did not confirm that action. It remains unsent."));
            }
        });
    }

    private void finishPhoneConfirmation(
            CoreActionClient.Proposal proposal,
            StoredNotificationEvent event,
            CoreActionClient.Receipt receipt) {
        phoneActionBusy = false;
        if (receipt == null) {
            responseStatus.setText("Confirmed. Android is still processing the command.");
            responseStatus.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            activePhoneProposal = null;
            activeProposalEvent = null;
            currentProposalId = null;
            return;
        }
        ReceiptState state = receiptState(receipt.status());
        if (state == ReceiptState.DISPATCHED && proposal.actionType() == ActionType.REPLY) {
            handledAttentionKeys.add(attentionKey(event));
            responseEditor.setText("");
        }
        activePhoneProposal = null;
        activeProposalEvent = null;
        renderReceipt(receipt.receiptId(), state, receipt.message());
        voiceController.speak(state == ReceiptState.DISPATCHED
                ? "Done. Android dispatched the action." : "That action was not dispatched.");
    }

    private void cancelPhoneProposal() {
        if (phoneActionBusy || activePhoneProposal == null) return;
        CoreActionClient.Proposal proposal = activePhoneProposal;
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(this, NotificationRuntime.get(this).getDeviceId());
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Core configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        confirmButton.setEnabled(false);
        cancelButton.setEnabled(false);
        actionExecutor.execute(() -> {
            try {
                new CoreActionClient(config, ConnectionConfigStore.loadUserActionToken(this))
                        .cancel(proposal);
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
                        "Core could not cancel the proposal yet."));
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(16), dp(20), dp(28));
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
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM);
        params.setMargins(dp(18), 0, dp(18), dp(18));
        return params;
    }

    private LinearLayout.LayoutParams navItemParams() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
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

    private static final class PersonTimeline {
        final String name;
        final Set<String> apps = new LinkedHashSet<>();
        final List<StoredNotificationEvent> events = new ArrayList<>();
        long latestAt;

        PersonTimeline(String name) { this.name = name; }
    }
}
