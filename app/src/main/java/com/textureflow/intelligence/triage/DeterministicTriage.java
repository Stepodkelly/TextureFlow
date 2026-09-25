package com.textureflow.intelligence.triage;

import com.textureflow.intelligence.api.AttentionLevel;

import java.util.Objects;

/**
 * Port of {@code assessPriority}, {@code mergeModelPriority}, {@code levelForScore},
 * and {@code roundScore} from {@code intelligence/src/priority.ts}.
 */
public final class DeterministicTriage {
    private DeterministicTriage() {}

    public static PriorityResult assess(
            TriageEvent event,
            IdentityResolution identity,
            long nowMillis) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(identity, "identity");

        String body = event.getBody();
        boolean maliciousInstruction = PriorityPatterns.containsUntrustedInstruction(body);
        boolean promotional = PriorityPatterns.isPromotional(body);
        double personImportance = identity.getKind() == IdentityKind.AMBIGUOUS
                ? 0.5
                : identity.getImportance();
        double urgencySignals = maliciousInstruction ? 0 : urgencyStrength(body);
        double directRequest = maliciousInstruction ? 0 : requestStrength(body);
        double recency = recencyStrength(event.resolvedPostedAtMillis(), nowMillis);
        double sourceRelevance = sourceStrength(event.getPackageName());

        PriorityFeatures features = new PriorityFeatures(
                personImportance,
                urgencySignals,
                directRequest,
                recency,
                sourceRelevance,
                promotional,
                maliciousInstruction);

        double score =
                0.3 * personImportance
                + 0.25 * urgencySignals
                + 0.2 * directRequest
                + 0.15 * recency
                + 0.1 * sourceRelevance;

        if (promotional && urgencySignals == 0 && directRequest == 0) {
            score *= 0.25;
        }
        if (maliciousInstruction) {
            score = Math.min(score, 0.49);
        }

        score = roundScore(score);
        PriorityAssessment assessment = new PriorityAssessment(
                score,
                levelForScore(score),
                priorityReason(features));
        return new PriorityResult(assessment, features);
    }

    /**
     * Merge only when deterministic score is in {@code [0.35, 0.75)}.
     * Model suggestion is bounded to ±0.1, then {@code (det*2 + bounded) / 3}.
     */
    public static PriorityAssessment mergeModelPriority(
            PriorityAssessment deterministic,
            ModelPriorityHint model) {
        Objects.requireNonNull(deterministic, "deterministic");
        Objects.requireNonNull(model, "model");
        if (deterministic.getScore() < 0.35 || deterministic.getScore() >= 0.75) {
            return deterministic;
        }
        double boundedSuggestion = Math.max(
                deterministic.getScore() - 0.1,
                Math.min(deterministic.getScore() + 0.1, model.getPriorityScore()));
        double score = roundScore((deterministic.getScore() * 2 + boundedSuggestion) / 3);
        return new PriorityAssessment(
                score,
                levelForScore(score),
                deterministic.getReason() + " Model evidence: " + model.getPriorityReason());
    }

    public static AttentionLevel levelForScore(double score) {
        if (score >= 0.8) {
            return AttentionLevel.URGENT;
        }
        if (score >= 0.6) {
            return AttentionLevel.IMPORTANT;
        }
        if (score >= 0.35) {
            return AttentionLevel.NORMAL;
        }
        return AttentionLevel.LOW;
    }

    public static double roundScore(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 100) / 100.0;
    }

    static double urgencyStrength(String body) {
        if (!PriorityPatterns.URGENT.matcher(body).find()) {
            return 0;
        }
        if (PriorityPatterns.STRONG_URGENCY.matcher(body).find()) {
            return 1;
        }
        return 0.8;
    }

    static double requestStrength(String body) {
        if (PriorityPatterns.STRONG_REQUEST.matcher(body).find()) {
            return 1;
        }
        if (PriorityPatterns.REQUEST.matcher(body).find()) {
            return 0.75;
        }
        if (PriorityPatterns.ACCESS_REQUEST.matcher(body).find()) {
            return 0.8;
        }
        return 0;
    }

    static double recencyStrength(Long postedAtMillis, long nowMillis) {
        if (postedAtMillis == null) {
            return 0.3;
        }
        double ageMinutes = Math.max(0, (nowMillis - postedAtMillis) / 60_000.0);
        if (ageMinutes <= 15) {
            return 1;
        }
        if (ageMinutes <= 60) {
            return 0.85;
        }
        if (ageMinutes <= 240) {
            return 0.65;
        }
        if (ageMinutes <= 1_440) {
            return 0.4;
        }
        return 0.15;
    }

    static double sourceStrength(String packageName) {
        if (packageName != null && PriorityPatterns.SOURCE_DIRECT.matcher(packageName).find()) {
            return 0.9;
        }
        if (packageName != null && PriorityPatterns.SOURCE_WORK.matcher(packageName).find()) {
            return 0.7;
        }
        return 0.5;
    }

    static String priorityReason(PriorityFeatures features) {
        if (features.isMaliciousInstruction()) {
            return PriorityReasons.MALICIOUS;
        }
        if (features.isPromotional()) {
            return PriorityReasons.PROMOTIONAL;
        }
        if (features.getUrgencySignals() >= 0.8 && features.getDirectRequest() >= 0.75) {
            return PriorityReasons.URGENT_DIRECT_REQUEST;
        }
        if (features.getUrgencySignals() >= 0.8) {
            return PriorityReasons.URGENCY;
        }
        if (features.getDirectRequest() >= 0.75) {
            return PriorityReasons.DIRECT_REQUEST;
        }
        if (features.getPersonImportance() >= 0.8) {
            return PriorityReasons.IMPORTANT_CONTACT;
        }
        return PriorityReasons.DEFAULT;
    }
}
