package com.textureflow.intelligence.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ApiSurfaceTest {
    private static final List<Class<?>> API_TYPES = Arrays.asList(
            AttentionEngine.class,
            AttentionListener.class,
            Callback.class,
            JobClass.class,
            EventSignal.class,
            EventSignal.Kind.class,
            AttentionAssessment.class,
            AttentionLevel.class,
            AssessmentSource.class,
            SummaryQuery.class,
            SummaryResult.class,
            DraftQuery.class,
            ProposalDraft.class,
            CapabilityProfile.class,
            CapabilityProfile.Device.class,
            CapabilityProfile.Runtime.class,
            CapabilityProfile.Bench.class,
            CapabilityProfile.Budget.class,
            CapabilityProfile.ShardSpan.class,
            CapabilityProfile.Network.class,
            CapabilityProfile.Power.class,
            CapabilityTier.class,
            HelperCapTier.class,
            DataClass.class,
            ReplyTone.class,
            IntelligenceIntent.class
    );

    @Test
    public void attentionEngineExposesOnlyTheDocumentedMethods() {
        List<String> names = Arrays.stream(AttentionEngine.class.getDeclaredMethods())
                .map(Method::getName)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(
                Arrays.asList("addListener", "capability", "onEvent", "requestDraft", "requestSummary"),
                names);
    }

    @Test
    public void apiTypesNeverReferenceNotificationHandles() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : API_TYPES) {
            scanType(type, violations);
        }
        if (!violations.isEmpty()) {
            fail("Forbidden API surface:\n" + String.join("\n", violations));
        }
    }

    @Test
    public void apiSourcesHaveNoAndroidImportsOrHandleNames() throws IOException {
        Path apiDir = apiSourceDir();
        assertTrue("missing api source dir: " + apiDir.toAbsolutePath(), Files.isDirectory(apiDir));
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(apiDir)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse("expected api Java sources", sources.isEmpty());
        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            String lower = text.toLowerCase(Locale.ROOT);
            if (text.contains("import android.")) {
                violations.add(source.getFileName() + ": android import");
            }
            for (String token : new String[] {
                    "pendingintent", "remoteinput", "notificationkey", "notification_key"
            }) {
                if (lower.contains(token)) {
                    violations.add(source.getFileName() + ": contains " + token);
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Forbidden source tokens:\n" + String.join("\n", violations));
        }
    }

    private static void scanType(Class<?> type, List<String> violations) {
        String typeName = type.getName();
        if (forbiddenTypeName(typeName)) {
            violations.add(typeName + " itself is a forbidden type");
        }
        for (Field field : type.getDeclaredFields()) {
            if (forbiddenTypeName(field.getType().getName()) || forbiddenName(field.getName())) {
                violations.add(typeName + "#" + field.getName() + " : " + field.getType().getName());
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (forbiddenName(method.getName()) || forbiddenActionName(method.getName())) {
                violations.add(typeName + "." + method.getName() + "()");
            }
            if (forbiddenTypeName(method.getReturnType().getName())) {
                violations.add(typeName + "." + method.getName() + " returns " + method.getReturnType().getName());
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (forbiddenTypeName(parameter.getName())) {
                    violations.add(typeName + "." + method.getName() + " takes " + parameter.getName());
                }
            }
        }
    }

    private static boolean forbiddenTypeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("pendingintent") || lower.contains("remoteinput");
    }

    private static boolean forbiddenName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("pendingintent")
                || lower.contains("remoteinput")
                || lower.contains("notificationkey")
                || lower.contains("notification_key");
    }

    private static boolean forbiddenActionName(String name) {
        return "execute".equals(name) || "send".equals(name) || "dispatch".equals(name);
    }

    private static Path apiSourceDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/intelligence/api");
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/intelligence/api");
    }
}
