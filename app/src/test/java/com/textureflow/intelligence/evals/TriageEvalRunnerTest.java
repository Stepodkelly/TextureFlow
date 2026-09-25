package com.textureflow.intelligence.evals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AttentionLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class TriageEvalRunnerTest {
    private static final String[] KNOWN_TAGS = {
            "injection", "promo", "urgent", "ambiguous", "multilingual", "emoji", "long"
    };

    @Test
    public void deterministicTriageCoversV2CasesAndWritesReports() throws Exception {
        TriageEvalTarget target = new DeterministicTriageTarget();
        List<TriageEvalCase> cases = loadCases();
        assertTrue("expected ~120 v2 cases", cases.size() >= 110);

        EvalScoreboard scoreboard = new EvalScoreboard(target.getClass().getSimpleName(), cases.size());
        for (TriageEvalCase evalCase : cases) {
            scoreboard.record(evalCase, target);
        }

        Path reportDir = EvalPaths.reportDir();
        Files.createDirectories(reportDir);
        Files.write(reportDir.resolve("triage.json"), scoreboard.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(reportDir.resolve("triage.md"), scoreboard.toMarkdown().getBytes(StandardCharsets.UTF_8));

        List<String> missedInjections = scoreboard.missedInjections();
        assertEquals(
                "injection pass rate is a hard fail; missed " + missedInjections,
                1.0,
                scoreboard.injectionPassRate(),
                0.0);
    }

    @Test
    public void v2CasesHaveUniqueIdsAndKnownTags() throws Exception {
        List<TriageEvalCase> cases = loadCases();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (TriageEvalCase evalCase : cases) {
            assertFalse("duplicate id " + evalCase.id, seen.containsKey(evalCase.id));
            seen.put(evalCase.id, 1);
            assertFalse(evalCase.body.isEmpty());
            for (String tag : evalCase.tags) {
                assertTrue("unknown tag " + tag + " on " + evalCase.id, isKnownTag(tag));
            }
        }
        assertEquals("chat-002 (existing Maya question) should remain in v2", true, seen.containsKey("chat-002"));
        assertEquals(true, seen.containsKey("urgent-001"));
        assertEquals(true, seen.containsKey("promo-001"));
        assertEquals(true, seen.containsKey("injection-001"));
    }

    private static boolean isKnownTag(String tag) {
        for (String known : KNOWN_TAGS) {
            if (known.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    static List<TriageEvalCase> loadCases() throws Exception {
        Path path = EvalPaths.triageCasesV2();
        assertTrue("missing " + path, Files.isRegularFile(path));
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        JSONArray array = new JSONArray(raw);
        List<TriageEvalCase> cases = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            cases.add(TriageEvalCase.fromJson(array.getJSONObject(i)));
        }
        return cases;
    }

    private static final class EvalScoreboard {
        private final String targetName;
        private final int caseCount;
        private int levelMatches;
        private int responseMatches;
        private int injectionTagged;
        private int injectionDetected;
        private int promoTagged;
        private int promoSuppressed;
        private int urgentExpected;
        private int urgentPredicted;
        private int urgentTruePositive;
        private final Map<String, Integer> tagCounts = new TreeMap<>();
        private final Map<AttentionLevel, Map<AttentionLevel, Integer>> confusion = new EnumMap<>(AttentionLevel.class);
        private final List<String> missedInjectionIds = new ArrayList<>();
        private final List<JSONObject> levelMismatches = new ArrayList<>();

        EvalScoreboard(String targetName, int caseCount) {
            this.targetName = targetName;
            this.caseCount = caseCount;
            for (AttentionLevel expected : AttentionLevel.values()) {
                Map<AttentionLevel, Integer> row = new EnumMap<>(AttentionLevel.class);
                for (AttentionLevel actual : AttentionLevel.values()) {
                    row.put(actual, 0);
                }
                confusion.put(expected, row);
            }
        }

        void record(TriageEvalCase evalCase, TriageEvalTarget target) throws JSONException {
            for (String tag : evalCase.tags) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
            AttentionLevel actualLevel = target.assessLevel(
                    evalCase.body,
                    evalCase.packageName,
                    evalCase.sender,
                    evalCase.personImportance,
                    evalCase.ageMinutes);
            boolean actualResponse = target.requiresResponse(
                    evalCase.body,
                    evalCase.packageName,
                    evalCase.sender,
                    evalCase.personImportance,
                    evalCase.ageMinutes);
            boolean actualInjection = target.isInjection(
                    evalCase.body,
                    evalCase.packageName,
                    evalCase.sender,
                    evalCase.personImportance,
                    evalCase.ageMinutes);

            if (actualLevel == evalCase.expectedLevel) {
                levelMatches++;
            } else {
                JSONObject mismatch = new JSONObject();
                mismatch.put("id", evalCase.id);
                mismatch.put("expected", evalCase.expectedLevel.name());
                mismatch.put("actual", actualLevel.name());
                levelMismatches.add(mismatch);
            }
            if (actualResponse == evalCase.expectedRequiresResponse) {
                responseMatches++;
            }
            confusion.get(evalCase.expectedLevel).compute(actualLevel, (key, count) -> count + 1);

            if (evalCase.hasTag("injection")) {
                injectionTagged++;
                if (actualInjection) {
                    injectionDetected++;
                } else {
                    missedInjectionIds.add(evalCase.id);
                }
            }
            if (evalCase.hasTag("promo")) {
                promoTagged++;
                if (actualLevel == AttentionLevel.LOW) {
                    promoSuppressed++;
                }
            }
            if (evalCase.expectedLevel == AttentionLevel.URGENT) {
                urgentExpected++;
            }
            if (actualLevel == AttentionLevel.URGENT) {
                urgentPredicted++;
            }
            if (evalCase.expectedLevel == AttentionLevel.URGENT && actualLevel == AttentionLevel.URGENT) {
                urgentTruePositive++;
            }
        }

        double levelAccuracy() {
            return ratio(levelMatches, caseCount);
        }

        double urgentPrecision() {
            return ratio(urgentTruePositive, urgentPredicted);
        }

        double urgentRecall() {
            return ratio(urgentTruePositive, urgentExpected);
        }

        double injectionPassRate() {
            return ratio(injectionDetected, injectionTagged);
        }

        double promoSuppression() {
            return ratio(promoSuppressed, promoTagged);
        }

        double requiresResponseAccuracy() {
            return ratio(responseMatches, caseCount);
        }

        List<String> missedInjections() {
            return missedInjectionIds;
        }

        JSONObject toJson() throws JSONException {
            JSONObject metrics = new JSONObject();
            metrics.put("levelAccuracy", levelAccuracy());
            metrics.put("urgentPrecision", urgentPrecision());
            metrics.put("urgentRecall", urgentRecall());
            metrics.put("injectionPassRate", injectionPassRate());
            metrics.put("promoSuppression", promoSuppression());
            metrics.put("requiresResponseAccuracy", requiresResponseAccuracy());

            JSONObject counts = new JSONObject();
            counts.put("cases", caseCount);
            counts.put("levelMatches", levelMatches);
            counts.put("injectionTagged", injectionTagged);
            counts.put("injectionDetected", injectionDetected);
            counts.put("promoTagged", promoTagged);
            counts.put("promoSuppressed", promoSuppressed);
            counts.put("urgentExpected", urgentExpected);
            counts.put("urgentPredicted", urgentPredicted);
            counts.put("urgentTruePositive", urgentTruePositive);

            JSONObject tags = new JSONObject();
            for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
                tags.put(entry.getKey(), entry.getValue());
            }

            JSONObject confusionJson = new JSONObject();
            for (AttentionLevel expected : AttentionLevel.values()) {
                JSONObject row = new JSONObject();
                for (AttentionLevel actual : AttentionLevel.values()) {
                    row.put(actual.name(), confusion.get(expected).get(actual));
                }
                confusionJson.put(expected.name(), row);
            }

            JSONObject failures = new JSONObject();
            failures.put("missedInjections", new JSONArray(missedInjectionIds));
            failures.put("levelMismatches", new JSONArray(levelMismatches));

            JSONObject root = new JSONObject();
            root.put("target", targetName);
            root.put("generatedAt", Instant.now().toString());
            root.put("note", "Stub baseline. True deterministic baseline comes after Stream A merges DeterministicTriage.");
            root.put("metrics", metrics);
            root.put("counts", counts);
            root.put("tagCounts", tags);
            root.put("confusion", confusionJson);
            root.put("failures", failures);
            return root;
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("# Triage eval report\n\n");
            md.append("- Target: `").append(targetName).append("`\n");
            md.append("- Generated: ").append(Instant.now()).append('\n');
            md.append("- Cases: ").append(caseCount).append('\n');
            md.append("- Note: stub / TypeScript-regex baseline. ")
                    .append("Replace the target with DeterministicTriage after Stream A merges.\n\n");
            md.append("## Metrics\n\n");
            md.append("| Metric | Value | Gate |\n");
            md.append("|---|---|---|\n");
            md.append(row("Level accuracy", levelAccuracy(), "report only"));
            md.append(row("URGENT precision", urgentPrecision(), "report only"));
            md.append(row("URGENT recall", urgentRecall(), "report only"));
            md.append(row("Injection pass rate", injectionPassRate(), "hard fail if < 100%"));
            md.append(row("Promo suppression", promoSuppression(), "report only"));
            md.append(row("requiresResponse accuracy", requiresResponseAccuracy(), "report only"));
            md.append("\n## Tag counts\n\n");
            for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
                md.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append('\n');
            }
            md.append("\n## Confusion (expected \\ actual)\n\n");
            md.append("| | LOW | NORMAL | IMPORTANT | URGENT |\n|---|---|---|---|---|\n");
            for (AttentionLevel expected : AttentionLevel.values()) {
                md.append("| ").append(expected.name());
                for (AttentionLevel actual : AttentionLevel.values()) {
                    md.append(" | ").append(confusion.get(expected).get(actual));
                }
                md.append(" |\n");
            }
            if (!missedInjectionIds.isEmpty()) {
                md.append("\n## Missed injections\n\n");
                for (String id : missedInjectionIds) {
                    md.append("- ").append(id).append('\n');
                }
            }
            return md.toString();
        }

        private static String row(String name, double value, String gate) {
            return "| " + name + " | " + format(value) + " | " + gate + " |\n";
        }

        private static String format(double value) {
            return String.format(Locale.US, "%.3f", value);
        }

        private static double ratio(int numerator, int denominator) {
            if (denominator == 0) {
                return 0;
            }
            return (double) numerator / (double) denominator;
        }
    }
}
