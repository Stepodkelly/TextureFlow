package com.textureflow.intelligence.evals;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.shield.InjectionDetector;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.PriorityFeatures;
import com.textureflow.intelligence.triage.PriorityPatterns;
import com.textureflow.intelligence.triage.PriorityResult;
import com.textureflow.intelligence.triage.ResolvedIdentity;
import com.textureflow.intelligence.triage.TriageEvent;

import java.util.regex.Pattern;

/** Wraps {@link DeterministicTriage} for the shared v2 eval harness. */
public final class DeterministicTriageTarget implements TriageEvalTarget {
    private static final Pattern RESPONSE_LIKELY = Pattern.compile(
            "\\?|\\b(?:please|can you|could you|need|let me know|locked|waiting)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public AttentionLevel assessLevel(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        return assess(body, packageName, sender, personImportance, ageMinutes)
                .getAssessment()
                .getLevel();
    }

    @Override
    public boolean requiresResponse(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        PriorityFeatures features = assess(body, packageName, sender, personImportance, ageMinutes)
                .getFeatures();
        if (features.isMaliciousInstruction() || features.isPromotional()) {
            return false;
        }
        String text = body == null ? "" : body;
        return RESPONSE_LIKELY.matcher(text).find();
    }

    @Override
    public boolean isInjection(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        return InjectionDetector.containsUntrustedInstruction(body)
                || PriorityPatterns.containsUntrustedInstruction(body);
    }

    private static PriorityResult assess(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        long now = 1_700_000_000_000L;
        long posted = now - Math.max(0, ageMinutes) * 60_000L;
        String name = sender == null || sender.isEmpty() ? "Unknown" : sender;
        TriageEvent event = TriageEvent.atMillis(
                "eval",
                packageName == null ? "" : packageName,
                body,
                posted,
                name,
                null);
        ResolvedIdentity identity = new ResolvedIdentity(
                "eval_person",
                name,
                personImportance,
                null,
                name);
        return DeterministicTriage.assess(event, identity, now);
    }
}
