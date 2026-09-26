package com.textureflow.ui;

import android.os.Handler;

import com.textureflow.notifications.NotificationRuntime;
import com.textureflow.ui.chats.ChatListController;
import com.textureflow.ui.chats.ConversationController;
import com.textureflow.ui.flows.FlowsPage;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.lighting.ReflectionLightsController;
import com.textureflow.ui.nav.NavigationController;
import com.textureflow.ui.nav.Page;
import com.textureflow.ui.settings.SettingsPage;
import com.textureflow.ui.intel.AttentionUiBinder;
import com.textureflow.ui.voice.VoiceSessionController;

import java.util.concurrent.ExecutorService;

/** Shared host wiring for extracted UI controllers. No behaviour of its own. */
public final class MainSurface {
    public final MainActivity activity;
    public final Handler mainHandler;
    public final ExecutorService actionExecutor;

    public UiKit kit;
    public HapticTextureEngine textureEngine;
    public TextureBackgroundView backgroundView;
    public ShakeUrgencyController shakeController;
    public ConversationalVoiceController voiceController;

    public NavigationController navigation;
    public ChatListController chats;
    public ConversationController conversation;
    public SettingsPage settings;
    public FlowsPage flows;
    public ReflectionLightsController lights;
    public VoiceSessionController voice;
    public AttentionUiBinder intel;

    public MainActivity.ConnectionState connectionState = MainActivity.ConnectionState.DISCONNECTED;
    public MainActivity.SessionState sessionState = MainActivity.SessionState.IDLE;
    public MainActivity.ActionRequestListener actionRequestListener;

    public MainSurface(MainActivity activity, Handler mainHandler, ExecutorService actionExecutor) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.actionExecutor = actionExecutor;
    }

    public NotificationRuntime runtime() {
        return NotificationRuntime.get(activity);
    }

    public void showPage(Page page) {
        navigation.showPage(page);
    }

    public void updateReflectionLights() {
        lights.update();
    }

    public EyeOfHorusView eyeView() {
        return chats == null ? null : chats.legacy().eyeView;
    }
}
