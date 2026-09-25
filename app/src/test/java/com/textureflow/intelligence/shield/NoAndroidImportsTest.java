package com.textureflow.intelligence.shield;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class NoAndroidImportsTest {
    @Test
    public void shieldSourcesHaveNoAndroidImports() throws IOException {
        assertNoAndroidImports(sourceDir("shield"));
    }

    public static void assertNoAndroidImports(Path dir) throws IOException {
        assertTrue("missing source dir: " + dir.toAbsolutePath(), Files.isDirectory(dir));
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(dir)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse("expected Java sources in " + dir, sources.isEmpty());
        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            if (text.contains("import android.")) {
                violations.add(source.getFileName() + ": android import");
            }
        }
        if (!violations.isEmpty()) {
            fail("Forbidden android imports:\n" + String.join("\n", violations));
        }
    }

    public static Path sourceDir(String packageLeaf) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/intelligence/" + packageLeaf);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/intelligence/" + packageLeaf);
    }
}
