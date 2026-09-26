package com.textureflow.ui.settings;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.textureflow.intelligence.api.AttentionEngine;
import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.engine.metrics.AssessmentMetricsPresenter;
import com.textureflow.intelligence.engine.metrics.ModelDownloadCopy;
import com.textureflow.intelligence.engine.metrics.PhoneBenchCopy;
import com.textureflow.intelligence.engine.metrics.PhoneBenchOutcome;
import com.textureflow.intelligence.engine.metrics.PhoneBenchRunner;
import com.textureflow.intelligence.model.AndroidNetworkPolicy;
import com.textureflow.intelligence.model.CapabilityProbe;
import com.textureflow.intelligence.model.DownloadListener;
import com.textureflow.intelligence.model.ModelDownloader;
import com.textureflow.intelligence.model.ModelFiles;
import com.textureflow.intelligence.model.ModelPorts;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.kit.UiKit;

/**
 * Intelligence block on Settings. Does not touch sensory / core / notification-reader
 * panels. Download starts with {@code ModelDownloader.gemma3(context).download(listener)}.
 */
public final class IntelligenceSection {
    private final MainSurface surface;
    private Switch modeSwitch;
    private Button downloadButton;
    private TextView downloadStatus;
    private Button benchButton;
    private TextView benchStatus;
    private TextView metricsStatus;
    private boolean suppressCallbacks;
    private boolean downloading;
    private String lastBenchResult;

    public IntelligenceSection(MainSurface surface) {
        this.surface = surface;
    }

    public Switch modeSwitch() {
        return modeSwitch;
    }

    public boolean isEnabled() {
        if (modeSwitch != null) {
            return modeSwitch.isChecked();
        }
        return IntelligencePreferences.isEnabled(surface.activity);
    }

    public LinearLayout build(UiKit kit) {
        LinearLayout panel = kit.surface(kit.dp(28));
        panel.addView(kit.sectionHeading("Intelligence"));

        modeSwitch = kit.settingSwitch("On-device intelligence");
        modeSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (suppressCallbacks) {
                return;
            }
            IntelligencePreferences.persist(surface.activity, checked);
            applyAttachment(checked);
        });
        panel.addView(modeSwitch);

        TextView pipLegend = kit.supportingValue(PhoneBenchCopy.pipLegend());
        panel.addView(pipLegend, kit.topMargin(kit.dp(6)));

        downloadStatus = kit.supportingValue(ModelDownloadCopy.idlePrompt());
        downloadStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(downloadStatus, kit.topMargin(kit.dp(12)));
        downloadButton = kit.button(ModelDownloadCopy.buttonIdle(), UiKit.TEAL);
        downloadButton.setOnClickListener(view -> startDownload());
        panel.addView(downloadButton, kit.topMargin(kit.dp(4)));

        benchStatus = kit.supportingValue(PhoneBenchCopy.plainTier(null));
        benchStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(benchStatus, kit.topMargin(kit.dp(10)));
        benchButton = kit.button("Test this phone", UiKit.TEAL);
        benchButton.setOnClickListener(view -> startBench());
        panel.addView(benchButton, kit.topMargin(kit.dp(4)));

        panel.addView(kit.sectionHeading("On-device metrics"), kit.topMargin(kit.dp(14)));
        metricsStatus = kit.supportingValue(AssessmentMetricsPresenter.emptyLabel());
        metricsStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(metricsStatus, kit.topMargin(kit.dp(4)));
        return panel;
    }

    public void restore() {
        boolean enabled = IntelligencePreferences.isEnabled(surface.activity);
        suppressCallbacks = true;
        if (modeSwitch != null) {
            modeSwitch.setChecked(enabled);
        }
        suppressCallbacks = false;
        applyAttachment(enabled);
        refreshPanel();
    }

    /**
     * Called from the existing settings refresh. When intelligence is off, detach
     * after {@code MainActivity} {@code attachIfPresent} so resume cannot re-bind.
     */
    public void onHostRefresh() {
        if (!isEnabled()) {
            applyAttachment(false);
        }
        refreshPanel();
    }

    public void applyAttachment(boolean enabled) {
        if (enabled) {
            AttentionEngine engine = surface.runtime().attentionEngine();
            surface.activity.setAttentionEngine(engine);
        } else {
            surface.activity.setAttentionEngine(null);
        }
    }

    void refreshPanel() {
        refreshDownloadRow();
        refreshTierLine();
        refreshMetrics();
    }

    private void refreshDownloadRow() {
        if (downloadButton == null || downloading) {
            return;
        }
        boolean present = ModelFiles.isPresent(ModelFiles.resolve(surface.activity));
        boolean wifi = new AndroidNetworkPolicy(surface.activity).allowDownload();
        if (present) {
            downloadButton.setText(ModelDownloadCopy.buttonReady());
            downloadButton.setEnabled(false);
            downloadStatus.setText(ModelDownloadCopy.ready());
            return;
        }
        if (!wifi) {
            downloadButton.setText(ModelDownloadCopy.buttonWifiBlocked());
            downloadButton.setEnabled(false);
            downloadStatus.setText(ModelDownloadCopy.wifiRequired());
            return;
        }
        downloadButton.setText(ModelDownloadCopy.buttonIdle());
        downloadButton.setEnabled(true);
        downloadStatus.setText(ModelDownloadCopy.idlePrompt());
    }

    private void refreshTierLine() {
        if (benchStatus == null || lastBenchResult != null) {
            return;
        }
        if (benchButton != null && !benchButton.isEnabled()) {
            return;
        }
        CapabilityProfile profile = CapabilityProbe.fromAndroid(surface.activity);
        if (!ModelFiles.isPresent(ModelFiles.resolve(surface.activity))) {
            benchStatus.setText(PhoneBenchCopy.missingFile(profile.getTier()));
            return;
        }
        benchStatus.setText(PhoneBenchCopy.plainTier(profile.getTier()));
    }

    private void refreshMetrics() {
        if (metricsStatus == null) {
            return;
        }
        metricsStatus.setText(SettingsMetrics.summary(surface.runtime().ledger()));
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        if (!new AndroidNetworkPolicy(surface.activity).allowDownload()) {
            downloadButton.setEnabled(false);
            downloadButton.setText(ModelDownloadCopy.buttonWifiBlocked());
            downloadStatus.setText(ModelDownloadCopy.wifiRequired());
            return;
        }
        downloading = true;
        downloadButton.setEnabled(false);
        downloadButton.setText(ModelDownloadCopy.downloading());
        downloadStatus.setText(ModelDownloadCopy.progress(0L, ModelDownloadCopy.artifactBytes()));
        surface.actionExecutor.execute(() -> {
            try {
                ModelDownloader downloader = ModelDownloader.gemma3(surface.activity);
                DownloadListener listener = (written, total) -> surface.mainHandler.post(() -> {
                    if (downloadStatus != null) {
                        downloadStatus.setText(ModelDownloadCopy.progress(written, total));
                    }
                });
                downloader.download(listener);
                surface.mainHandler.post(() -> {
                    downloading = false;
                    downloadStatus.setText(ModelDownloadCopy.ready());
                    refreshDownloadRow();
                    refreshTierLine();
                });
            } catch (Exception error) {
                surface.mainHandler.post(() -> {
                    downloading = false;
                    downloadStatus.setText(ModelDownloadCopy.failed(
                            error.getMessage() == null ? "" : error.getMessage()));
                    refreshDownloadRow();
                });
            }
        });
    }

    private void startBench() {
        if (benchButton != null) {
            benchButton.setEnabled(false);
        }
        benchStatus.setText(PhoneBenchCopy.running());
        surface.actionExecutor.execute(() -> {
            boolean present = ModelFiles.isPresent(ModelFiles.resolve(surface.activity));
            CapabilityProfile profile = CapabilityProbe.fromAndroid(surface.activity);
            PhoneBenchOutcome outcome;
            if (!present) {
                outcome = new PhoneBenchOutcome(PhoneBenchCopy.missingFile(profile.getTier()), null);
            } else {
                outcome = PhoneBenchRunner.runMeasured(
                        true,
                        profile.getTier(),
                        ModelPorts.open(surface.activity),
                        System.currentTimeMillis());
                if (outcome.getBench() != null) {
                    try {
                        surface.runtime().ledger().saveCapabilityProfile(
                                CapabilityProbe.fromAndroid(surface.activity, outcome.getBench()));
                    } catch (RuntimeException ignored) {
                        // Persist must not fail the wizard copy.
                    }
                }
            }
            String shown = outcome.getCopy();
            surface.mainHandler.post(() -> {
                lastBenchResult = shown;
                if (benchStatus != null) {
                    benchStatus.setText(shown);
                }
                if (benchButton != null) {
                    benchButton.setEnabled(true);
                }
            });
        });
    }
}
