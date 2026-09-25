package com.textureflow.intelligence.triage;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Alias book + {@code normalizeAlias} / {@code stableHash} from
 * {@code intelligence/src/aliases.ts}.
 */
public final class PersonAliasBook {
    public static final List<PersonAliasSeed> DEFAULT_PERSON_ALIASES = Collections.unmodifiableList(Arrays.asList(
            new PersonAliasSeed(
                    "person_sam",
                    "Sam",
                    1,
                    "close contact",
                    Arrays.asList(
                            new PersonAlias("Sam"),
                            new PersonAlias("Sam K", "com.whatsapp"),
                            new PersonAlias("@samk", "org.telegram.messenger"))),
            new PersonAliasSeed(
                    "person_maya",
                    "Maya",
                    0.85,
                    "important contact",
                    Arrays.asList(
                            new PersonAlias("Maya"),
                            new PersonAlias("Maya K.", "com.whatsapp"),
                            new PersonAlias("@mayak", "org.telegram.messenger")))));

    private final List<PersonAliasSeed> seeds;

    public PersonAliasBook() {
        this(DEFAULT_PERSON_ALIASES);
    }

    public PersonAliasBook(List<PersonAliasSeed> seeds) {
        List<PersonAliasSeed> copied = new ArrayList<>();
        for (PersonAliasSeed seed : seeds) {
            copied.add(new PersonAliasSeed(
                    seed.getPersonId(),
                    seed.getDisplayName(),
                    clamp(seed.getImportance()),
                    seed.getRelationship(),
                    seed.getAliases()));
        }
        this.seeds = Collections.unmodifiableList(copied);
    }

    public static PersonAliasBook defaults() {
        return new PersonAliasBook();
    }

    public List<PersonAliasSeed> getSeeds() {
        return seeds;
    }

    /**
     * NFKD, strip combining marks U+0300–U+036F, trim, lower, strip leading {@code @},
     * then drop non {@code [a-z0-9+]}.
     */
    public static String normalizeAlias(String value) {
        if (value == null) {
            value = "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
        normalized = normalized.replaceAll("[\\u0300-\\u036f]", "");
        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.replaceAll("[^a-z0-9+]+", "");
    }

    /**
     * FNV-ish 32-bit hash matching {@code aliases.ts}: offset {@code 0x811c9dc5},
     * prime {@code 0x01000193}, {@code Math.imul}, unsigned {@code toString(36)}.
     */
    public static String stableHash(String value) {
        int hash = 0x811c9dc5;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x01000193;
        }
        long unsigned = hash & 0xffffffffL;
        return Long.toString(unsigned, 36);
    }

    public static String provisionalPersonId(String packageName, String normalized) {
        String pkg = packageName == null ? "any" : packageName;
        return "person_provisional_" + stableHash(pkg + ":" + normalized);
    }

    static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
