package com.textureflow.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TextureFlowDatabaseMigrationHookTest {
    private static final List<String> V2_TABLES = Arrays.asList(
            "attention_ticks",
            "assessments",
            "role_runs",
            "proposals",
            "capability_profile",
            "nodes",
            "compute_sessions",
            "depth_jobs",
            "boundary_reports",
            "tick_outcomes");

    @Test
    public void schemaVersionIsTwo() throws Exception {
        Field version = TextureFlowDatabase.class.getDeclaredField("DATABASE_VERSION");
        version.setAccessible(true);
        assertEquals(2, version.getInt(null));
    }

    @Test
    public void migrateTo2CreatesLedgerAndReservedTables() throws Exception {
        Method migrate = TextureFlowDatabase.class.getDeclaredMethod("migrateTo2", SQLiteDatabase.class);
        assertNotNull(migrate);
        assertEquals(void.class, migrate.getReturnType());
        assertTrue("migrateTo2 must be static so onUpgrade can call it without an instance",
                Modifier.isStatic(migrate.getModifiers()));

        String[] statements = TextureFlowDatabase.V2_STATEMENTS;
        assertTrue("migrateTo2 is no longer a no-op", statements.length > 0);
        String joined = String.join("\n", statements).toLowerCase(Locale.ROOT);
        for (String table : V2_TABLES) {
            assertTrue("missing CREATE TABLE " + table, joined.contains("create table " + table));
        }
    }

    @Test
    public void tickAssessmentAndRoleTablesHaveNoBodyColumns() {
        assertNoBodyColumns("attention_ticks");
        assertNoBodyColumns("assessments");
        assertNoBodyColumns("role_runs");
        Set<String> proposalColumns = columnsOf("proposals");
        assertTrue("proposals may keep payload_text until terminal status",
                proposalColumns.contains("payload_text"));
        assertFalse(proposalColumns.contains("body"));
    }

    @Test
    public void onCreateAppliesV1ThenMigrateTo2() throws IOException {
        String source = new String(Files.readAllBytes(databaseSource()), StandardCharsets.UTF_8);
        int onCreate = source.indexOf("public void onCreate(SQLiteDatabase db)");
        assertTrue("onCreate missing", onCreate >= 0);
        int onUpgrade = source.indexOf("public void onUpgrade(", onCreate);
        assertTrue("onUpgrade missing after onCreate", onUpgrade > onCreate);
        String onCreateBody = source.substring(onCreate, onUpgrade);
        int v1 = onCreateBody.indexOf("createV1Schema");
        int v2 = onCreateBody.indexOf("migrateTo2");
        assertTrue("onCreate must create v1 then migrateTo2", v1 >= 0 && v2 > v1);
    }

    private static void assertNoBodyColumns(String table) {
        Set<String> columns = columnsOf(table);
        for (String forbidden : new String[] {"body", "message", "message_body", "payload_text", "payload"}) {
            assertFalse(table + " must not store " + forbidden, columns.contains(forbidden));
        }
    }

    private static Set<String> columnsOf(String table) {
        String prefix = "create table " + table + " (";
        for (String sql : TextureFlowDatabase.V2_STATEMENTS) {
            String normalized = sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
            int idx = normalized.indexOf(prefix);
            if (idx < 0) {
                continue;
            }
            int start = idx + prefix.length();
            int end = normalized.lastIndexOf(')');
            if (end <= start) {
                fail("could not parse columns for " + table);
            }
            Set<String> columns = new LinkedHashSet<>();
            for (String part : normalized.substring(start, end).split(",")) {
                String name = part.trim().split("\\s+")[0];
                if (!name.isEmpty()) {
                    columns.add(name);
                }
            }
            return columns;
        }
        fail("missing CREATE TABLE " + table);
        return Set.of();
    }

    private static Path databaseSource() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/data/TextureFlowDatabase.java");
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/data/TextureFlowDatabase.java");
    }
}
