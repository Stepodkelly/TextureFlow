package com.textureflow.intelligence.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.textureflow.actions.ActionType;
import com.textureflow.data.TextureFlowDatabase;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Contract suite against real SQLite. Mirrors {@link InMemoryLedgerTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class SqliteLedgerInstrumentedTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final String DB_NAME = "sqlite-ledger-contract.db";

    private Context context;
    private TextureFlowDatabase database;
    private SqliteLedger ledger;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        database = new TextureFlowDatabase(context, DB_NAME);
        ledger = new SqliteLedger(database);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        if (context != null) {
            context.deleteDatabase(DB_NAME);
        }
    }

    @Test
    public void recordsTickAssessmentAndRoleRunAcrossReopen() {
        ledger.recordTick(sampleTick("tick-1", NOW));
        ledger.recordAssessment(sampleAssessment("tick-1", "direct_request", "Sam asked you to come down."));
        ledger.recordRoleRun(sampleRoleRun("tick-1"));

        database.close();
        database = new TextureFlowDatabase(context, DB_NAME);
        ledger = new SqliteLedger(database);

        TickRecord storedTick = ledger.getTick("tick-1");
        assertEquals("tick-1", storedTick.getTickId());
        assertEquals(TickTrigger.EVENT_POSTED, storedTick.getTrigger());
        assertEquals("person-sam", storedTick.getPersonId());
        assertEquals("com.whatsapp", storedTick.getPackageName());
        assertEquals(NOW, storedTick.getStartedAt());
        assertEquals(12L, storedTick.getDurationMs());
        assertEquals("A3", storedTick.getEscalationRule());
        assertEquals(CapabilityTier.T0, storedTick.getTier());
        assertEquals(AssessmentSource.DETERMINISTIC, storedTick.getSource());

        AssessmentRecord storedAssessment = ledger.getAssessment("tick-1");
        assertEquals(AttentionLevel.IMPORTANT, storedAssessment.getLevel());
        assertEquals(0.72, storedAssessment.getSystemConfidence(), 0.0);
        assertNull(storedAssessment.getModelConfidence());
        assertEquals("direct_request", storedAssessment.getReasonCode());
        assertEquals("Sam asked you to come down.", storedAssessment.getReasonText());

        assertEquals(1, ledger.getRoleRuns("tick-1").size());
        RoleRunRecord storedRole = ledger.getRoleRuns("tick-1").get(0);
        assertEquals(CouncilRole.TRIAGE, storedRole.getRole());
        assertEquals("triage.v1", storedRole.getPromptVersion());
        assertEquals(80, storedRole.getInputTokens());
        assertEquals(20, storedRole.getOutputTokens());
        assertEquals(15L, storedRole.getWallMs());
        assertTrue(storedRole.isSchemaValid());
        assertEquals(Collections.emptyList(), storedRole.getSkepticIssues());
    }

    @Test
    public void upsertKeepsPayloadWhileOpenThenClearsOnTerminalTransition() {
        ledger.upsertProposal(sampleProposal("prop-1", ProposalStatus.OPEN, "I'm coming down.", NOW));
        assertEquals("I'm coming down.", ledger.getProposal("prop-1").getPayloadText());

        ProposalRecord confirmed = ledger.transitionProposal("prop-1", ProposalStatus.CONFIRMED);
        assertEquals(ProposalStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("", confirmed.getPayloadText());
        assertEquals("", ledger.getProposal("prop-1").getPayloadText());
    }

    @Test
    public void everyTerminalStatusClearsPayload() {
        for (ProposalStatus status : ProposalStatus.values()) {
            if (!status.isTerminal()) {
                continue;
            }
            String id = "prop-" + status.name();
            ledger.upsertProposal(sampleProposal(id, ProposalStatus.OPEN, "keep this until terminal", NOW));
            assertEquals("", ledger.transitionProposal(id, status).getPayloadText());
            assertEquals("", ledger.getProposal(id).getPayloadText());
        }
    }

    @Test
    public void upsertOfAlreadyTerminalProposalIsRejected() {
        ledger.upsertProposal(sampleProposal("prop-term", ProposalStatus.OPEN, "secret", NOW));
        ledger.transitionProposal("prop-term", ProposalStatus.EXPIRED);
        try {
            ledger.upsertProposal(sampleProposal("prop-term", ProposalStatus.OPEN, "revived", NOW));
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("terminal"));
        }
        assertEquals(ProposalStatus.EXPIRED, ledger.getProposal("prop-term").getStatus());
        assertEquals("", ledger.getProposal("prop-term").getPayloadText());
    }

    @Test
    public void transitionOfUnknownProposalFails() {
        try {
            ledger.transitionProposal("missing", ProposalStatus.STALE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown proposal"));
        }
    }

    @Test
    public void capabilityProfileSaveAndLoad() {
        assertNull(ledger.loadCapabilityProfile());
        ledger.saveCapabilityProfile(sampleProfile());
        CapabilityProfile loaded = ledger.loadCapabilityProfile();
        assertEquals("Pixel 8", loaded.getDevice().getModel());
        assertEquals(CapabilityTier.T0, loaded.getTier());
        assertEquals(HelperCapTier.C0, loaded.getHelperCapTier());
        assertEquals("none", loaded.getRuntime().getPort());
        assertTrue(loaded.getRolesEligible().isEmpty());
        assertTrue(loaded.getShardEligible().isEmpty());
    }

    @Test
    public void purgeOlderThanDropsStaleMetricsAndTerminalProposals() {
        ledger.recordTick(sampleTick("old-tick", NOW - IntelligenceLedger.RETENTION_MILLIS - 1));
        ledger.recordAssessment(sampleAssessment("old-tick", "old", "old reason"));
        ledger.recordRoleRun(sampleRoleRun("old-tick"));
        ledger.recordTick(sampleTick("new-tick", NOW - IntelligenceLedger.RETENTION_MILLIS + 1));
        ledger.recordAssessment(sampleAssessment("new-tick", "new", "new reason"));
        ledger.recordRoleRun(sampleRoleRun("new-tick"));
        ledger.upsertProposal(sampleProposal("old-open", ProposalStatus.OPEN, "still needed",
                NOW - IntelligenceLedger.RETENTION_MILLIS - 5));
        ledger.upsertProposal(sampleProposal("old-done", ProposalStatus.OPEN, "done text",
                NOW - IntelligenceLedger.RETENTION_MILLIS - 5));
        ledger.transitionProposal("old-done", ProposalStatus.CONFIRMED);
        ledger.upsertProposal(sampleProposal("new-done", ProposalStatus.OPEN, "fresh", NOW));
        ledger.transitionProposal("new-done", ProposalStatus.CONFIRMED);

        ledger.purgeOlderThan(NOW - IntelligenceLedger.RETENTION_MILLIS);

        assertNull(ledger.getTick("old-tick"));
        assertNull(ledger.getAssessment("old-tick"));
        assertTrue(ledger.getRoleRuns("old-tick").isEmpty());
        assertEquals("new-tick", ledger.getTick("new-tick").getTickId());
        assertEquals("new reason", ledger.getAssessment("new-tick").getReasonText());
        assertEquals(1, ledger.getRoleRuns("new-tick").size());
        assertEquals(ProposalStatus.OPEN, ledger.getProposal("old-open").getStatus());
        assertEquals("still needed", ledger.getProposal("old-open").getPayloadText());
        assertNull(ledger.getProposal("old-done"));
        assertEquals(ProposalStatus.CONFIRMED, ledger.getProposal("new-done").getStatus());
    }

    private static TickRecord sampleTick(String tickId, long startedAt) {
        return new TickRecord(
                tickId,
                TickTrigger.EVENT_POSTED,
                "person-sam",
                "com.whatsapp",
                startedAt,
                12L,
                "A3",
                CapabilityTier.T0,
                AssessmentSource.DETERMINISTIC);
    }

    private static AssessmentRecord sampleAssessment(String tickId, String reasonCode, String reasonText) {
        return new AssessmentRecord(
                tickId,
                AttentionLevel.IMPORTANT,
                0.72,
                null,
                reasonCode,
                reasonText);
    }

    private static RoleRunRecord sampleRoleRun(String tickId) {
        return new RoleRunRecord(
                tickId,
                CouncilRole.TRIAGE,
                "triage.v1",
                80,
                20,
                15L,
                true,
                Collections.emptyList());
    }

    private static ProposalRecord sampleProposal(
            String proposalId, ProposalStatus status, String payload, long createdAt) {
        return new ProposalRecord(
                proposalId,
                "event-1",
                1,
                "com.whatsapp",
                "person-sam",
                ActionType.REPLY,
                "hash-1",
                status,
                createdAt,
                createdAt + 60_000L,
                payload);
    }

    private static CapabilityProfile sampleProfile() {
        return new CapabilityProfile(
                new CapabilityProfile.Device("Pixel 8", "Tensor", 34, 8000),
                new CapabilityProfile.Runtime("none", "", "none"),
                new CapabilityProfile.Bench(0, 0, 0, 0),
                new CapabilityProfile.Budget(2000, 1, "nominal"),
                CapabilityTier.T0,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                CapabilityProfile.Network.UNKNOWN,
                CapabilityProfile.Power.UNKNOWN);
    }
}
