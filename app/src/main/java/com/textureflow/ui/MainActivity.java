package com.textureflow.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionEngine;
import com.textureflow.texture.TextureCue;
import com.textureflow.texture.TextureCueScheduler;
import com.textureflow.ui.chats.ChatListController;
import com.textureflow.ui.chats.ConversationController;
import com.textureflow.ui.flows.FlowsPage;
import com.textureflow.ui.intel.AttentionUiBinder;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.lighting.ReflectionLightsController;
import com.textureflow.ui.nav.NavigationController;
import com.textureflow.ui.nav.Page;
import com.textureflow.ui.settings.SettingsPage;
import com.textureflow.ui.voice.VoiceSessionController;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Host: lifecycle, permissions, and wiring for extracted Moth Market controllers. */
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

    public static final int AUDIO_PERMISSION_REQUEST = 1002;
    private static final long SURFACE_REFRESH_MS = 2_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final MainSurface surface = new MainSurface(this, mainHandler, actionExecutor);

    private HapticTextureEngine textureEngine;
    private TextureBackgroundView backgroundView;
    private ShakeUrgencyController shakeController;
    private boolean notificationPermissionRequested;

    private final Runnable surfaceRefresh = new Runnable() {
        @Override
        public void run() {
            surface.settings.refreshCoreStatus();
            refreshLocalSurface();
            mainHandler.postDelayed(this, SURFACE_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textureEngine = new HapticTextureEngine(this);
        surface.textureEngine = textureEngine;
        surface.kit = new UiKit(this, textureEngine);
        surface.lights = new ReflectionLightsController(surface);
        surface.navigation = new NavigationController(surface);
        surface.conversation = new ConversationController(surface);
        surface.chats = new ChatListController(surface);
        surface.settings = new SettingsPage(surface);
        surface.flows = new FlowsPage(surface);
        surface.voice = new VoiceSessionController(surface);
        surface.intel = new AttentionUiBinder(surface);
        surface.voice.initialize();
        surface.intel.attachIfPresent(surface.runtime());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MothMarketTheme.BG);
        backgroundView = new TextureBackgroundView(this);
        surface.backgroundView = backgroundView;
        backgroundView.setBaseColor(MothMarketTheme.BG);
        backgroundView.setReducedTexture(false);
        backgroundView.setAlpha(1f);
        root.addView(backgroundView, surface.kit.matchFrame());
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> surface.updateReflectionLights());
        surface.kit.setScrollHook(scrollY -> {
            backgroundView.setScrollOffset(scrollY);
            surface.updateReflectionLights();
        });

        FrameLayout content = new FrameLayout(this);
        FrameLayout.LayoutParams contentParams = surface.kit.matchFrame();
        contentParams.setMargins(0, 0, 0, surface.kit.dp(104));
        root.addView(content, contentParams);

        FrameLayout chatsPage = surface.chats.buildPage();
        FrameLayout chatThreadPage = surface.conversation.buildPage();
        FrameLayout settingsPage = surface.settings.build();
        FrameLayout flowsPage = surface.flows.build();
        surface.navigation.bindPages(chatsPage, chatThreadPage, settingsPage, flowsPage);
        content.addView(chatsPage, surface.kit.matchFrame());
        content.addView(chatThreadPage, surface.kit.matchFrame());
        content.addView(settingsPage, surface.kit.matchFrame());
        content.addView(flowsPage, surface.kit.matchFrame());
        root.addView(surface.navigation.build(), surface.kit.navigationParams());

        textureEngine.setListener(new TextureCueScheduler.Listener() {
            @Override
            public void onCueStarted(TextureCue cue, String correlationId) {
                backgroundView.showCue(cue, surface.settings.reducedTextureSwitch() == null
                        || surface.settings.reducedTextureSwitch().isChecked()
                        || textureEngine.getProfile().reducesContinuousTexture());
                EyeOfHorusView.State state = ConversationController.eyeStateForCue(cue);
                EyeOfHorusView eyeView = surface.eyeView();
                if (state != null && eyeView != null) eyeView.setState(state);
            }

            @Override
            public void onCueFinished(TextureCue cue, String correlationId, boolean cancelled) {
                EyeOfHorusView eyeView = surface.eyeView();
                if (eyeView != null) {
                    eyeView.setState(ConversationController.eyeStateForSurface(
                            surface.connectionState, surface.sessionState));
                }
            }
        });

        setContentView(root);
        surface.settings.restoreSensoryPreferences();
        shakeController = new ShakeUrgencyController(this, surface.voice::speakUrgentItem);
        surface.shakeController = shakeController;
        surface.navigation.showPage(Page.CHATS);
        renderConnection(ConnectionState.DISCONNECTED, "Waiting for the Core link");
        surface.chats.renderPeople(Collections.emptyList());
    }

    public void setAttentionEngine(AttentionEngine engine) {
        if (surface.intel == null) surface.intel = new AttentionUiBinder(surface);
        surface.intel.setEngine(engine);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surface.intel != null) surface.intel.attachIfPresent(surface.runtime());
        textureEngine.setForeground(true);
        surface.voiceController.start();
        surface.settings.refreshNotificationReader();
        updateShakeLifecycle();
        mainHandler.removeCallbacks(surfaceRefresh);
        mainHandler.post(surfaceRefresh);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        Page current = surface.navigation.currentPage();
        if (current == Page.CHAT) {
            surface.conversation.closeConversation();
            return;
        }
        if (current == Page.SETTINGS || current == Page.FLOWS) {
            surface.navigation.showPage(Page.CHATS);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (shakeController != null) shakeController.stop();
        surface.voiceController.stop();
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
        if (surface.intel != null) surface.intel.detach();
        surface.voiceController.release();
        actionExecutor.shutdownNow();
        textureEngine.release();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void setActionRequestListener(ActionRequestListener listener) {
        surface.actionRequestListener = listener;
    }

    public void refreshLocalSurface() {
        List<StoredNotificationEvent> live = surface.runtime().notifications().getLiveEvents();
        surface.conversation.refreshAttention(live);
        surface.chats.renderPeopleIfChanged(surface.runtime().notifications().getRecentEvents(100));
    }

    public void updateShakeLifecycle() {
        if (shakeController == null) return;
        if (surface.settings.shakeSwitch().isChecked() && hasWindowFocus()) shakeController.start();
        else shakeController.stop();
    }

    public void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || notificationPermissionRequested
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationPermissionRequested = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            surface.voiceController.stop();
            surface.voiceController.start();
            mainHandler.postDelayed(surface.voiceController::listen, 150L);
        }
    }

    public void renderConnection(ConnectionState state, String detail) {
        surface.settings.renderConnection(state, detail);
    }

    public void renderListening(String sessionId) {
        surface.conversation.renderListening(sessionId);
    }

    public void renderThinking(String detail) {
        surface.conversation.renderThinking(detail);
    }

    public void renderIdle(String detail) {
        surface.conversation.renderIdle(detail);
    }

    public void renderAttention(String eventId, String sender, String app, String body,
                                String priorityReason, boolean urgent) {
        surface.conversation.renderAttention(eventId, sender, app, body, priorityReason, urgent);
    }

    public void clearAttention() {
        surface.conversation.clearAttention();
    }

    public void renderProposal(String proposalId, String spokenPreview, String expiryDescription) {
        surface.conversation.renderProposal(proposalId, spokenPreview, expiryDescription);
    }

    public void renderExecution(String commandId, String detail) {
        surface.conversation.renderExecution(commandId, detail);
    }

    public void renderReceipt(String receiptId, ReceiptState state, String message) {
        surface.conversation.renderReceipt(receiptId, state, message);
    }

    public void renderCancelled(String proposalId, String message) {
        surface.conversation.renderCancelled(proposalId, message);
    }

    public void setSpeechActive(boolean active) {
        textureEngine.setSpeechActive(active);
    }
}
