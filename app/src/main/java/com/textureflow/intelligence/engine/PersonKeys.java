package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.triage.AmbiguousIdentity;
import com.textureflow.intelligence.triage.IdentityResolution;
import com.textureflow.intelligence.triage.IdentityResolver;
import com.textureflow.intelligence.triage.PersonAliasBook;
import com.textureflow.intelligence.triage.ProvisionalIdentity;
import com.textureflow.intelligence.triage.ResolvedIdentity;
import com.textureflow.intelligence.triage.TriageEvent;

/** Stable person ids for {@code EventSignal} / {@link EventStore} grouping. */
public final class PersonKeys {
    private PersonKeys() {}

    public static String resolve(String senderName, String packageName) {
        IdentityResolution identity = new IdentityResolver().resolveEvent(
                new TriageEvent("", packageName, "", null, null, senderName, null));
        return resolve(identity);
    }

    public static String resolve(IdentityResolution identity) {
        if (identity instanceof ResolvedIdentity) {
            return ((ResolvedIdentity) identity).getPersonId();
        }
        if (identity instanceof ProvisionalIdentity) {
            return ((ProvisionalIdentity) identity).getPersonId();
        }
        if (identity instanceof AmbiguousIdentity) {
            AmbiguousIdentity ambiguous = (AmbiguousIdentity) identity;
            return "person_ambiguous_" + PersonAliasBook.stableHash(ambiguous.getQuery());
        }
        return "";
    }
}
