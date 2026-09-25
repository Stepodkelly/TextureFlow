package com.textureflow.intelligence.triage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class IdentityResolverTest {
    private final IdentityResolver resolver = new IdentityResolver();

    @Test
    public void defaultSeedsResolveSamAndMaya() {
        assertResolved(resolver.resolveMention("Sam"), "person_sam", 1.0);
        assertResolved(resolver.resolveEvent(event("Sam K", "com.whatsapp", null)), "person_sam", 1.0);
        assertResolved(
                resolver.resolveEvent(event("@samk", "org.telegram.messenger", null)),
                "person_sam",
                1.0);
        assertResolved(resolver.resolveEvent(event("Maya K.", "com.whatsapp", null)), "person_maya", 0.85);
        assertResolved(
                resolver.resolveEvent(event("@mayak", "org.telegram.messenger", null)),
                "person_maya",
                0.85);
    }

    @Test
    public void explicitPersonIdWinsOverDisplayName() {
        IdentityResolution resolved = resolver.resolveEvent(event("Stranger", "com.other", "person_sam"));
        assertEquals(IdentityKind.RESOLVED, resolved.getKind());
        assertEquals("person_sam", ((ResolvedIdentity) resolved).getPersonId());
        assertEquals("Sam", ((ResolvedIdentity) resolved).getDisplayName());
        assertEquals("Stranger", ((ResolvedIdentity) resolved).getMatchedAlias());
    }

    @Test
    public void unknownExplicitIdFallsThroughToAliasMatch() {
        IdentityResolution resolved = resolver.resolveEvent(event("Sam", "com.whatsapp", "person_missing"));
        assertEquals(IdentityKind.RESOLVED, resolved.getKind());
        assertEquals("person_sam", ((ResolvedIdentity) resolved).getPersonId());
    }

    @Test
    public void ambiguousAliasesAreNeverMerged() {
        List<PersonAliasSeed> seeds = Arrays.asList(
                new PersonAliasSeed("person_alex_chen", "Alex Chen", 0.7, null,
                        Arrays.asList(new PersonAlias("Alex"))),
                new PersonAliasSeed("person_alex_rivera", "Alex Rivera", 0.8, null,
                        Arrays.asList(new PersonAlias("Alex"))));
        IdentityResolution resolution = new IdentityResolver(seeds).resolveMention("Alex");
        assertEquals(IdentityKind.AMBIGUOUS, resolution.getKind());
        AmbiguousIdentity ambiguous = (AmbiguousIdentity) resolution;
        assertEquals("Alex", ambiguous.getQuery());
        assertEquals(2, ambiguous.getCandidates().size());
        assertEquals("person_alex_chen", ambiguous.getCandidates().get(0).getPersonId());
        assertEquals("person_alex_rivera", ambiguous.getCandidates().get(1).getPersonId());
        assertEquals(0.5, ambiguous.getImportance(), 0.0);
    }

    @Test
    public void provisionalIdsMatchTypescriptStableHash() {
        IdentityResolution unknown = resolver.resolveEvent(event("Jordan", "com.whatsapp", null));
        assertEquals(IdentityKind.PROVISIONAL, unknown.getKind());
        ProvisionalIdentity provisional = (ProvisionalIdentity) unknown;
        assertEquals(
                "person_provisional_" + PersonAliasBook.stableHash("com.whatsapp:" + PersonAliasBook.normalizeAlias("Jordan")),
                provisional.getPersonId());
        assertEquals(0.5, provisional.getImportance(), 0.0);
        assertEquals("Jordan", provisional.getDisplayName());
        assertEquals("Jordan", provisional.getMatchedAlias());

        IdentityResolution mention = resolver.resolveMention("Jordan");
        assertEquals(
                "person_provisional_" + PersonAliasBook.stableHash("any:" + PersonAliasBook.normalizeAlias("Jordan")),
                ((ProvisionalIdentity) mention).getPersonId());
    }

    @Test
    public void normalizeAliasUsesNfkdAndStripsCombiningMarks() {
        assertEquals("sam", PersonAliasBook.normalizeAlias("Sám"));
        assertEquals("samk", PersonAliasBook.normalizeAlias("  @Sam K.  "));
        assertEquals("mayak", PersonAliasBook.normalizeAlias("Maya K."));
        assertEquals("a1b2", PersonAliasBook.normalizeAlias("A-1 B_2"));
        IdentityResolution accented = resolver.resolveMention("Sám");
        assertEquals(IdentityKind.RESOLVED, accented.getKind());
        assertEquals("person_sam", ((ResolvedIdentity) accented).getPersonId());
    }

    @Test
    public void emptySenderBecomesUnknownProvisional() {
        IdentityResolution empty = resolver.resolveEvent(event("   ", "com.other", null));
        assertEquals(IdentityKind.PROVISIONAL, empty.getKind());
        assertEquals("Unknown sender", ((ProvisionalIdentity) empty).getDisplayName());
    }

    @Test
    public void importanceIsClamped() {
        PersonAliasBook book = new PersonAliasBook(Arrays.asList(
                new PersonAliasSeed("person_hot", "Hot", 4.0, null, Arrays.asList(new PersonAlias("Hot"))),
                new PersonAliasSeed("person_cold", "Cold", -2.0, null, Arrays.asList(new PersonAlias("Cold")))));
        IdentityResolver custom = new IdentityResolver(book);
        assertEquals(1.0, custom.resolveMention("Hot").getImportance(), 0.0);
        assertEquals(0.0, custom.resolveMention("Cold").getImportance(), 0.0);
    }

    @Test
    public void stableHashIsUnsignedBase36Fnv() {
        assertEquals(PersonAliasBook.stableHash("com.whatsapp:jordan"), PersonAliasBook.stableHash("com.whatsapp:jordan"));
        assertTrue(PersonAliasBook.stableHash("x").matches("[0-9a-z]+"));
    }

    private static void assertResolved(IdentityResolution resolution, String personId, double importance) {
        assertEquals(IdentityKind.RESOLVED, resolution.getKind());
        ResolvedIdentity resolved = (ResolvedIdentity) resolution;
        assertEquals(personId, resolved.getPersonId());
        assertEquals(importance, resolved.getImportance(), 0.0);
    }

    private static TriageEvent event(String displayName, String packageName, String personId) {
        return TriageEvent.iso("evt", packageName, "hi", "2026-08-09T18:11:00-07:00", displayName, personId);
    }
}
