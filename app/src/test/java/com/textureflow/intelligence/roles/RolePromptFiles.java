package com.textureflow.intelligence.roles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class RolePromptFiles {
    private RolePromptFiles() {}

    static String load(String fileName) {
        Path[] candidates = {
                Paths.get("app/src/main/assets/intelligence/prompts/" + fileName),
                Paths.get("src/main/assets/intelligence/prompts/" + fileName),
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                try {
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("missing " + fileName);
    }
}
