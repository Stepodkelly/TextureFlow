package com.textureflow.intelligence.shield;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds the §8.1 shielded context: untrusted wraps, ≤5 events, relative age,
 * no keys/phones/absolute timestamps, other people's messages stripped.
 */
public final class ContextShield {
    private final ContextLimits limits;
    private final TokenBudget budget;
    private final TokenEstimator estimator;

    public ContextShield() {
        this(ContextLimits.DEFAULT, TokenBudget.DEFAULT, CharHeuristicTokenEstimator.INSTANCE);
    }

    public ContextShield(ContextLimits limits, TokenBudget budget, TokenEstimator estimator) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
    }

    public ShieldedContext shield(ShieldRequest request) {
        Objects.requireNonNull(request, "request");
        List<ShieldEvent> matching = filterMatching(request);
        boolean injectionFlagged = false;
        for (ShieldEvent event : matching) {
            if (InjectionDetector.containsUntrustedInstruction(event.getBody())) {
                injectionFlagged = true;
                break;
            }
        }

        matching.sort(Comparator.comparingLong(ShieldEvent::getPostedAtMillis).reversed());
        boolean overflow = matching.size() > limits.maxEvents();
        if (matching.size() > limits.maxEvents()) {
            matching = new ArrayList<>(matching.subList(0, limits.maxEvents()));
        }

        List<PreparedEvent> prepared = applyCharacterBudget(matching, request.getNowMillis());
        overflow = overflow || prepared.stream().anyMatch(item -> item.charTruncated);

        String displayName = TextCleaner.cleanText(request.getDisplayName());
        String relationship = TextCleaner.cleanText(request.getRelationship());
        String app = resolveAppLabel(request, matching);
        String instruction = TextCleaner.cleanText(request.getUserInstruction());

        String headerText = joinPresent(displayName, relationship, app);
        String fittedHeader = budget.fitHeaderPart(headerText, budget.headerTokens(), estimator);
        if (!fittedHeader.equals(headerText)) {
            overflow = true;
        }
        String fittedInstruction = budget.fitInstruction(instruction, estimator);
        if (!fittedInstruction.equals(instruction)) {
            overflow = true;
        }

        int headerTokens = estimator.estimateTokens(joinPresent(displayName, relationship, app));
        int instructionTokens = estimator.estimateTokens(fittedInstruction);
        int eventRoom = budget.eventTokenRoom(headerTokens, instructionTokens);

        TokenTrimResult trimmed = trimOldestFirst(prepared, eventRoom);
        overflow = overflow || trimmed.overflow;

        List<ShieldedEvent> events = new ArrayList<>();
        int keptEventTokens = 0;
        for (PreparedEvent item : trimmed.kept) {
            events.add(new ShieldedEvent(item.ageMinutes, item.wrapped));
            keptEventTokens += estimator.estimateTokens(item.wrapped);
        }

        int inputTokens = headerTokens + instructionTokens + keptEventTokens;
        TokenBudgetReport report = new TokenBudgetReport(inputTokens, trimmed.droppedTokens, overflow);
        return new ShieldedContext(
                new ShieldedPerson(displayName, relationship),
                app,
                events,
                fittedInstruction,
                report,
                overflow,
                injectionFlagged);
    }

    private static List<ShieldEvent> filterMatching(ShieldRequest request) {
        List<ShieldEvent> matching = new ArrayList<>();
        for (ShieldEvent event : request.getEvents()) {
            if (event == null || event.isRemoved()) {
                continue;
            }
            if (!event.belongsTo(request.getPersonId())) {
                continue;
            }
            matching.add(event);
        }
        return matching;
    }

    private List<PreparedEvent> applyCharacterBudget(List<ShieldEvent> newestFirst, long nowMillis) {
        int remaining = limits.maxTotalBodyCharacters();
        List<PreparedEvent> prepared = new ArrayList<>();
        for (ShieldEvent event : newestFirst) {
            if (remaining <= 0) {
                break;
            }
            String cleaned = TextCleaner.stripPhoneNumbers(
                    TextCleaner.cleanText(emptyBodyFallback(event.getBody())));
            int bodyLimit = Math.min(limits.maxBodyCharactersPerEvent(), remaining);
            String limited = TextCleaner.truncateCodePoints(cleaned, bodyLimit);
            boolean charTruncated = TextCleaner.codePointLength(limited) < TextCleaner.codePointLength(cleaned);
            remaining -= TextCleaner.codePointLength(limited);
            prepared.add(new PreparedEvent(
                    ageMinutes(event.getPostedAtMillis(), nowMillis),
                    TextCleaner.wrapUntrusted(limited),
                    charTruncated));
        }
        return prepared;
    }

    private TokenTrimResult trimOldestFirst(List<PreparedEvent> newestFirst, int eventRoom) {
        List<PreparedEvent> working = new ArrayList<>(newestFirst);
        int droppedTokens = 0;
        boolean overflow = false;
        int total = 0;
        for (PreparedEvent item : working) {
            total += estimator.estimateTokens(item.wrapped);
        }
        int excess = Math.max(0, total - eventRoom);
        if (excess > 0) {
            overflow = true;
        }
        for (int i = working.size() - 1; i >= 0 && excess > 0; i--) {
            PreparedEvent item = working.get(i);
            int tokens = estimator.estimateTokens(item.wrapped);
            if (tokens <= excess) {
                droppedTokens += tokens;
                excess -= tokens;
                working.remove(i);
                continue;
            }
            String inner = TextCleaner.unwrapUntrusted(item.wrapped);
            int keepTokens = Math.max(0, tokens - excess);
            String keptInner = TokenBudget.truncateToTokens(inner, keepTokens, estimator);
            String rewritten = TextCleaner.wrapUntrusted(keptInner);
            int after = estimator.estimateTokens(rewritten);
            droppedTokens += Math.max(0, tokens - after);
            excess = 0;
            working.set(i, new PreparedEvent(item.ageMinutes, rewritten, true));
        }
        return new TokenTrimResult(working, droppedTokens, overflow);
    }

    private String resolveAppLabel(ShieldRequest request, List<ShieldEvent> matching) {
        String requested = TextCleaner.cleanText(request.getAppLabel());
        if (!requested.isEmpty()) {
            return requested;
        }
        if (!matching.isEmpty()) {
            return TextCleaner.cleanText(matching.get(0).getAppLabel());
        }
        return "";
    }

    static int ageMinutes(long postedAtMillis, long nowMillis) {
        long delta = nowMillis - postedAtMillis;
        if (delta <= 0L) {
            return 0;
        }
        long minutes = delta / 60_000L;
        return minutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) minutes;
    }

    private static String emptyBodyFallback(String body) {
        String cleaned = TextCleaner.cleanText(body);
        return cleaned.isEmpty() ? "No message text available." : cleaned;
    }

    private static String joinPresent(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private static final class PreparedEvent {
        final int ageMinutes;
        final String wrapped;
        final boolean charTruncated;

        PreparedEvent(int ageMinutes, String wrapped, boolean charTruncated) {
            this.ageMinutes = ageMinutes;
            this.wrapped = wrapped;
            this.charTruncated = charTruncated;
        }
    }

    private static final class TokenTrimResult {
        final List<PreparedEvent> kept;
        final int droppedTokens;
        final boolean overflow;

        TokenTrimResult(List<PreparedEvent> kept, int droppedTokens, boolean overflow) {
            this.kept = kept;
            this.droppedTokens = droppedTokens;
            this.overflow = overflow;
        }
    }
}
