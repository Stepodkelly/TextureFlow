package com.textureflow.intelligence.evals;

import com.textureflow.intelligence.api.AttentionLevel;

/**
 * Narrow seam so the eval runner can score any triage implementation.
 * Stream A’s {@code DeterministicTriage} should be wrapped to this after merge.
 */
public interface TriageEvalTarget {
    AttentionLevel assessLevel(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes);

    boolean requiresResponse(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes);

    boolean isInjection(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes);
}
