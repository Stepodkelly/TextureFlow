package com.textureflow.intelligence.evals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves the repo root whether Gradle cwd is the repo or the {@code app/} module. */
final class EvalPaths {
    private EvalPaths() {}

    static Path repoRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd,
                cwd.getParent(),
                cwd.resolve("..").normalize(),
        };
        for (Path candidate : candidates) {
            if (candidate != null && looksLikeRepo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate TextureFlow repo root from " + cwd);
    }

    static Path triageCasesV2() {
        return repoRoot().resolve("shared/evals/triage-cases-v2.json");
    }

    static Path draftCases() {
        return repoRoot().resolve("shared/evals/draft-cases.json");
    }

    static Path reportDir() {
        return repoRoot().resolve("app/build/reports/evals");
    }

    private static boolean looksLikeRepo(Path path) {
        return Files.isRegularFile(path.resolve("shared/evals/triage-cases-v2.json"))
                && Files.isRegularFile(path.resolve("shared/evals/intelligence-cases.json"));
    }
}
