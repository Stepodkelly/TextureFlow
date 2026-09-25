package com.textureflow.intelligence.triage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TriageSurfaceTest {
    @Test
    public void triageSourcesHaveNoAndroidImportsOrNotificationKeys() throws IOException {
        Path dir = triageSourceDir();
        assertTrue("missing triage source dir: " + dir.toAbsolutePath(), Files.isDirectory(dir));
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(dir)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse("expected triage Java sources", sources.isEmpty());
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
            fail("Forbidden triage surface:\n" + String.join("\n", violations));
        }
    }

    private static Path triageSourceDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/intelligence/triage");
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/intelligence/triage");
    }
}
