package com.textureflow.intelligence.triage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Port of {@code PersonAliasResolver} from {@code intelligence/src/aliases.ts}. */
public final class IdentityResolver {
    private final PersonAliasBook book;

    public IdentityResolver() {
        this(PersonAliasBook.defaults());
    }

    public IdentityResolver(PersonAliasBook book) {
        this.book = Objects.requireNonNull(book, "book");
    }

    public IdentityResolver(List<PersonAliasSeed> seeds) {
        this(new PersonAliasBook(seeds));
    }

    public PersonAliasBook getBook() {
        return book;
    }

    public IdentityResolution resolveEvent(TriageEvent event) {
        Objects.requireNonNull(event, "event");
        String personId = event.getSenderPersonId();
        if (personId != null) {
            List<PersonAliasSeed> explicit = new ArrayList<>();
            for (PersonAliasSeed seed : book.getSeeds()) {
                if (personId.equals(seed.getPersonId())) {
                    explicit.add(seed);
                }
            }
            if (explicit.size() == 1) {
                return resolved(explicit.get(0), event.getSenderDisplayName());
            }
        }
        return resolve(event.getSenderDisplayName(), event.getPackageName());
    }

    public IdentityResolution resolveMention(String value) {
        return resolve(value, null);
    }

    private IdentityResolution resolve(String value, String packageName) {
        if (value == null) {
            value = "";
        }
        String normalized = PersonAliasBook.normalizeAlias(value);
        List<PersonAliasSeed> appSpecific = matchingSeeds(normalized, packageName, true);
        List<PersonAliasSeed> matches = appSpecific.isEmpty()
                ? matchingSeeds(normalized, packageName, false)
                : appSpecific;

        if (matches.size() == 1) {
            return resolved(matches.get(0), value);
        }
        if (matches.size() > 1) {
            List<AmbiguousCandidate> candidates = new ArrayList<>();
            for (PersonAliasSeed seed : matches) {
                candidates.add(new AmbiguousCandidate(seed.getPersonId(), seed.getDisplayName()));
            }
            candidates.sort(Comparator.comparing(AmbiguousCandidate::getPersonId));
            return new AmbiguousIdentity(value, candidates);
        }

        String trimmed = value.trim();
        String displayName = trimmed.isEmpty() ? "Unknown sender" : trimmed;
        return new ProvisionalIdentity(
                PersonAliasBook.provisionalPersonId(packageName, normalized),
                displayName,
                0.5,
                value);
    }

    private List<PersonAliasSeed> matchingSeeds(
            String normalized,
            String packageName,
            boolean appSpecificOnly) {
        List<PersonAliasSeed> matches = new ArrayList<>();
        for (PersonAliasSeed seed : book.getSeeds()) {
            if (PersonAliasBook.normalizeAlias(seed.getDisplayName()).equals(normalized)
                    && !appSpecificOnly) {
                matches.add(seed);
                continue;
            }
            boolean aliasMatch = false;
            for (PersonAlias alias : seed.getAliases()) {
                if (!PersonAliasBook.normalizeAlias(alias.getValue()).equals(normalized)) {
                    continue;
                }
                if (appSpecificOnly) {
                    if (Objects.equals(alias.getPackageName(), packageName)) {
                        aliasMatch = true;
                        break;
                    }
                } else if (alias.getPackageName() == null || packageName == null) {
                    aliasMatch = true;
                    break;
                }
            }
            if (aliasMatch) {
                matches.add(seed);
            }
        }
        return matches;
    }

    private static ResolvedIdentity resolved(PersonAliasSeed seed, String matchedAlias) {
        return new ResolvedIdentity(
                seed.getPersonId(),
                seed.getDisplayName(),
                seed.getImportance(),
                seed.getRelationship(),
                matchedAlias);
    }
}
