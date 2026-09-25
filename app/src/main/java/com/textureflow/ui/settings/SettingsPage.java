package com.textureflow.ui.settings;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.textureflow.connection.ConnectionConfigStore;
import com.textureflow.connection.ConnectionStatusStore;
import com.textureflow.connection.TextureFlowConnectionController;
import com.textureflow.data.ListenerHealthStore;
import com.textureflow.notifications.ListenerHealthPolicy;
import com.textureflow.notifications.NotificationHealthJobService;
import com.textureflow.notifications.NotificationRuntime;
import com.textureflow.notifications.NotificationWatchdogScheduler;
import com.textureflow.notifications.TextureNotificationListenerService;
import com.textureflow.texture.SensoryProfile;
import com.textureflow.ui.EyeOfHorusView;
import com.textureflow.ui.MainActivity;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.TextureBackgroundView;
import com.textureflow.ui.chats.ChatListPresenter;
import com.textureflow.ui.chats.ConversationController;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.nav.Page;

import java.util.Locale;

public final class SettingsPage {
    private static final String PREFERENCES = "texture_surface_preferences";

    private final MainSurface surface;
    private FrameLayout settingsPage;
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
    private SeekBar albedoSeekBar;
    private TextView albedoValueLabel;
    private boolean suppressPreferenceCallbacks;
    private boolean coreStartIssued;

    public SettingsPage(MainSurface surface) {
        this.surface = surface;
    }

    public FrameLayout page() {
        return settingsPage;
    }

    public TextView connectionStatus() { return connectionStatus; }
    public TextView sessionStatus() { return sessionStatus; }
    public TextView notificationStatus() { return notificationStatus; }
    public Button notificationAccessButton() { return notificationAccessButton; }
    public Button coreLinkButton() { return coreLinkButton; }
    public TextView receiptStatus() { return receiptStatus; }
    public TextView receiptDetail() { return receiptDetail; }
    public Switch shakeSwitch() { return shakeSwitch; }
    public Switch reducedTextureSwitch() { return reducedTextureSwitch; }

    public FrameLayout build() {
        UiKit kit = surface.kit;
        LinearLayout content = kit.pageColumn();
        Button back = kit.compactButton("Back to chats", UiKit.TEAL);
        back.setOnClickListener(view -> surface.showPage(Page.CHATS));
        content.addView(back, kit.narrowStart());
        TextView title = kit.text("Settings", 28, UiKit.INK, true);
        title.setAccessibilityHeading(true);
        content.addView(title, kit.topMargin(kit.dp(8)));

        LinearLayout connectionPanel = kit.surface(kit.dp(28));
        connectionPanel.addView(kit.sectionHeading("Connection"));
        connectionStatus = kit.value("Disconnected");
        connectionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(connectionStatus);
        sessionStatus = kit.supportingValue("Ready");
        sessionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(sessionStatus, kit.topMargin(kit.dp(5)));
        notificationStatus = kit.supportingValue("Notification access has not been checked.");
        notificationStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connectionPanel.addView(notificationStatus, kit.topMargin(kit.dp(8)));
        notificationAccessButton = kit.button("Notification access", UiKit.TEAL);
        notificationAccessButton.setOnClickListener(this::openNotificationAccessSettings);
        connectionPanel.addView(notificationAccessButton, kit.topMargin(kit.dp(12)));
        coreLinkButton = kit.button("Core connection", UiKit.TEAL);
        coreLinkButton.setOnClickListener(this::openCoreSetup);
        connectionPanel.addView(coreLinkButton);
        content.addView(connectionPanel, kit.wideWithTop(kit.dp(18)));

        LinearLayout sensoryPanel = kit.surface(kit.dp(28));
        sensoryPanel.addView(kit.sectionHeading("Sensory profile"));
        profileButton = kit.button("Profile: Balanced", UiKit.TEAL);
        profileButton.setOnClickListener(this::cycleProfile);
        sensoryPanel.addView(profileButton, kit.topMargin(kit.dp(5)));

        audioSwitch = kit.settingSwitch("Texture audio");
        audioSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                surface.textureEngine.setAudioEnabled(checked);
                updateSensorySummary();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(audioSwitch);

        hapticsSwitch = kit.settingSwitch("Haptic texture");
        hapticsSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                surface.textureEngine.setHapticsEnabled(checked);
                updateSensorySummary();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(hapticsSwitch);

        shakeSwitch = kit.settingSwitch("Shake for urgent items");
        shakeSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                surface.activity.updateShakeLifecycle();
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(shakeSwitch);

        reducedTextureSwitch = kit.settingSwitch("Reduced visual texture");
        reducedTextureSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!suppressPreferenceCallbacks) {
                surface.backgroundView.setReducedTexture(checked);
                surface.eyeView().setReducedMotion(checked
                        || surface.textureEngine.getProfile().reducesContinuousTexture());
                persistSensoryPreferences();
            }
        });
        sensoryPanel.addView(reducedTextureSwitch);

        sensoryPanel.addView(kit.sectionHeading("Moth albedo"), kit.topMargin(kit.dp(14)));
        albedoValueLabel = kit.supportingValue("Reflection strength 1.00");
        sensoryPanel.addView(albedoValueLabel, kit.topMargin(kit.dp(4)));
        albedoSeekBar = new SeekBar(surface.activity);
        albedoSeekBar.setMax(750); // 0.00 .. 7.50
        albedoSeekBar.setProgress(300); // default 3.00
        albedoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float strength = progress / 100f;
                surface.backgroundView.setAlbedoStrength(strength);
                albedoValueLabel.setText(String.format(
                        Locale.US, "Reflection strength %.2f%s",
                        strength,
                        surface.backgroundView.usesRuntimeShader() ? "" : " (fallback)"));
                if (fromUser && !suppressPreferenceCallbacks) {
                    persistSensoryPreferences();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                surface.updateReflectionLights();
            }
        });
        sensoryPanel.addView(albedoSeekBar, kit.topMargin(kit.dp(6)));

        sensorySummary = kit.supportingValue("");
        sensorySummary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        sensoryPanel.addView(sensorySummary, kit.topMargin(kit.dp(8)));
        Button testTextures = kit.button("Test textures", UiKit.TEAL);
        testTextures.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_media_play, 0, 0, 0);
        testTextures.setOnClickListener(view -> {
            surface.textureEngine.playCarpetScroll(view);
            surface.mainHandler.postDelayed(() -> surface.textureEngine.playGlassTouch(view), 650L);
        });
        sensoryPanel.addView(testTextures, kit.topMargin(kit.dp(10)));
        content.addView(sensoryPanel, kit.wideWithTop(kit.dp(12)));

        LinearLayout receiptPanel = kit.surface(kit.dp(28));
        receiptPanel.addView(kit.sectionHeading("Latest action"));
        receiptStatus = kit.value("No execution receipt");
        receiptStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        receiptPanel.addView(receiptStatus);
        receiptDetail = kit.supportingValue("Only Android-confirmed results appear here.");
        receiptPanel.addView(receiptDetail, kit.topMargin(kit.dp(6)));
        content.addView(receiptPanel, kit.wideWithTop(kit.dp(12)));
        settingsPage = kit.scrollPage(content);
        return settingsPage;
    }

    public void cycleProfile(View source) {
        SensoryProfile next = surface.textureEngine.getProfile().next();
        surface.textureEngine.setProfile(next);
        profileButton.setText("Profile: " + next.displayName());
        surface.eyeView().setReducedMotion(
                reducedTextureSwitch.isChecked() || next.reducesContinuousTexture());
        updateSensorySummary();
        persistSensoryPreferences();
        surface.textureEngine.playBoundaryBump(source);
    }

    public void updateSensorySummary() {
        SensoryProfile profile = surface.textureEngine.getProfile();
        String audio = surface.textureEngine.isAudioEnabled() && profile != SensoryProfile.VISUAL_ONLY
                ? "audio on" : "audio off";
        String haptics = surface.textureEngine.isHapticsEnabled() && profile != SensoryProfile.VISUAL_ONLY
                ? "haptics on" : "haptics off";
        sensorySummary.setText(profile.displayName() + ": " + audio + ", " + haptics + ".");
    }

    public void restoreSensoryPreferences() {
        SharedPreferences preferences = surface.activity.getSharedPreferences(
                PREFERENCES, android.content.Context.MODE_PRIVATE);
        SensoryProfile profile;
        try {
            profile = SensoryProfile.valueOf(
                    preferences.getString("profile", SensoryProfile.BALANCED.name()));
        } catch (IllegalArgumentException exception) {
            profile = SensoryProfile.BALANCED;
        }
        suppressPreferenceCallbacks = true;
        surface.textureEngine.setProfile(profile);
        surface.textureEngine.setAudioEnabled(preferences.getBoolean("audio", true));
        surface.textureEngine.setHapticsEnabled(preferences.getBoolean("haptics", true));
        audioSwitch.setChecked(surface.textureEngine.isAudioEnabled());
        hapticsSwitch.setChecked(surface.textureEngine.isHapticsEnabled());
        shakeSwitch.setChecked(preferences.getBoolean("shake", true));
        boolean reduced = preferences.getBoolean("reduced_texture", false);
        reducedTextureSwitch.setChecked(reduced);
        surface.backgroundView.setReducedTexture(reduced);
        float albedo = preferences.getFloat("albedo_strength", TextureBackgroundView.DEFAULT_ALBEDO_STRENGTH);
        // Prior builds defaulted to 1.0 — treat that as unset and use the new 3× default.
        if (!preferences.contains("albedo_strength") || albedo <= 1.001f) {
            albedo = TextureBackgroundView.DEFAULT_ALBEDO_STRENGTH;
        }
        albedo = Math.max(0f, Math.min(TextureBackgroundView.MAX_ALBEDO_STRENGTH, albedo));
        if (albedoSeekBar != null) {
            albedoSeekBar.setProgress(Math.round(albedo * 100f));
        }
        surface.backgroundView.setAlbedoStrength(albedo);
        if (albedoValueLabel != null) {
            albedoValueLabel.setText(String.format(
                    Locale.US, "Reflection strength %.2f%s",
                    albedo,
                    surface.backgroundView.usesRuntimeShader() ? "" : " (fallback)"));
        }
        surface.eyeView().setReducedMotion(reduced || profile.reducesContinuousTexture());
        profileButton.setText("Profile: " + profile.displayName());
        suppressPreferenceCallbacks = false;
        updateSensorySummary();
        surface.backgroundView.post(surface::updateReflectionLights);
    }

    public void persistSensoryPreferences() {
        surface.activity.getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE).edit()
                .putString("profile", surface.textureEngine.getProfile().name())
                .putBoolean("audio", surface.textureEngine.isAudioEnabled())
                .putBoolean("haptics", surface.textureEngine.isHapticsEnabled())
                .putBoolean("shake", shakeSwitch.isChecked())
                .putBoolean("reduced_texture", reducedTextureSwitch.isChecked())
                .putFloat("albedo_strength", surface.backgroundView.getAlbedoStrength())
                .apply();
    }

    public void renderConnection(MainActivity.ConnectionState state, String detail) {
        surface.activity.runOnUiThread(() -> {
            surface.connectionState = state == null
                    ? MainActivity.ConnectionState.DISCONNECTED : state;
            String label = switch (surface.connectionState) {
                case CONNECTED -> "Connected";
                case CONNECTING -> "Connecting";
                case STALE -> "Connection recovering";
                case DISCONNECTED -> "Disconnected";
            };
            connectionStatus.setText(ChatListPresenter.joinStatus(label, detail));
            connectionStatus.setTextColor(surface.connectionState == MainActivity.ConnectionState.CONNECTED
                    ? UiKit.TEAL
                    : surface.connectionState == MainActivity.ConnectionState.STALE
                    ? UiKit.CRIMSON : UiKit.INK);
            EyeOfHorusView eyeView = surface.eyeView();
            if (eyeView != null) {
                eyeView.setState(ConversationController.eyeStateForSurface(
                        surface.connectionState, surface.sessionState));
            }
        });
    }

    public void refreshNotificationReader() {
        boolean granted = hasNotificationAccess();
        notificationAccessButton.setText(granted ? "Review notification access" : "Enable notification access");
        long now = System.currentTimeMillis();
        ListenerHealthStore.Snapshot health = surface.runtime().health().read();
        boolean live = TextureNotificationListenerService.hasLiveConnection();
        notificationStatus.setText(
                ListenerHealthPolicy.statusLabel(health, now, granted, live));
        if (!granted) {
            renderConnection(MainActivity.ConnectionState.DISCONNECTED, "Notification access required");
            surface.conversation.clearAttention();
            return;
        }

        NotificationHealthJobService.schedule(surface.activity);
        NotificationWatchdogScheduler.schedule(surface.activity);
        TextureNotificationListenerService.requestRebindNow(surface.activity);
        startCoreLink();
        surface.activity.requestNotificationPermissionIfNeeded();
        NotificationRuntime runtime = surface.runtime();
        coreLinkButton.setText(ConnectionConfigStore.isStub(surface.activity, runtime.getDeviceId())
                ? "Local only (Convex stubbed)"
                : ConnectionConfigStore.isConfigured(surface.activity, runtime.getDeviceId())
                ? "Reconnect Core" : "Configure Core");
        if (ConnectionConfigStore.isStub(surface.activity, runtime.getDeviceId())) {
            connectionStatus.setText("Local stub · Convex and VoiceOS offline");
            sessionStatus.setText("On-device only");
        }
        surface.activity.refreshLocalSurface();
    }

    public void startCoreLink() {
        NotificationRuntime runtime = surface.runtime();
        if (coreStartIssued) return;
        if (!ConnectionConfigStore.isConfigured(surface.activity, runtime.getDeviceId())
                || ConnectionConfigStore.isStub(surface.activity, runtime.getDeviceId())) {
            TextureFlowConnectionController.useLocalStub(surface.activity);
        }
        try {
            TextureFlowConnectionController.start(surface.activity);
            coreStartIssued = true;
            if (ConnectionConfigStore.isStub(surface.activity, runtime.getDeviceId())) {
                renderConnection(MainActivity.ConnectionState.CONNECTED, "Local stub · no Convex / VoiceOS");
            }
        } catch (RuntimeException unavailable) {
            coreStartIssued = false;
            renderConnection(MainActivity.ConnectionState.STALE, "Core link will retry");
        }
    }

    public void openNotificationAccessSettings(View ignored) {
        surface.activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    public void openCoreSetup(View ignored) {
        boolean stubbed = ConnectionConfigStore.isStub(
                surface.activity, surface.runtime().getDeviceId());
        new AlertDialog.Builder(surface.activity)
                .setTitle(stubbed ? "Local mode" : "TextureFlow Core")
                .setMessage(stubbed
                        ? "Convex and VoiceOS are stubbed. The phone runs on-device only "
                        + "(notifications, bank, Moth Market UI). You can delete those "
                        + "integrations later; this mode is the default now."
                        : "Live Convex is configured. Prefer local stub unless you are "
                        + "actively restoring cloud sync.")
                .setPositiveButton(stubbed ? "Keep local stub" : "Switch to local stub",
                        (dialog, which) -> {
                            TextureFlowConnectionController.stop(surface.activity);
                            coreStartIssued = false;
                            TextureFlowConnectionController.useLocalStub(surface.activity);
                            surface.mainHandler.postDelayed(this::startCoreLink, 250L);
                            renderConnection(MainActivity.ConnectionState.CONNECTED,
                                    "Local stub · no Convex / VoiceOS");
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    public void refreshCoreStatus() {
        if (!hasNotificationAccess()) return;
        long now = System.currentTimeMillis();
        ListenerHealthStore.Snapshot health = surface.runtime().health().read();
        boolean live = TextureNotificationListenerService.hasLiveConnection();
        boolean readerOk = ListenerHealthPolicy.isFresh(health, now) && live;

        ConnectionStatusStore.Snapshot status = ConnectionStatusStore.read(surface.activity);
        long age = now - status.lastOnlineAtMillis();
        if ("ONLINE".equals(status.state()) && age >= 0L && age <= 45_000L) {
            renderConnection(MainActivity.ConnectionState.CONNECTED,
                    readerOk ? "Core and reader active" : "Core online · reader reconnecting");
        } else if ("STARTING".equals(status.state()) || "REGISTERING".equals(status.state())) {
            renderConnection(MainActivity.ConnectionState.CONNECTING, status.detail());
        } else if ("BACKING_OFF".equals(status.state()) || "DEGRADED".equals(status.state())
                || age > 45_000L) {
            renderConnection(MainActivity.ConnectionState.STALE, "Core link recovering");
            if (!coreStartIssued) startCoreLink();
        }
        notificationStatus.setText(
                ListenerHealthPolicy.statusLabel(health, now, true, live));
        if (!readerOk) {
            // Reconcile alone cannot heal a live-flag zombie; escalate when policy says locked out.
            if (ListenerHealthPolicy.needsForceRestart(health, now, live)) {
                TextureNotificationListenerService.forceStaleRebind(surface.activity);
            } else {
                TextureNotificationListenerService.requestHealthReconciliation(surface.activity);
            }
        }
    }

    public boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                surface.activity.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName expected = new ComponentName(
                surface.activity, TextureNotificationListenerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName candidate = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(candidate)) return true;
        }
        return false;
    }
}
