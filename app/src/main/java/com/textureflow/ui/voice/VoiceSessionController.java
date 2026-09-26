package com.textureflow.ui.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;

import com.textureflow.actions.ActionType;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.texture.TextureCue;
import com.textureflow.ui.ConversationalVoiceController;
import com.textureflow.ui.EyeOfHorusView;
import com.textureflow.ui.MainActivity;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.VoiceCommandParser;
import com.textureflow.ui.chats.ChatListController;
import com.textureflow.ui.chats.ChatListPresenter;
import com.textureflow.ui.chats.ConversationController;
import com.textureflow.ui.nav.Page;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoiceSessionController {
    private static final long NEEDS_ME_REFINE_MS = 8_000L;

    private final MainSurface surface;
    private String lastNeedsMeSpoken = "";
    private long needsMeOpenUntil;

    public VoiceSessionController(MainSurface surface) {
        this.surface = surface;
    }

    public void initialize() {
        surface.voiceController = new ConversationalVoiceController(surface.activity,
                new ConversationalVoiceController.Callback() {
                    @Override
                    public void onStateChanged(ConversationalVoiceController.State state) {
                        boolean speaking = state == ConversationalVoiceController.State.SPEAKING;
                        surface.textureEngine.setSpeechActive(speaking);
                        ChatListController.LegacyControls legacy = surface.chats == null
                                ? null : surface.chats.legacy();
                        if (legacy == null || legacy.talkButton == null) return;
                        switch (state) {
                            case LISTENING -> {
                                surface.sessionState = MainActivity.SessionState.LISTENING;
                                surface.settings.sessionStatus().setText("Listening");
                                legacy.talkButton.setText("Done");
                                legacy.eyeView.setState(EyeOfHorusView.State.LISTENING);
                                surface.textureEngine.emit(TextureCue.LISTENING_STARTED,
                                        "phone-voice", legacy.talkButton);
                            }
                            case PROCESSING -> {
                                surface.sessionState = MainActivity.SessionState.THINKING;
                                surface.settings.sessionStatus().setText("Understanding");
                                legacy.talkButton.setText("Talk");
                                legacy.eyeView.setState(EyeOfHorusView.State.THINKING);
                            }
                            case SPEAKING -> {
                                surface.sessionState = MainActivity.SessionState.PRESENTING;
                                surface.settings.sessionStatus().setText("Speaking · tap Talk to interrupt");
                                legacy.talkButton.setText("Interrupt");
                            }
                            case IDLE, STOPPED -> {
                                legacy.talkButton.setText("Talk");
                                if (surface.sessionState == MainActivity.SessionState.LISTENING
                                        || surface.sessionState == MainActivity.SessionState.THINKING) {
                                    surface.sessionState = MainActivity.SessionState.IDLE;
                                }
                                legacy.eyeView.setState(ConversationController.eyeStateForSurface(
                                        surface.connectionState, surface.sessionState));
                            }
                            default -> legacy.talkButton.setText("Talk");
                        }
                    }

                    @Override
                    public void onPartialUtterance(String utterance) {
                        ChatListController.LegacyControls legacy = surface.chats.legacy();
                        legacy.responseStatus.setText("Listening…");
                        legacy.responseStatus.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFinalUtterance(String utterance) {
                        handleVoiceUtterance(utterance);
                    }

                    @Override
                    public void onFailure(ConversationalVoiceController.Failure failure) {
                        if (failure == ConversationalVoiceController.Failure.RECORD_AUDIO_PERMISSION_REQUIRED) {
                            ChatListController.LegacyControls legacy = surface.chats.legacy();
                            legacy.responseStatus.setText("Microphone permission is needed for conversation");
                            legacy.responseStatus.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    public void speakUrgentItem() {
        ConversationalVoiceController.State voiceState = surface.voiceController.getState();
        if (voiceState == ConversationalVoiceController.State.SPEAKING
                || voiceState == ConversationalVoiceController.State.LISTENING
                || voiceState == ConversationalVoiceController.State.PROCESSING) {
            startVoiceTurn(surface.chats.legacy().talkButton);
            return;
        }
        List<StoredNotificationEvent> queue = surface.conversation.attentionQueue(
                surface.runtime().notifications().getLiveEvents());
        Map<String, AttentionAssessment> assessments =
                surface.chats == null ? Map.of() : surface.chats.assessments();
        String message = ChatListPresenter.needsMeSpoken(queue, assessments);
        lastNeedsMeSpoken = message;
        needsMeOpenUntil = System.currentTimeMillis() + NEEDS_ME_REFINE_MS;
        StoredNotificationEvent urgent = queue.isEmpty() ? null : queue.get(0);
        if (urgent != null) {
            surface.showPage(Page.CHATS);
            if (ChatListPresenter.effectiveLevel(urgent, assessments)
                    == AttentionLevel.URGENT) {
                surface.textureEngine.emit(TextureCue.ATTENTION_URGENT, urgent.getEventId(),
                        surface.chats.legacy().attentionPanel);
            }
        }
        if (surface.activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) surface.voiceController.speakAndListen(message);
        else surface.voiceController.speak(message);
    }

    /** Refine a recent "what needs me?" answer when a model assessment arrives. */
    public void onAssessmentsUpdated() {
        if (System.currentTimeMillis() > needsMeOpenUntil) return;
        List<StoredNotificationEvent> queue = surface.conversation.attentionQueue(
                surface.runtime().notifications().getLiveEvents());
        Map<String, AttentionAssessment> assessments =
                surface.chats == null ? Map.of() : surface.chats.assessments();
        boolean modelArrived = false;
        for (AttentionAssessment assessment : assessments.values()) {
            if (ChatListPresenter.isOnDeviceModel(assessment)) {
                modelArrived = true;
                break;
            }
        }
        if (!modelArrived) return;
        String message = ChatListPresenter.needsMeSpoken(queue, assessments);
        if (message.equals(lastNeedsMeSpoken)) return;
        lastNeedsMeSpoken = message;
        surface.voiceController.speak(message);
    }

    public void startVoiceTurn(View source) {
        if (surface.activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            surface.activity.requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MainActivity.AUDIO_PERMISSION_REQUEST);
            return;
        }
        surface.voiceController.tapToListenOrInterrupt();
        surface.textureEngine.playGlassTouch(
                source == null ? surface.chats.legacy().responsePanel : source);
    }

    public void handleVoiceUtterance(String utterance) {
        ConversationController conversation = surface.conversation;
        ChatListController.LegacyControls legacy = surface.chats.legacy();
        String spoken = utterance == null ? "" : utterance.trim();
        if (spoken.isEmpty()) return;
        String normalized = spoken.toLowerCase(Locale.US)
                .replaceAll("[\\s.!?]+$", "").trim();

        String dictatedReply = VoiceCommandParser.replyDraft(spoken, normalized);
        if (dictatedReply != null && conversation.currentAttention() != null) {
            if (!conversation.currentAttention().hasCapability("REPLY")) {
                surface.voiceController.speakAndListen(
                        "This notification cannot accept a reply. Say snooze this, done, or next.");
                return;
            }
            legacy.responseEditor.setVisibility(View.VISIBLE);
            legacy.responseEditor.setText(dictatedReply);
            legacy.responseEditor.setSelection(legacy.responseEditor.length());
            legacy.responseTitle.setText(
                    conversation.activePhoneProposal() == null ? "Proposed reply" : "Revised reply");
            legacy.responseStatus.setText("Preparing the exact reply preview");
            legacy.responseStatus.setVisibility(View.VISIBLE);
            if (conversation.activePhoneProposal() == null) conversation.selectResponseAction("Send");
            else conversation.confirmPhoneProposal();
            return;
        }

        if (conversation.activePhoneProposal() != null && (normalized.equals("confirm")
                || normalized.equals("yes") || normalized.equals("go ahead")
                || normalized.equals("do it") || normalized.equals("send")
                || normalized.equals("send it") || normalized.equals("send that")
                || normalized.equals("send now") || normalized.equals("yes send")
                || normalized.equals("confirm and send")
                || normalized.equals("review changes"))) {
            conversation.requestConfirmation(legacy.confirmButton);
            return;
        }
        if (conversation.activePhoneProposal() != null && (normalized.equals("cancel")
                || normalized.equals("never mind") || normalized.equals("stop"))) {
            conversation.requestCancellation(legacy.cancelButton);
            return;
        }
        if (conversation.activePhoneProposal() != null
                && conversation.activePhoneProposal().actionType() == ActionType.REPLY
                && (normalized.startsWith("change it to ") || normalized.startsWith("make it "))) {
            int prefix = normalized.startsWith("change it to ") ? "change it to ".length()
                    : "make it ".length();
            String replacement = spoken.substring(Math.min(prefix, spoken.length())).trim();
            if (!replacement.isEmpty()) {
                legacy.responseEditor.setText(replacement);
                legacy.responseEditor.setSelection(legacy.responseEditor.length());
                surface.voiceController.speakAndListen(
                        "I changed the proposed reply. Say send to confirm it, or keep editing.");
                return;
            }
        }
        if (conversation.activePhoneProposal() == null && conversation.currentAttention() != null
                && (normalized.equals("send") || normalized.equals("send it")
                || normalized.equals("send that") || normalized.equals("send now"))) {
            conversation.selectResponseAction("Send");
            return;
        }
        if (conversation.activePhoneProposal() == null && conversation.currentAttention() != null
                && (normalized.equals("later") || normalized.equals("snooze")
                || normalized.equals("snooze this")
                || normalized.startsWith("remind me later"))) {
            conversation.selectResponseAction("Later");
            return;
        }
        if (conversation.activePhoneProposal() == null && conversation.currentAttention() != null
                && (normalized.equals("done") || normalized.equals("dismiss")
                || normalized.equals("dismiss this") || normalized.equals("next"))) {
            conversation.selectResponseAction("Done");
            return;
        }
        if (ChatListPresenter.looksLikeNeedsMe(normalized)) {
            speakUrgentItem();
            return;
        }
        if (ChatListPresenter.looksLikeHistoryQuestion(normalized)) {
            surface.voiceController.speakAndListen(answerFromLocalHistory(spoken));
            return;
        }
        if (conversation.currentAttention() != null) {
            surface.voiceController.speakAndListen(
                    "I did not catch the action. Say reply with your message, snooze this, done, or ask about the conversation.");
        } else {
            surface.voiceController.speakAndListen("You are all caught up. Ask me about a recent person or message.");
        }
    }

    private String answerFromLocalHistory(String question) {
        List<StoredNotificationEvent> recent =
                surface.runtime().notifications().getRecentEvents(100);
        StoredNotificationEvent match = ChatListPresenter.matchHistory(question, recent);
        if (match == null) {
            return "I do not have a matching notification snapshot for that person yet.";
        }
        return "The recent notification history I have from "
                + ChatListPresenter.emptyFallback(match.getSenderName(), "that person") + " says: "
                + ChatListPresenter.emptyFallback(match.getBody(), "No message preview was retained.");
    }
}
