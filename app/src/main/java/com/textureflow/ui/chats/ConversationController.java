package com.textureflow.ui.chats;

import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.connection.ConnectionConfig;
import com.textureflow.connection.ConnectionConfigStore;
import com.textureflow.connection.CoreActionClient;
import com.textureflow.connection.LocalCoreActionClient;
import com.textureflow.connection.TextureFlowConnectionController;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.notifications.TextureNotificationListenerService;
import com.textureflow.texture.TextureCue;
import com.textureflow.ui.EyeOfHorusView;
import com.textureflow.ui.MainActivity;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.MothMarketTheme;
import com.textureflow.ui.TextureDrawableFactory;
import com.textureflow.ui.kit.UiKit;
import com.textureflow.ui.nav.Page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConversationController {
    private final MainSurface surface;
    private FrameLayout chatThreadPage;
    private LinearLayout conversationMessages;
    private TextView conversationTitle;
    private String activeConversationKey;
    private StoredNotificationEvent currentAttention;
    private final Set<String> handledAttentionKeys = new LinkedHashSet<>();
    private final Map<String, Long> hiddenEventUntil = new LinkedHashMap<>();
    private int activeQueueSize;
    private String currentProposalId;
    private CoreActionClient.Proposal activePhoneProposal;
    private LocalCoreActionClient localActionClient;
    private StoredNotificationEvent activeProposalEvent;
    private boolean phoneActionBusy;
    private final ProposalFlowPresenter proposalFlow = new ProposalFlowPresenter();
    private String intelligenceProposalId;

    public ConversationController(MainSurface surface) {
        this.surface = surface;
    }

    public FrameLayout page() {
        return chatThreadPage;
    }

    public StoredNotificationEvent currentAttention() {
        return currentAttention;
    }

    public CoreActionClient.Proposal activePhoneProposal() {
        return activePhoneProposal;
    }

    public List<StoredNotificationEvent> attentionQueue(List<StoredNotificationEvent> live) {
        return ChatListPresenter.attentionQueue(
                live,
                handledAttentionKeys,
                hiddenEventUntil,
                System.currentTimeMillis(),
                surface.chats == null ? Map.of() : surface.chats.assessments());
    }

    public ProposalFlowPresenter proposalFlow() {
        return proposalFlow;
    }

    public FrameLayout buildPage() {
        UiKit kit = surface.kit;
        LinearLayout content = kit.pageColumn();
        content.setPadding(kit.dp(14), kit.dp(14), kit.dp(14), kit.dp(12));

        LinearLayout header = new LinearLayout(surface.activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = kit.compactButton("Back", UiKit.TEAL);
        back.setOnClickListener(view -> closeConversation());
        header.addView(back, kit.narrowStart());
        conversationTitle = kit.text("", 22, UiKit.INK, true);
        conversationTitle.setAccessibilityHeading(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(kit.dp(12), 0, 0, 0);
        header.addView(conversationTitle, titleParams);
        content.addView(header, kit.matchWrap());

        conversationMessages = new LinearLayout(surface.activity);
        conversationMessages.setOrientation(LinearLayout.VERTICAL);
        conversationMessages.setPadding(0, kit.dp(12), 0, kit.dp(12));
        content.addView(conversationMessages, kit.matchWrap());
        chatThreadPage = kit.scrollPage(content);
        return chatThreadPage;
    }

    public void openConversation(PersonTimeline person) {
        activeConversationKey = ChatListPresenter.normalizePersonKey(person.name);
        conversationTitle.setText(person.name);
        conversationMessages.removeAllViews();
        UiKit kit = surface.kit;
        if (person.demo) {
            for (ChatBubble bubble : person.demoMessages) {
                conversationMessages.addView(messageBubble(bubble.outbound, bubble.body, bubble.at),
                        kit.wideWithBottom(kit.dp(8)));
            }
        } else {
            List<StoredNotificationEvent> events = new ArrayList<>(person.events);
            events.sort(Comparator.comparingLong(StoredNotificationEvent::getUpdatedAt));
            for (StoredNotificationEvent event : events) {
                String body = ChatListPresenter.emptyFallback(event.getBody(), "Notification content unavailable");
                conversationMessages.addView(
                        messageBubble(false, body, event.getUpdatedAt()),
                        kit.wideWithBottom(kit.dp(8)));
            }
        }
        surface.showPage(Page.CHAT);
        surface.textureEngine.playBoundaryBump(conversationMessages);
        StoredNotificationEvent lead = latestReplyEvent(person);
        if (lead != null && surface.intel != null) {
            surface.intel.requestDraftFor(lead);
            applyPendingDraftFor(lead);
        }
    }

    private static StoredNotificationEvent latestReplyEvent(PersonTimeline person) {
        StoredNotificationEvent lead = null;
        for (StoredNotificationEvent event : person.events) {
            if (!event.hasCapability("REPLY")) continue;
            if (lead == null || event.getUpdatedAt() > lead.getUpdatedAt()) lead = event;
        }
        return lead;
    }

    private void applyPendingDraftFor(StoredNotificationEvent event) {
        if (event == null || surface.intel == null) return;
        for (ProposalDraft draft : surface.intel.pendingDrafts().values()) {
            if (event.getEventId().equals(draft.getEventId())) {
                offerProposalDraft(draft);
                return;
            }
        }
    }

    private View messageBubble(boolean outbound, String body, long at) {
        UiKit kit = surface.kit;
        LinearLayout wrap = new LinearLayout(surface.activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(outbound ? Gravity.END : Gravity.START);

        LinearLayout bubble = new LinearLayout(surface.activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(8));
        bubble.setBackground(MothMarketTheme.roundRect(
                outbound ? MothMarketTheme.BUBBLE_OUT : MothMarketTheme.BUBBLE_IN, 18f, surface.activity));
        bubble.setElevation(kit.dp(1));
        TextView bodyView = kit.text(body, 16, UiKit.INK, false);
        bodyView.setLineSpacing(kit.dp(2), 1f);
        bubble.addView(bodyView);
        TextView meta = kit.text(ChatListPresenter.clockTime(at), 11, UiKit.MUTED, false);
        meta.setGravity(outbound ? Gravity.END : Gravity.START);
        bubble.addView(meta, kit.topMargin(kit.dp(4)));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.gravity = outbound ? Gravity.END : Gravity.START;
        bubbleParams.setMargins(outbound ? kit.dp(48) : 0, 0, outbound ? 0 : kit.dp(48), 0);
        wrap.addView(bubble, bubbleParams);
        return wrap;
    }

    public void closeConversation() {
        activeConversationKey = null;
        surface.showPage(Page.CHATS);
        surface.textureEngine.playBoundaryBump(surface.chats.chatListContainer());
    }

    public void refreshAttention(List<StoredNotificationEvent> live) {
        List<StoredNotificationEvent> queue = attentionQueue(live);
        activeQueueSize = queue.size();
        StoredNotificationEvent attention = queue.isEmpty() ? null : queue.get(0);
        boolean actionInProgress = activePhoneProposal != null || phoneActionBusy
                || surface.sessionState == MainActivity.SessionState.EXECUTING;
        if (!actionInProgress) {
            if (attention == null) {
                clearAttention();
            } else if (currentAttention == null
                    || !currentAttention.getEventId().equals(attention.getEventId())
                    || currentAttention.getVersion() != attention.getVersion()) {
                currentAttention = attention;
                String reason = attention.getPriorityReason();
                boolean urgent = "URGENT".equals(attention.getPriorityLevel());
                if (surface.chats != null) {
                    AttentionAssessment assessment = ChatListPresenter.assessmentForEvent(
                            attention, surface.chats.assessments());
                    if (assessment != null) {
                        reason = assessment.getReason();
                        urgent = assessment.getLevel() == AttentionLevel.URGENT;
                    }
                }
                renderAttention(attention.getEventId(), attention.getSenderName(), attention.getAppLabel(),
                        attention.getBody(), reason, urgent);
                renderResponseOptions(attention);
                if (surface.intel != null) {
                    surface.intel.requestDraftFor(attention);
                    applyPendingDraftFor(attention);
                }
            }
        }
    }

    public void renderResponseOptions(StoredNotificationEvent event) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        UiKit kit = surface.kit;
        legacy.responseOptions.removeAllViews();
        List<String> actions = new ArrayList<>();
        if (event.hasCapability("REPLY")) {
            actions.add("Send");
            legacy.responseEditor.setEnabled(true);
            legacy.responseEditor.setHint("Edit suggested reply");
            legacy.responseEditor.setText(ChatListPresenter.suggestReply(event));
            legacy.responseEditor.setSelection(legacy.responseEditor.length());
            legacy.responseTitle.setText("Suggested reply");
        } else {
            legacy.responseEditor.setVisibility(View.GONE);
            legacy.responseTitle.setText("Choose an action");
        }
        if (event.hasCapability("SNOOZE")) actions.add("Later");
        if (event.hasCapability("DISMISS")) actions.add("Done");
        for (String action : actions) {
            Button option = kit.compactButton(action, "Send".equals(action) ? UiKit.TEAL : UiKit.AMBER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            params.setMargins(kit.dp(3), 0, kit.dp(3), 0);
            option.setLayoutParams(params);
            option.setOnClickListener(view -> selectResponseAction(action));
            legacy.responseOptions.addView(option);
        }
    }

    public void selectResponseAction(String action) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        legacy.responseStatus.setVisibility(View.VISIBLE);
        if (currentAttention == null || phoneActionBusy) return;
        String capability = "Send".equals(action) ? "REPLY"
                : "Later".equals(action) ? "SNOOZE" : "DISMISS";
        if (!currentAttention.hasCapability(capability)) {
            legacy.responseStatus.setText(action + " is not available for this notification");
            surface.voiceController.speak(action + " is not available for this notification.");
            return;
        }
        if ("Send".equals(action) && legacy.responseEditor.getText().toString().trim().isEmpty()) {
            legacy.responseEditor.requestFocus();
            InputMethodManager keyboard = surface.activity.getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.showSoftInput(legacy.responseEditor, InputMethodManager.SHOW_IMPLICIT);
            legacy.responseStatus.setText("Write a reply first");
            return;
        }
        ActionType type = "Send".equals(action) ? ActionType.REPLY
                : "Later".equals(action) ? ActionType.SNOOZE : ActionType.DISMISS;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (type == ActionType.REPLY) payload.put("message", legacy.responseEditor.getText().toString().trim());
        if (type == ActionType.SNOOZE) payload.put("minutes", 60L);
        preparePhoneAction(currentAttention, type, payload);
        surface.textureEngine.playGlassTouch(legacy.responsePanel);
    }

    private void preparePhoneAction(
            StoredNotificationEvent event, ActionType type, Map<String, Object> payload) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(
                    surface.activity, surface.runtime().getDeviceId());
        } catch (RuntimeException missingConfig) {
            TextureFlowConnectionController.useLocalStub(surface.activity);
            config = ConnectionConfigStore.load(
                    surface.activity, surface.runtime().getDeviceId());
        }

        phoneActionBusy = true;
        setResponseActionsEnabled(false);
        legacy.responseStatus.setText("Preparing exact preview");
        ConnectionConfig finalConfig = config;
        surface.actionExecutor.execute(() -> {
            try {
                CoreActionClient.Proposal proposal;
                if (finalConfig.isStub()) {
                    if (localActionClient == null) {
                        localActionClient = new LocalCoreActionClient(surface.activity, finalConfig);
                    }
                    proposal = localActionClient.create(event, type, payload);
                } else {
                    String actionToken = ConnectionConfigStore.loadUserActionToken(surface.activity);
                    if (actionToken == null || actionToken.trim().isEmpty()) {
                        surface.activity.runOnUiThread(() -> {
                            finishPhoneActionFailure(
                                    "Add the User action token in Settings to use live Core.");
                            surface.showPage(Page.SETTINGS);
                        });
                        return;
                    }
                    CoreActionClient client = new CoreActionClient(finalConfig, actionToken);
                    proposal = client.create(event, type, payload);
                }
                CoreActionClient.Proposal ready = proposal;
                surface.activity.runOnUiThread(() -> renderPhoneProposal(event, ready));
            } catch (Exception failure) {
                String reason = failure.getMessage();
                surface.activity.runOnUiThread(() -> finishPhoneActionFailure(reason == null
                        ? "Could not prepare that action."
                        : reason));
            }
        });
    }

    public static MainActivity.ReceiptState receiptState(String status) {
        if ("DISPATCHED".equals(status)) return MainActivity.ReceiptState.DISPATCHED;
        if ("EXPIRED".equals(status)) return MainActivity.ReceiptState.EXPIRED;
        if ("STALE".equals(status)) return MainActivity.ReceiptState.STALE;
        return MainActivity.ReceiptState.FAILED;
    }

    private void renderPhoneProposal(
            StoredNotificationEvent event, CoreActionClient.Proposal proposal) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        phoneActionBusy = false;
        activePhoneProposal = proposal;
        activeProposalEvent = event;
        currentProposalId = proposal.proposalId();
        surface.sessionState = MainActivity.SessionState.AWAITING_CONFIRMATION;
        legacy.responseOptions.setVisibility(View.GONE);
        legacy.responseStatus.setText("Exact preview: " + proposal.spokenPreview());
        legacy.responseStatus.setVisibility(View.VISIBLE);
        legacy.confirmButton.setText(proposal.actionType() == ActionType.REPLY ? "Send now" : "Confirm");
        legacy.confirmButton.setVisibility(View.VISIBLE);
        legacy.cancelButton.setVisibility(View.VISIBLE);
        legacy.cancelButton.setText("Back to actions");
        legacy.confirmButton.setEnabled(true);
        legacy.cancelButton.setEnabled(true);
        if (proposal.actionType() == ActionType.REPLY) {
            legacy.responseTitle.setText("Proposed reply");
            legacy.responseEditor.setEnabled(true);
            legacy.responseEditor.setVisibility(View.VISIBLE);
        }
        legacy.eyeView.setState(EyeOfHorusView.State.AWAITING_CONFIRMATION);
        surface.textureEngine.emit(TextureCue.PROPOSAL_READY, proposal.proposalId(), legacy.responsePanel);
        surface.voiceController.speakAndListen(proposal.spokenPreview()
                + " Say confirm to authorize it, change it, or cancel.");
    }

    private void setResponseActionsEnabled(boolean enabled) {
        LinearLayout options = surface.chats.legacy().responseOptions;
        for (int index = 0; index < options.getChildCount(); index++) {
            options.getChildAt(index).setEnabled(enabled);
        }
    }

    private void finishPhoneActionFailure(String message) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        phoneActionBusy = false;
        setResponseActionsEnabled(true);
        legacy.confirmButton.setEnabled(true);
        legacy.cancelButton.setEnabled(true);
        legacy.responseStatus.setText(message);
        legacy.responseStatus.setVisibility(View.VISIBLE);
        surface.textureEngine.emit(TextureCue.ACTION_FAILED, "phone-action", legacy.responsePanel);
    }

    public void renderListening(String sessionId) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            surface.sessionState = MainActivity.SessionState.LISTENING;
            surface.settings.sessionStatus().setText("Listening");
            legacy.eyeView.setState(EyeOfHorusView.State.LISTENING);
            surface.textureEngine.emit(TextureCue.LISTENING_STARTED,
                    ChatListPresenter.safeId(sessionId, "session"), legacy.eyeView);
        });
    }

    public void renderThinking(String detail) {
        surface.activity.runOnUiThread(() -> {
            surface.sessionState = MainActivity.SessionState.THINKING;
            surface.settings.sessionStatus().setText(ChatListPresenter.joinStatus("Understanding", detail));
            surface.chats.legacy().eyeView.setState(EyeOfHorusView.State.THINKING);
        });
    }

    public void renderIdle(String detail) {
        surface.activity.runOnUiThread(() -> {
            surface.sessionState = MainActivity.SessionState.IDLE;
            surface.settings.sessionStatus().setText(ChatListPresenter.joinStatus("Ready", detail));
            surface.chats.legacy().eyeView.setState(eyeStateForSurface(
                    surface.connectionState, surface.sessionState));
        });
    }

    public void renderAttention(String eventId, String sender, String app, String body,
                                String priorityReason, boolean urgent) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            UiKit kit = surface.kit;
            surface.sessionState = MainActivity.SessionState.PRESENTING;
            String source = ChatListPresenter.joinStatus(
                    ChatListPresenter.emptyFallback(sender, "Unknown sender"),
                    ChatListPresenter.emptyFallback(app, "Unknown app"));
            legacy.attentionMeta.setText(activeQueueSize > 1
                    ? source + " · 1 of " + activeQueueSize : source);
            legacy.attentionBody.setText(ChatListPresenter.emptyFallback(body, "Notification content is unavailable."));
            legacy.attentionReason.setText(ChatListPresenter.emptyFallback(priorityReason,
                    urgent ? "Urgent" : "Important"));
            legacy.attentionPanel.setBackground(urgent
                    ? TextureDrawableFactory.emphasizedPanel(surface.activity, kit.dp(30), UiKit.AMBER)
                    : TextureDrawableFactory.quietPanel(surface.activity, kit.dp(30)));
            // Keep legacy attention chrome hidden on the Moth Market chat list.
            legacy.attentionPanel.setVisibility(View.GONE);
            legacy.responseSmile.setVisibility(View.GONE);
            legacy.responseTitle.setVisibility(View.VISIBLE);
            legacy.responseEditor.setVisibility(View.VISIBLE);
            legacy.responseOptions.setVisibility(View.VISIBLE);
            surface.settings.sessionStatus().setText(urgent ? "Urgent item present" : "Important item present");
            if (urgent) surface.textureEngine.emit(TextureCue.ATTENTION_URGENT,
                    ChatListPresenter.safeId(eventId, "event"), surface.chats.page());
        });
    }

    public void clearAttention() {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            currentAttention = null;
            activePhoneProposal = null;
            activeProposalEvent = null;
            phoneActionBusy = false;
            legacy.attentionPanel.setVisibility(View.GONE);
            legacy.responseSmile.setVisibility(View.VISIBLE);
            legacy.responseTitle.setVisibility(View.GONE);
            legacy.responseEditor.setVisibility(View.GONE);
            legacy.responseEditor.setText("");
            legacy.responseOptions.setVisibility(View.GONE);
            legacy.responseOptions.removeAllViews();
            legacy.responseStatus.setVisibility(View.GONE);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            currentProposalId = null;
            intelligenceProposalId = null;
            proposalFlow.clearActive();
        });
    }

    public void renderProposal(String proposalId, String spokenPreview, String expiryDescription) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            currentProposalId = proposalId;
            surface.sessionState = MainActivity.SessionState.AWAITING_CONFIRMATION;
            legacy.responseSmile.setVisibility(View.GONE);
            legacy.responseTitle.setVisibility(View.VISIBLE);
            legacy.responseTitle.setText("Proposed response");
            legacy.responseEditor.setVisibility(View.VISIBLE);
            legacy.responseEditor.setText(ChatListPresenter.emptyFallback(spokenPreview, "Proposal preview unavailable."));
            legacy.responseEditor.setEnabled(false);
            legacy.responseOptions.setVisibility(View.GONE);
            legacy.responseStatus.setText(ChatListPresenter.joinStatus("Awaiting confirmation", expiryDescription));
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.confirmButton.setVisibility(View.VISIBLE);
            legacy.cancelButton.setVisibility(View.VISIBLE);
            legacy.confirmButton.setEnabled(true);
            legacy.cancelButton.setEnabled(true);
            legacy.eyeView.setState(EyeOfHorusView.State.PROPOSAL);
            // Proposal chrome stays on the hidden legacy panel until chat actions land.
            surface.textureEngine.emit(TextureCue.PROPOSAL_READY,
                    ChatListPresenter.safeId(proposalId, "proposal"), legacy.responsePanel);
        });
    }

    public void renderExecution(String commandId, String detail) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            surface.sessionState = MainActivity.SessionState.EXECUTING;
            legacy.responseStatus.setText(ChatListPresenter.joinStatus("Executing confirmed action", detail));
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            legacy.eyeView.setState(EyeOfHorusView.State.EXECUTING);
            surface.textureEngine.emit(TextureCue.EXECUTION_STARTED,
                    ChatListPresenter.safeId(commandId, "command"), legacy.responsePanel);
        });
    }

    public void renderReceipt(String receiptId, MainActivity.ReceiptState state, String message) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            MainActivity.ReceiptState safeState = state == null ? MainActivity.ReceiptState.FAILED : state;
            boolean dispatched = safeState == MainActivity.ReceiptState.DISPATCHED;
            surface.sessionState = dispatched
                    ? MainActivity.SessionState.RECEIPT : MainActivity.SessionState.FAILED;
            String status = switch (safeState) {
                case DISPATCHED -> "Dispatched";
                case EXPIRED -> "Command expired";
                case STALE -> "Notification changed";
                case FAILED -> "Action failed";
            };
            surface.settings.receiptStatus().setText(status);
            surface.settings.receiptStatus().setTextColor(dispatched ? UiKit.TEAL : UiKit.CRIMSON);
            surface.settings.receiptDetail().setText(ChatListPresenter.emptyFallback(message,
                    dispatched ? "Android dispatched the action." : "Android did not dispatch the action."));
            legacy.responseStatus.setText(status);
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            currentProposalId = null;
            legacy.cancelButton.setText("Cancel");
            surface.textureEngine.emit(dispatched ? TextureCue.ACTION_DISPATCHED : TextureCue.ACTION_FAILED,
                    ChatListPresenter.safeId(receiptId, "receipt"), legacy.responsePanel);
            if (!dispatched && currentAttention != null) {
                legacy.responseEditor.setEnabled(true);
                renderResponseOptions(currentAttention);
                legacy.responseOptions.setVisibility(View.VISIBLE);
            }
            surface.mainHandler.postDelayed(surface.activity::refreshLocalSurface, 350L);
        });
    }

    public void renderCancelled(String proposalId, String message) {
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            surface.sessionState = MainActivity.SessionState.CANCELLED;
            legacy.responseStatus.setText(ChatListPresenter.joinStatus("Cancelled", message));
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            legacy.responseEditor.setEnabled(true);
            currentProposalId = null;
            surface.textureEngine.emit(TextureCue.CANCELLED,
                    ChatListPresenter.safeId(proposalId, "proposal"), legacy.responsePanel);
        });
    }

    public void offerProposalDraft(ProposalDraft draft) {
        if (draft == null) return;
        StoredNotificationEvent event = eventForDraft(draft);
        int version = event == null ? draft.getEventVersion() : event.getVersion();
        long now = System.currentTimeMillis();
        ProposalFlowPresenter.OfferResult result = proposalFlow.offer(draft, version, now);
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            if (result == ProposalFlowPresenter.OfferResult.STALE) {
                legacy.responseEditor.setText("");
                legacy.responseStatus.setText("That suggestion is no longer current.");
                legacy.responseStatus.setVisibility(View.VISIBLE);
                return;
            }
            if (phoneActionBusy || activePhoneProposal != null) return;
            intelligenceProposalId = draft.getProposalId();
            currentProposalId = draft.getProposalId();
            String readBack = ProposalFlowPresenter.readBackText(draft);
            if (event != null && (currentAttention == null
                    || !currentAttention.getEventId().equals(event.getEventId()))) {
                currentAttention = event;
            }
            if (draft.getActionType() == ActionType.REPLY) {
                legacy.responseEditor.setVisibility(View.VISIBLE);
                legacy.responseEditor.setEnabled(true);
                legacy.responseEditor.setText(draft.getReplyText());
                legacy.responseEditor.setSelection(legacy.responseEditor.length());
                legacy.responseTitle.setText("Proposed reply");
            }
            legacy.responseStatus.setText("Exact preview: " + readBack);
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.responseOptions.setVisibility(View.GONE);
            legacy.confirmButton.setText(draft.getActionType() == ActionType.REPLY
                    ? "Send now" : "Confirm");
            legacy.confirmButton.setVisibility(View.VISIBLE);
            legacy.confirmButton.setEnabled(true);
            legacy.cancelButton.setText("Back to actions");
            legacy.cancelButton.setVisibility(View.VISIBLE);
            legacy.cancelButton.setEnabled(true);
            surface.sessionState = MainActivity.SessionState.AWAITING_CONFIRMATION;
            legacy.eyeView.setState(EyeOfHorusView.State.AWAITING_CONFIRMATION);
            surface.textureEngine.emit(TextureCue.PROPOSAL_READY, draft.getProposalId(),
                    legacy.responsePanel);
            surface.voiceController.speakAndListen(readBack
                    + " Say confirm to authorize it, change it, or cancel.");
        });
    }

    public void onProposalInvalidated(String proposalId, String reason) {
        boolean matched = proposalFlow.invalidate(proposalId, reason)
                || (proposalId != null && proposalId.equals(intelligenceProposalId));
        if (!matched) return;
        surface.activity.runOnUiThread(() -> {
            ChatListController.LegacyControls legacy = surface.chats.legacy();
            intelligenceProposalId = null;
            currentProposalId = null;
            activePhoneProposal = null;
            activeProposalEvent = null;
            phoneActionBusy = false;
            legacy.responseEditor.setText("");
            legacy.responseEditor.setEnabled(true);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            String detail = ChatListPresenter.emptyFallback(reason, "The suggestion is no longer valid.");
            legacy.responseStatus.setText(detail);
            legacy.responseStatus.setVisibility(View.VISIBLE);
            if (currentAttention != null) {
                renderResponseOptions(currentAttention);
                legacy.responseOptions.setVisibility(View.VISIBLE);
            }
            surface.sessionState = MainActivity.SessionState.CANCELLED;
            surface.textureEngine.emit(TextureCue.CANCELLED,
                    ChatListPresenter.safeId(proposalId, "proposal"), legacy.responsePanel);
        });
    }

    private StoredNotificationEvent eventForDraft(ProposalDraft draft) {
        if (draft == null) return null;
        if (currentAttention != null && draft.getEventId().equals(currentAttention.getEventId())) {
            return currentAttention;
        }
        if (activeProposalEvent != null && draft.getEventId().equals(activeProposalEvent.getEventId())) {
            return activeProposalEvent;
        }
        for (StoredNotificationEvent event : surface.runtime().notifications().getLiveEvents()) {
            if (draft.getEventId().equals(event.getEventId())) return event;
        }
        for (StoredNotificationEvent event : surface.runtime().notifications().getRecentEvents(100)) {
            if (draft.getEventId().equals(event.getEventId())) return event;
        }
        return null;
    }

    public void requestConfirmation(View source) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        if (proposalFlow.active() != null && activePhoneProposal == null) {
            confirmIntelligenceDraft();
            return;
        }
        if (activePhoneProposal != null) {
            confirmPhoneProposal();
            return;
        }
        if (currentProposalId == null || surface.actionRequestListener == null) {
            legacy.responseStatus.setText("No pending proposal to confirm");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            return;
        }
        legacy.confirmButton.setEnabled(false);
        legacy.cancelButton.setEnabled(false);
        legacy.responseStatus.setText("Confirmation requested");
        surface.actionRequestListener.onConfirmRequested(currentProposalId);
    }

    private void confirmIntelligenceDraft() {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        ProposalDraft draft = proposalFlow.active();
        if (draft == null || phoneActionBusy) return;
        StoredNotificationEvent event = eventForDraft(draft);
        long now = System.currentTimeMillis();
        int version = event == null ? -1 : event.getVersion();
        if (event == null || ProposalFlowPresenter.isStale(draft, version, now)) {
            proposalFlow.invalidate(draft.getProposalId(), "The notification changed.");
            legacy.responseEditor.setText("");
            legacy.responseStatus.setText("That suggestion is no longer current.");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            if (currentAttention != null) {
                renderResponseOptions(currentAttention);
                legacy.responseOptions.setVisibility(View.VISIBLE);
            }
            return;
        }
        String edited = draft.getActionType() == ActionType.REPLY
                ? legacy.responseEditor.getText().toString().trim() : "";
        Map<String, Object> payload = ProposalFlowPresenter.confirmPayload(draft, edited);
        ConfirmedProposal confirmation;
        try {
            ConnectionConfig config = ConnectionConfigStore.load(
                    surface.activity, surface.runtime().getDeviceId());
            confirmation = ProposalFlowPresenter.toConfirmedProposal(
                    draft,
                    config.ownerId(),
                    config.deviceId(),
                    java.time.Instant.now().toString(),
                    payload);
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Local configuration is unavailable.");
            return;
        }
        if (confirmation.getEventId().isEmpty()
                || confirmation.getExpectedEventVersion() != event.getVersion()) {
            legacy.responseStatus.setText("That suggestion is no longer current.");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            return;
        }
        proposalFlow.clearActive();
        intelligenceProposalId = null;
        executeConfirmedDraft(event, draft.getActionType(), payload, confirmation);
    }

    /**
     * One-tap confirm for an engine draft: the read-back was already shown.
     * Builds {@link ConfirmedProposal} then runs the existing local/Core path
     * that feeds {@code CommandPolicy}.
     */
    private void executeConfirmedDraft(
            StoredNotificationEvent event,
            ActionType type,
            Map<String, Object> payload,
            ConfirmedProposal confirmation) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(
                    surface.activity, surface.runtime().getDeviceId());
        } catch (RuntimeException missing) {
            TextureFlowConnectionController.useLocalStub(surface.activity);
            config = ConnectionConfigStore.load(
                    surface.activity, surface.runtime().getDeviceId());
        }
        phoneActionBusy = true;
        setResponseActionsEnabled(false);
        legacy.confirmButton.setEnabled(false);
        legacy.cancelButton.setEnabled(false);
        renderExecution(confirmation.getProposalId(), "Executing confirmed suggestion");
        ConnectionConfig finalConfig = config;
        surface.actionExecutor.execute(() -> {
            try {
                CoreActionClient.Proposal proposal;
                if (finalConfig.isStub()) {
                    if (localActionClient == null) {
                        localActionClient = new LocalCoreActionClient(surface.activity, finalConfig);
                    }
                    proposal = localActionClient.create(event, type, payload);
                    CoreActionClient.Confirmation result = localActionClient.confirm(proposal);
                    surface.activity.runOnUiThread(() ->
                            finishPhoneConfirmation(proposal, event, result.receipt()));
                    return;
                }
                String actionToken = ConnectionConfigStore.loadUserActionToken(surface.activity);
                if (actionToken == null || actionToken.trim().isEmpty()) {
                    surface.activity.runOnUiThread(() -> {
                        finishPhoneActionFailure(
                                "Add the User action token in Settings to use live Core.");
                        surface.showPage(Page.SETTINGS);
                    });
                    return;
                }
                CoreActionClient client = new CoreActionClient(finalConfig, actionToken);
                proposal = client.create(event, type, payload);
                CoreActionClient.Confirmation result = client.confirm(proposal);
                CoreActionClient.Receipt receipt = result.receipt() == null
                        ? client.awaitReceipt(result.commandId(), 65_000L)
                        : result.receipt();
                surface.activity.runOnUiThread(() ->
                        finishPhoneConfirmation(proposal, event, receipt));
            } catch (Exception failure) {
                surface.activity.runOnUiThread(() -> finishPhoneActionFailure(
                        "Action was not confirmed. It remains unsent."));
            }
        });
    }

    public void requestCancellation(View source) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        if (proposalFlow.active() != null && activePhoneProposal == null) {
            ProposalDraft draft = proposalFlow.active();
            proposalFlow.clearActive();
            intelligenceProposalId = null;
            currentProposalId = null;
            renderCancelled(draft.getProposalId(), "Nothing was executed.");
            if (currentAttention != null) {
                renderResponseOptions(currentAttention);
                legacy.responseOptions.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (activePhoneProposal != null) {
            cancelPhoneProposal();
            return;
        }
        if (currentProposalId == null || surface.actionRequestListener == null) {
            legacy.responseStatus.setText("No pending proposal to cancel");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            return;
        }
        legacy.confirmButton.setEnabled(false);
        legacy.cancelButton.setEnabled(false);
        legacy.responseStatus.setText("Cancellation requested");
        surface.actionRequestListener.onCancelRequested(currentProposalId);
    }

    public void confirmPhoneProposal() {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        if (phoneActionBusy || activePhoneProposal == null || activeProposalEvent == null) return;
        CoreActionClient.Proposal proposal = activePhoneProposal;
        StoredNotificationEvent event = activeProposalEvent;
        String latestReply = proposal.actionType() == ActionType.REPLY
                ? legacy.responseEditor.getText().toString().trim() : "";
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(surface.activity, surface.runtime().getDeviceId());
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Local configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        legacy.confirmButton.setEnabled(false);
        legacy.cancelButton.setEnabled(false);
        ConnectionConfig finalConfig = config;
        surface.actionExecutor.execute(() -> {
            try {
                if (finalConfig.isStub()) {
                    if (localActionClient == null) {
                        localActionClient = new LocalCoreActionClient(surface.activity, finalConfig);
                    }
                    CoreActionClient.Proposal working = proposal;
                    if (proposal.actionType() == ActionType.REPLY
                            && !latestReply.equals(proposal.replyMessage())) {
                        CoreActionClient.Proposal revised =
                                localActionClient.reviseReply(proposal, event, latestReply);
                        surface.activity.runOnUiThread(() -> {
                            renderPhoneProposal(event, revised);
                            legacy.responseStatus.setText("Reply changed. Confirm the revised exact preview: "
                                    + revised.spokenPreview());
                        });
                        return;
                    }
                    final CoreActionClient.Proposal toConfirm = working;
                    surface.activity.runOnUiThread(() -> renderExecution(toConfirm.proposalId(),
                            "Executing on-device (stub)"));
                    CoreActionClient.Confirmation confirmation = localActionClient.confirm(toConfirm);
                    CoreActionClient.Receipt receipt = confirmation.receipt();
                    surface.activity.runOnUiThread(() -> finishPhoneConfirmation(toConfirm, event, receipt));
                    return;
                }

                CoreActionClient client = new CoreActionClient(
                        finalConfig, ConnectionConfigStore.loadUserActionToken(surface.activity));
                if (proposal.actionType() == ActionType.REPLY) {
                    if (!latestReply.equals(proposal.replyMessage())) {
                        CoreActionClient.Proposal revised =
                                client.reviseReply(proposal, event, latestReply);
                        surface.activity.runOnUiThread(() -> {
                            renderPhoneProposal(event, revised);
                            legacy.responseStatus.setText("Reply changed. Confirm the revised exact preview: "
                                    + revised.spokenPreview());
                        });
                        return;
                    }
                }
                CoreActionClient.Confirmation confirmation = client.confirm(proposal);
                surface.activity.runOnUiThread(() -> renderExecution(confirmation.commandId(),
                        "Waiting for Android receipt"));
                CoreActionClient.Receipt receipt = confirmation.receipt() == null
                        ? client.awaitReceipt(confirmation.commandId(), 65_000L)
                        : confirmation.receipt();
                surface.activity.runOnUiThread(() -> finishPhoneConfirmation(proposal, event, receipt));
            } catch (Exception failure) {
                surface.activity.runOnUiThread(() -> finishPhoneActionFailure(
                        "Action was not confirmed. It remains unsent."));
            }
        });
    }

    private void finishPhoneConfirmation(
            CoreActionClient.Proposal proposal,
            StoredNotificationEvent event,
            CoreActionClient.Receipt receipt) {
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        phoneActionBusy = false;
        if (receipt == null) {
            surface.sessionState = MainActivity.SessionState.FAILED;
            legacy.responseStatus.setText(
                    "No Android receipt arrived before expiry. Check the source app before retrying.");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            legacy.confirmButton.setVisibility(View.GONE);
            legacy.cancelButton.setVisibility(View.GONE);
            activePhoneProposal = null;
            activeProposalEvent = null;
            currentProposalId = null;
            if (currentAttention != null) {
                renderResponseOptions(currentAttention);
                legacy.responseOptions.setVisibility(View.VISIBLE);
            }
            return;
        }
        MainActivity.ReceiptState state = receiptState(receipt.status());
        if (state == MainActivity.ReceiptState.DISPATCHED) {
            handledAttentionKeys.add(ChatListPresenter.attentionKey(event));
            hiddenEventUntil.put(event.getEventId(), System.currentTimeMillis() + 30_000L);
            if (proposal.actionType() == ActionType.REPLY) legacy.responseEditor.setText("");
            TextureNotificationListenerService.requestHealthReconciliation(surface.activity);
        }
        activePhoneProposal = null;
        activeProposalEvent = null;
        renderReceipt(receipt.receiptId(), state, receipt.message());
        surface.voiceController.speakAndListen(state == MainActivity.ReceiptState.DISPATCHED
                ? "Done. I moved to the next item. You can reply, snooze, dismiss, or ask about the conversation."
                : "That action was not dispatched. You can retry, change it, or choose another action.");
    }

    private void cancelPhoneProposal() {
        if (phoneActionBusy || activePhoneProposal == null) return;
        CoreActionClient.Proposal proposal = activePhoneProposal;
        ConnectionConfig config;
        try {
            config = ConnectionConfigStore.load(surface.activity, surface.runtime().getDeviceId());
        } catch (RuntimeException missing) {
            finishPhoneActionFailure("Local configuration is unavailable.");
            return;
        }
        phoneActionBusy = true;
        surface.chats.legacy().confirmButton.setEnabled(false);
        surface.chats.legacy().cancelButton.setEnabled(false);
        surface.actionExecutor.execute(() -> {
            try {
                if (config.isStub()) {
                    if (localActionClient != null) localActionClient.cancel(proposal);
                } else {
                    new CoreActionClient(config, ConnectionConfigStore.loadUserActionToken(surface.activity))
                            .cancel(proposal);
                }
                surface.activity.runOnUiThread(() -> {
                    ChatListController.LegacyControls legacy = surface.chats.legacy();
                    phoneActionBusy = false;
                    activePhoneProposal = null;
                    activeProposalEvent = null;
                    renderCancelled(proposal.proposalId(), "Nothing was executed.");
                    if (currentAttention != null) {
                        renderResponseOptions(currentAttention);
                        legacy.responseOptions.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception failure) {
                surface.activity.runOnUiThread(() -> finishPhoneActionFailure(
                        "Could not cancel the proposal yet."));
            }
        });
    }

    public static EyeOfHorusView.State eyeStateForSurface(
            MainActivity.ConnectionState connectionState,
            MainActivity.SessionState sessionState) {
        if (connectionState == MainActivity.ConnectionState.DISCONNECTED
                || connectionState == MainActivity.ConnectionState.STALE) {
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

    public static EyeOfHorusView.State eyeStateForCue(TextureCue cue) {
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
}
