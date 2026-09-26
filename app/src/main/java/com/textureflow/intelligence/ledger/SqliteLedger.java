package com.textureflow.intelligence.ledger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.textureflow.actions.ActionType;
import com.textureflow.data.TextureFlowDatabase;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link IntelligenceLedger} backed by {@link TextureFlowDatabase} v2 tables.
 * Proposal {@code payload_text} is stored only while status is {@link ProposalStatus#OPEN}.
 */
public final class SqliteLedger implements IntelligenceLedger {
    private final SQLiteOpenHelper database;

    public SqliteLedger(Context context) {
        this(new TextureFlowDatabase(Objects.requireNonNull(context, "context")));
    }

    public SqliteLedger(SQLiteOpenHelper database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public synchronized void recordTick(TickRecord tick) {
        Objects.requireNonNull(tick, "tick");
        writable().execSQL(
                "INSERT OR REPLACE INTO attention_ticks("
                        + "tick_id, trigger, person_id, package_name, started_at, duration_ms, "
                        + "escalation_rule, tier, source) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[] {
                        tick.getTickId(),
                        tick.getTrigger().name(),
                        tick.getPersonId(),
                        tick.getPackageName(),
                        tick.getStartedAt(),
                        tick.getDurationMs(),
                        tick.getEscalationRule(),
                        tick.getTier().name(),
                        tick.getSource().name()
                });
    }

    @Override
    public synchronized void recordAssessment(AssessmentRecord assessment) {
        Objects.requireNonNull(assessment, "assessment");
        writable().execSQL(
                "INSERT OR REPLACE INTO assessments("
                        + "tick_id, level, system_confidence, model_confidence, reason_code, reason_text) "
                        + "VALUES(?, ?, ?, ?, ?, ?)",
                new Object[] {
                        assessment.getTickId(),
                        assessment.getLevel().name(),
                        assessment.getSystemConfidence(),
                        assessment.getModelConfidence(),
                        assessment.getReasonCode(),
                        assessment.getReasonText()
                });
    }

    @Override
    public synchronized void recordRoleRun(RoleRunRecord roleRun) {
        Objects.requireNonNull(roleRun, "roleRun");
        writable().execSQL(
                "INSERT INTO role_runs("
                        + "tick_id, role, prompt_version, input_tokens, output_tokens, wall_ms, "
                        + "schema_valid, skeptic_issues) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[] {
                        roleRun.getTickId(),
                        roleRun.getRole().name(),
                        roleRun.getPromptVersion(),
                        roleRun.getInputTokens(),
                        roleRun.getOutputTokens(),
                        roleRun.getWallMs(),
                        roleRun.isSchemaValid() ? 1 : 0,
                        encodeIssues(roleRun.getSkepticIssues())
                });
    }

    @Override
    public synchronized void upsertProposal(ProposalRecord proposal) {
        Objects.requireNonNull(proposal, "proposal");
        SQLiteDatabase db = writable();
        db.beginTransaction();
        try {
            ProposalRecord existing = readProposal(db, proposal.getProposalId());
            if (existing != null && existing.getStatus().isTerminal()) {
                throw new IllegalStateException("cannot upsert terminal proposal: " + proposal.getProposalId());
            }
            writeProposal(db, proposal.withStatus(proposal.getStatus()));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized ProposalRecord transitionProposal(String proposalId, ProposalStatus newStatus) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(newStatus, "newStatus");
        SQLiteDatabase db = writable();
        db.beginTransaction();
        try {
            ProposalRecord existing = readProposal(db, proposalId);
            if (existing == null) {
                throw new IllegalArgumentException("unknown proposal: " + proposalId);
            }
            if (existing.getStatus() == newStatus) {
                db.setTransactionSuccessful();
                return existing;
            }
            if (existing.getStatus().isTerminal()) {
                throw new IllegalStateException("proposal already terminal: " + proposalId);
            }
            ProposalRecord updated = existing.withStatus(newStatus);
            writeProposal(db, updated);
            db.setTransactionSuccessful();
            return updated;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized CapabilityProfile loadCapabilityProfile() {
        try (Cursor cursor = readable().rawQuery(
                "SELECT profile_json FROM capability_profile WHERE singleton_id = 1",
                null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return CapabilityProfileJson.decode(cursor.getString(0));
        }
    }

    @Override
    public synchronized void saveCapabilityProfile(CapabilityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        writable().execSQL(
                "INSERT OR REPLACE INTO capability_profile(singleton_id, profile_json, updated_at) "
                        + "VALUES(1, ?, ?)",
                new Object[] {CapabilityProfileJson.encode(profile), System.currentTimeMillis()});
    }

    @Override
    public synchronized void purgeOlderThan(long cutoffMillis) {
        SQLiteDatabase db = writable();
        db.beginTransaction();
        try {
            Object[] cutoff = {cutoffMillis};
            db.execSQL(
                    "DELETE FROM assessments WHERE tick_id IN "
                            + "(SELECT tick_id FROM attention_ticks WHERE started_at < ?)",
                    cutoff);
            db.execSQL(
                    "DELETE FROM role_runs WHERE tick_id IN "
                            + "(SELECT tick_id FROM attention_ticks WHERE started_at < ?)",
                    cutoff);
            db.execSQL("DELETE FROM attention_ticks WHERE started_at < ?", cutoff);
            db.execSQL(
                    "DELETE FROM proposals WHERE status IN (" + terminalStatusSql() + ") AND created_at < ?",
                    cutoff);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized TickRecord getTick(String tickId) {
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, trigger, person_id, package_name, started_at, duration_ms, "
                        + "escalation_rule, tier, source FROM attention_ticks WHERE tick_id = ?",
                new String[] {tickId})) {
            return cursor.moveToFirst() ? readTick(cursor) : null;
        }
    }

    public synchronized List<TickRecord> getTicks() {
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, trigger, person_id, package_name, started_at, duration_ms, "
                        + "escalation_rule, tier, source FROM attention_ticks "
                        + "ORDER BY started_at ASC, tick_id ASC",
                null)) {
            return readAll(cursor, SqliteLedger::readTick);
        }
    }

    public synchronized AssessmentRecord getAssessment(String tickId) {
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, level, system_confidence, model_confidence, reason_code, reason_text "
                        + "FROM assessments WHERE tick_id = ?",
                new String[] {tickId})) {
            return cursor.moveToFirst() ? readAssessment(cursor) : null;
        }
    }

    public synchronized List<AssessmentRecord> getAssessments() {
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, level, system_confidence, model_confidence, reason_code, reason_text "
                        + "FROM assessments ORDER BY tick_id ASC",
                null)) {
            return readAll(cursor, SqliteLedger::readAssessment);
        }
    }

    public synchronized List<RoleRunRecord> getRoleRuns() {
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, role, prompt_version, input_tokens, output_tokens, wall_ms, "
                        + "schema_valid, skeptic_issues FROM role_runs ORDER BY id ASC",
                null)) {
            return readAll(cursor, SqliteLedger::readRoleRun);
        }
    }

    public synchronized List<RoleRunRecord> getRoleRuns(String tickId) {
        Objects.requireNonNull(tickId, "tickId");
        try (Cursor cursor = readable().rawQuery(
                "SELECT tick_id, role, prompt_version, input_tokens, output_tokens, wall_ms, "
                        + "schema_valid, skeptic_issues FROM role_runs WHERE tick_id = ? ORDER BY id ASC",
                new String[] {tickId})) {
            return readAll(cursor, SqliteLedger::readRoleRun);
        }
    }

    public synchronized ProposalRecord getProposal(String proposalId) {
        return readProposal(readable(), proposalId);
    }

    public synchronized List<ProposalRecord> getProposals() {
        try (Cursor cursor = readable().rawQuery(
                "SELECT proposal_id, event_id, event_version, package_name, person_id, action_type, "
                        + "payload_hash, status, created_at, expires_at, payload_text "
                        + "FROM proposals ORDER BY created_at ASC, proposal_id ASC",
                null)) {
            return readAll(cursor, SqliteLedger::readProposalRow);
        }
    }

    private SQLiteDatabase writable() {
        return database.getWritableDatabase();
    }

    private SQLiteDatabase readable() {
        return database.getReadableDatabase();
    }

    private static void writeProposal(SQLiteDatabase db, ProposalRecord proposal) {
        String payload = proposal.getStatus().isTerminal() ? "" : proposal.getPayloadText();
        db.execSQL(
                "INSERT OR REPLACE INTO proposals("
                        + "proposal_id, event_id, event_version, package_name, person_id, action_type, "
                        + "payload_hash, status, created_at, expires_at, payload_text) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[] {
                        proposal.getProposalId(),
                        proposal.getEventId(),
                        proposal.getEventVersion(),
                        proposal.getPackageName(),
                        proposal.getPersonId(),
                        proposal.getActionType().name(),
                        proposal.getPayloadHash(),
                        proposal.getStatus().name(),
                        proposal.getCreatedAt(),
                        proposal.getExpiresAt(),
                        payload
                });
    }

    private static ProposalRecord readProposal(SQLiteDatabase db, String proposalId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT proposal_id, event_id, event_version, package_name, person_id, action_type, "
                        + "payload_hash, status, created_at, expires_at, payload_text "
                        + "FROM proposals WHERE proposal_id = ?",
                new String[] {proposalId})) {
            return cursor.moveToFirst() ? readProposalRow(cursor) : null;
        }
    }

    private static TickRecord readTick(Cursor cursor) {
        return new TickRecord(
                cursor.getString(0),
                TickTrigger.valueOf(cursor.getString(1)),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getLong(4),
                cursor.getLong(5),
                cursor.getString(6),
                CapabilityTier.valueOf(cursor.getString(7)),
                AssessmentSource.valueOf(cursor.getString(8)));
    }

    private static AssessmentRecord readAssessment(Cursor cursor) {
        Double modelConfidence = cursor.isNull(3) ? null : cursor.getDouble(3);
        return new AssessmentRecord(
                cursor.getString(0),
                AttentionLevel.valueOf(cursor.getString(1)),
                cursor.getDouble(2),
                modelConfidence,
                cursor.getString(4),
                cursor.getString(5));
    }

    private static RoleRunRecord readRoleRun(Cursor cursor) {
        return new RoleRunRecord(
                cursor.getString(0),
                CouncilRole.valueOf(cursor.getString(1)),
                cursor.getString(2),
                cursor.getInt(3),
                cursor.getInt(4),
                cursor.getLong(5),
                cursor.getInt(6) != 0,
                decodeIssues(cursor.isNull(7) ? null : cursor.getString(7)));
    }

    private static ProposalRecord readProposalRow(Cursor cursor) {
        ProposalStatus status = ProposalStatus.valueOf(cursor.getString(7));
        String payload = cursor.isNull(10) ? "" : cursor.getString(10);
        return new ProposalRecord(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getInt(2),
                cursor.getString(3),
                cursor.getString(4),
                ActionType.valueOf(cursor.getString(5)),
                cursor.getString(6),
                status,
                cursor.getLong(8),
                cursor.getLong(9),
                status.isTerminal() ? "" : payload);
    }

    private static String encodeIssues(List<String> issues) {
        JSONArray array = new JSONArray();
        for (String issue : issues) {
            array.put(issue);
        }
        return array.toString();
    }

    private static List<String> decodeIssues(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<String> issues = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                issues.add(array.getString(i));
            }
            return issues;
        } catch (JSONException error) {
            throw new IllegalStateException("decode skeptic_issues", error);
        }
    }

    private static String terminalStatusSql() {
        StringBuilder sql = new StringBuilder();
        for (ProposalStatus status : ProposalStatus.values()) {
            if (!status.isTerminal()) {
                continue;
            }
            if (sql.length() > 0) {
                sql.append(',');
            }
            sql.append('\'').append(status.name()).append('\'');
        }
        return sql.toString();
    }

    private static <T> List<T> readAll(Cursor cursor, CursorReader<T> reader) {
        List<T> rows = new ArrayList<>();
        while (cursor.moveToNext()) {
            rows.add(reader.read(cursor));
        }
        return Collections.unmodifiableList(rows);
    }

    @FunctionalInterface
    private interface CursorReader<T> {
        T read(Cursor cursor);
    }
}
