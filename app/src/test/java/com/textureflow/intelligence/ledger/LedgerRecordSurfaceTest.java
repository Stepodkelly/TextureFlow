package com.textureflow.intelligence.ledger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
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

public final class LedgerRecordSurfaceTest {
    private static final List<Class<?>> METRIC_RECORDS = Arrays.asList(
            TickRecord.class,
            AssessmentRecord.class,
            RoleRunRecord.class);

    private static final List<String> FORBIDDEN_FIELD_NAMES = Arrays.asList(
            "body",
            "message",
            "messagebody",
            "rawbody",
            "notificationbody",
            "eventbody",
            "payload",
            "payloadtext",
            "replytext",
            "text");

    @Test
    public void tickAssessmentAndRoleRecordsCannotCarryABody() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : METRIC_RECORDS) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (FORBIDDEN_FIELD_NAMES.contains(name)) {
                    violations.add(type.getSimpleName() + "#" + field.getName());
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Tick/assessment/role records must not carry a body:\n" + String.join("\n", violations));
        }
    }

    @Test
    public void ledgerSourcesHaveNoAndroidImports() throws IOException {
        Path ledgerDir = ledgerSourceDir();
        assertTrue("missing ledger source dir: " + ledgerDir.toAbsolutePath(), Files.isDirectory(ledgerDir));
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(ledgerDir)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse("expected ledger Java sources", sources.isEmpty());
        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            if ("SqliteLedger.java".equals(source.getFileName().toString())) {
                continue;
            }
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            if (text.contains("import android.")) {
                violations.add(source.getFileName() + ": android import");
            }
        }
        if (!violations.isEmpty()) {
            fail("InMemoryLedger and ledger types must stay Android-free:\n" + String.join("\n", violations));
        }
    }

    private static Path ledgerSourceDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/intelligence/ledger");
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/intelligence/ledger");
    }
}
