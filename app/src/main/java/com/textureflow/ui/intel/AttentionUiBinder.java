package com.textureflow.ui.intel;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionEngine;
import com.textureflow.intelligence.api.AttentionListener;
import com.textureflow.intelligence.api.Callback;
import com.textureflow.intelligence.api.DraftQuery;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.ui.MainSurface;
import com.textureflow.ui.chats.ChatListPresenter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Subscribes the Moth Market surface to {@link AttentionEngine} when Stream G
 * attaches one. Assessments land on {@code ChatListController.setAssessments};
 * drafts and invalidations go to {@code ConversationController}.
 */
public final class AttentionUiBinder implements AttentionListener {
    private final MainSurface surface;
    private final Map<String, AttentionAssessment> assessments = new LinkedHashMap<>();
    private final Map<String, ProposalDraft> pendingDrafts = new LinkedHashMap<>();
    private AttentionEngine engine;
    private boolean listening;

    public AttentionUiBinder(MainSurface surface) {
        this.surface = surface;
    }

    public AttentionEngine engine() {
        return engine;
    }

    public Map<String, AttentionAssessment> assessments() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(assessments));
    }

    public Map<String, ProposalDraft> pendingDrafts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pendingDrafts));
    }

    public void setEngine(AttentionEngine next) {
        detach();
        engine = next;
        attach();
    }

    public void attachIfPresent(Object runtime) {
        if (engine != null) return;
        AttentionEngine found = findOnRuntime(runtime);
        if (found != null) setEngine(found);
    }

    public void attach() {
        if (engine == null || listening) return;
        engine.addListener(this);
        listening = true;
    }

    public void detach() {
        if (engine == null || !listening) return;
        try {
            Method remove = engine.getClass().getMethod("removeListener", AttentionListener.class);
            remove.invoke(engine, this);
        } catch (ReflectiveOperationException ignored) {
            // Wave 0 API has addListener only; G may add removeListener.
        }
        listening = false;
    }

    public void offerDraft(ProposalDraft draft) {
        if (draft == null) return;
        pendingDrafts.put(draft.getProposalId(), draft);
        surface.mainHandler.post(() -> {
            if (surface.conversation != null) {
                surface.conversation.offerProposalDraft(draft);
            }
        });
    }

    public void requestDraftFor(StoredNotificationEvent event) {
        if (engine == null || event == null) return;
        String suggested = ChatListPresenter.suggestReply(event);
        DraftQuery query = new DraftQuery(event.getEventId(), "", suggested, ReplyTone.NEUTRAL);
        engine.requestDraft(query, new Callback<ProposalDraft>() {
            @Override
            public void onResult(ProposalDraft value) {
                offerDraft(value);
            }

            @Override
            public void onError(Exception error) {
                // Deterministic suggested reply stays in the editor.
            }
        });
    }

    @Override
    public void onAssessment(AttentionAssessment assessment) {
        if (assessment == null) return;
        surface.mainHandler.post(() -> {
            if (!assessment.getPersonId().isEmpty()) {
                assessments.put(assessment.getPersonId(), assessment);
            }
            if (!assessment.getLeadEventId().isEmpty()) {
                assessments.put(assessment.getLeadEventId(), assessment);
            }
            if (surface.chats != null) {
                surface.chats.setAssessments(assessments);
            }
            if (surface.voice != null) {
                surface.voice.onAssessmentsUpdated();
            }
        });
    }

    @Override
    public void onProposalInvalidated(String proposalId, String reason) {
        surface.mainHandler.post(() -> {
            if (proposalId != null) pendingDrafts.remove(proposalId);
            if (surface.conversation != null) {
                surface.conversation.onProposalInvalidated(proposalId, reason);
            }
        });
    }

    /**
     * Stream G may hang the engine on {@code NotificationRuntime} after this
     * branch lands. Look up common accessors without editing that class.
     */
    public static AttentionEngine findOnRuntime(Object runtime) {
        if (runtime == null) return null;
        String[] methods = {
                "attentionEngine", "getAttentionEngine", "engine", "getEngine"
        };
        for (String name : methods) {
            try {
                Method method = runtime.getClass().getMethod(name);
                Object value = method.invoke(runtime);
                if (value instanceof AttentionEngine) return (AttentionEngine) value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        String[] fields = {"attentionEngine", "engine"};
        for (String name : fields) {
            try {
                Field field = runtime.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(runtime);
                if (value instanceof AttentionEngine) return (AttentionEngine) value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
