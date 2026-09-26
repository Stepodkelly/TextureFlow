package com.textureflow.intelligence.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * JVM surface/contract checks for {@link SqliteLedger}. Real SQLite is exercised
 * in {@code androidTest} when a device or emulator is available.
 */
public final class SqliteLedgerApiTest {
    @Test
    public void implementsIntelligenceLedgerWithContextAndHelperConstructors() throws Exception {
        assertTrue(IntelligenceLedger.class.isAssignableFrom(SqliteLedger.class));
        assertTrue(Modifier.isFinal(SqliteLedger.class.getModifiers()));
        SqliteLedger.class.getConstructor(Context.class);
        SqliteLedger.class.getConstructor(SQLiteOpenHelper.class);
        for (String name : new String[] {
                "getTick", "getTicks", "getAssessment", "getAssessments",
                "getRoleRuns", "getProposal", "getProposals"}) {
            boolean found = false;
            for (Method method : SqliteLedger.class.getDeclaredMethods()) {
                if (name.equals(method.getName()) && Modifier.isPublic(method.getModifiers())) {
                    found = true;
                    break;
                }
            }
            assertTrue("missing public " + name, found);
        }
    }

    @Test
    public void sqlClearsPayloadOnWriteAndPurgesLikeInMemoryLedger() throws IOException {
        String source = new String(Files.readAllBytes(sqliteLedgerSource()), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        assertTrue(source.contains("insert or replace into proposals("));
        assertTrue("terminal writes must force empty payload_text",
                source.contains("status().isterminal() ? \"\" : proposal.getpayloadtext()"));
        assertTrue(source.contains("delete from assessments where tick_id in"));
        assertTrue(source.contains("delete from role_runs where tick_id in"));
        assertTrue(source.contains("delete from attention_ticks where started_at < ?"));
        assertTrue(source.contains("delete from proposals where status in"));
        assertTrue(source.contains("and created_at < ?"));
        assertEquals(4, terminalCount());
    }

    private static int terminalCount() {
        int count = 0;
        for (ProposalStatus status : ProposalStatus.values()) {
            if (status.isTerminal()) {
                count++;
            }
        }
        return count;
    }

    private static Path sqliteLedgerSource() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/intelligence/ledger/SqliteLedger.java");
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        return cwd.resolve("app/src/main/java/com/textureflow/intelligence/ledger/SqliteLedger.java");
    }
}
