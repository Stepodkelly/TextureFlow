package com.textureflow.intelligence.triage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.textureflow.intelligence.api.AttentionLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class GoldenTriageParityTest {
    @Test
    public void javaAssessMatchesTypescriptGoldenScores() throws Exception {
        JSONObject root = loadGolden();
        JSONArray cases = root.getJSONArray("cases");
        assertTrue("expected golden triage cases", cases.length() > 0);
        List<String> mismatches = new ArrayList<>();
        for (int index = 0; index < cases.length(); index++) {
            JSONObject fixture = cases.getJSONObject(index);
            String id = fixture.getString("id");
            JSONObject eventJson = fixture.getJSONObject("event");
            JSONObject identityJson = fixture.getJSONObject("identity");
            JSONObject expected = fixture.getJSONObject("expected");
            long nowMillis = fixture.getLong("nowMillis");

            TriageEvent event = eventFrom(eventJson);
            IdentityResolution identity = identityFrom(identityJson);
            PriorityAssessment actual = DeterministicTriage.assess(event, identity, nowMillis).getAssessment();

            if (actual.getScore() != expected.getDouble("score")
                    || actual.getLevel() != AttentionLevel.valueOf(expected.getString("level"))
                    || !actual.getReason().equals(expected.getString("reason"))) {
                mismatches.add(id
                        + " expected score=" + expected.getDouble("score")
                        + " level=" + expected.getString("level")
                        + " reason=" + expected.getString("reason")
                        + " but got score=" + actual.getScore()
                        + " level=" + actual.getLevel()
                        + " reason=" + actual.getReason());
            }
        }
        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " golden mismatches:\n" + String.join("\n", mismatches));
        }
    }

    @Test
    public void javaMergeMatchesTypescriptGoldenMerges() throws Exception {
        JSONObject root = loadGolden();
        JSONArray cases = root.getJSONArray("mergeCases");
        assertTrue("expected merge golden cases", cases.length() > 0);
        for (int index = 0; index < cases.length(); index++) {
            JSONObject fixture = cases.getJSONObject(index);
            JSONObject det = fixture.getJSONObject("deterministic");
            JSONObject model = fixture.getJSONObject("model");
            JSONObject expected = fixture.getJSONObject("expected");
            PriorityAssessment actual = DeterministicTriage.mergeModelPriority(
                    new PriorityAssessment(
                            det.getDouble("score"),
                            AttentionLevel.valueOf(det.getString("level")),
                            det.getString("reason")),
                    new ModelPriorityHint(model.getDouble("priorityScore"), model.getString("priorityReason")));
            assertEquals(fixture.getString("id"), expected.getDouble("score"), actual.getScore(), 0.0);
            assertEquals(fixture.getString("id"), expected.getString("level"), actual.getLevel().name());
            assertEquals(fixture.getString("id"), expected.getString("reason"), actual.getReason());
        }
    }

    @Test
    public void javaIdentityMatchesTypescriptGoldenIdentities() throws Exception {
        JSONObject root = loadGolden();
        JSONArray cases = root.getJSONArray("identityCases");
        assertTrue("expected identity golden cases", cases.length() > 0);
        for (int index = 0; index < cases.length(); index++) {
            JSONObject fixture = cases.getJSONObject(index);
            JSONObject expected = fixture.getJSONObject("expected");
            IdentityResolver resolver = resolverFor(fixture);
            IdentityResolution actual;
            if (fixture.has("event")) {
                actual = resolver.resolveEvent(eventFrom(fixture.getJSONObject("event")));
            } else {
                actual = resolver.resolveMention(fixture.getString("query"));
            }
            assertEquals(fixture.getString("id"), expected.getString("kind"), actual.getKind().name());
            if ("AMBIGUOUS".equals(expected.getString("kind"))) {
                AmbiguousIdentity ambiguous = (AmbiguousIdentity) actual;
                assertEquals(fixture.getString("id"), expected.getString("query"), ambiguous.getQuery());
                JSONArray candidates = expected.getJSONArray("candidates");
                assertEquals(fixture.getString("id"), candidates.length(), ambiguous.getCandidates().size());
                for (int candidateIndex = 0; candidateIndex < candidates.length(); candidateIndex++) {
                    JSONObject candidate = candidates.getJSONObject(candidateIndex);
                    assertEquals(
                            fixture.getString("id"),
                            candidate.getString("personId"),
                            ambiguous.getCandidates().get(candidateIndex).getPersonId());
                }
            } else if ("RESOLVED".equals(expected.getString("kind"))) {
                ResolvedIdentity resolved = (ResolvedIdentity) actual;
                assertEquals(fixture.getString("id"), expected.getString("personId"), resolved.getPersonId());
                assertEquals(fixture.getString("id"), expected.getDouble("importance"), resolved.getImportance(), 0.0);
            } else {
                ProvisionalIdentity provisional = (ProvisionalIdentity) actual;
                assertEquals(fixture.getString("id"), expected.getString("personId"), provisional.getPersonId());
                assertEquals(fixture.getString("id"), expected.getString("displayName"), provisional.getDisplayName());
                assertEquals(fixture.getString("id"), 0.5, provisional.getImportance(), 0.0);
            }
        }
    }

    private static IdentityResolver resolverFor(JSONObject fixture) throws JSONException {
        if (!fixture.has("seeds") || fixture.isNull("seeds") || "defaults".equals(fixture.opt("seeds"))) {
            return new IdentityResolver();
        }
        JSONArray seedsJson = fixture.getJSONArray("seeds");
        List<PersonAliasSeed> seeds = new ArrayList<>();
        for (int index = 0; index < seedsJson.length(); index++) {
            JSONObject seed = seedsJson.getJSONObject(index);
            JSONArray aliasesJson = seed.getJSONArray("aliases");
            List<PersonAlias> aliases = new ArrayList<>();
            for (int aliasIndex = 0; aliasIndex < aliasesJson.length(); aliasIndex++) {
                JSONObject alias = aliasesJson.getJSONObject(aliasIndex);
                aliases.add(new PersonAlias(
                        alias.getString("value"),
                        alias.optString("packageName", null)));
            }
            seeds.add(new PersonAliasSeed(
                    seed.getString("personId"),
                    seed.getString("displayName"),
                    seed.getDouble("importance"),
                    seed.optString("relationship", null),
                    aliases));
        }
        return new IdentityResolver(seeds);
    }

    private static TriageEvent eventFrom(JSONObject eventJson) throws JSONException {
        Long millis = eventJson.has("postedAtMillis") && !eventJson.isNull("postedAtMillis")
                ? eventJson.getLong("postedAtMillis")
                : null;
        String personId = eventJson.optString("senderPersonId", "");
        if (personId.isEmpty()) {
            personId = null;
        }
        return new TriageEvent(
                eventJson.optString("eventId", ""),
                eventJson.optString("packageName", ""),
                eventJson.optString("body", ""),
                millis,
                eventJson.optString("postedAt", null),
                eventJson.optString("senderDisplayName", ""),
                personId);
    }

    private static IdentityResolution identityFrom(JSONObject identityJson) throws JSONException {
        String kind = identityJson.getString("kind");
        if ("AMBIGUOUS".equals(kind)) {
            JSONArray candidatesJson = identityJson.getJSONArray("candidates");
            List<AmbiguousCandidate> candidates = new ArrayList<>();
            for (int index = 0; index < candidatesJson.length(); index++) {
                JSONObject candidate = candidatesJson.getJSONObject(index);
                candidates.add(new AmbiguousCandidate(
                        candidate.getString("personId"),
                        candidate.getString("displayName")));
            }
            return new AmbiguousIdentity(identityJson.getString("query"), candidates);
        }
        if ("RESOLVED".equals(kind)) {
            return new ResolvedIdentity(
                    identityJson.getString("personId"),
                    identityJson.getString("displayName"),
                    identityJson.getDouble("importance"),
                    identityJson.has("relationship") && !identityJson.isNull("relationship")
                            ? identityJson.getString("relationship")
                            : null,
                    identityJson.getString("matchedAlias"));
        }
        return new ProvisionalIdentity(
                identityJson.getString("personId"),
                identityJson.getString("displayName"),
                identityJson.getDouble("importance"),
                identityJson.getString("matchedAlias"));
    }

    static JSONObject loadGolden() throws IOException, JSONException {
        Path path = goldenPath();
        assertTrue("missing golden file: " + path.toAbsolutePath(), Files.isRegularFile(path));
        return new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    static Path goldenPath() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromRoot = cwd.resolve("shared/evals/golden/triage.json");
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot;
        }
        return cwd.resolve("../shared/evals/golden/triage.json");
    }
}
