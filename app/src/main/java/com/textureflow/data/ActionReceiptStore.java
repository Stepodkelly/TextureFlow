package com.textureflow.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionErrorCode;
import com.textureflow.actions.ActionReceipt;

import org.json.JSONException;

public final class ActionReceiptStore {
    public enum ReservationState { NEW, COMPLETED, UNCERTAIN }

    public static final class Reservation {
        private final ReservationState state;
        private final String reservedCommandId;
        private final ActionReceipt existingReceipt;

        Reservation(ReservationState state, String reservedCommandId, ActionReceipt existingReceipt) {
            this.state = state;
            this.reservedCommandId = reservedCommandId;
            this.existingReceipt = existingReceipt;
        }

        public ReservationState getState() { return state; }
        public String getReservedCommandId() { return reservedCommandId; }
        public ActionReceipt getExistingReceipt() { return existingReceipt; }
    }

    private final TextureFlowDatabase database;

    public ActionReceiptStore(TextureFlowDatabase database) {
        this.database = database;
    }

    /**
     * Persists DISPATCHING before the external PendingIntent side effect. An interrupted
     * reservation is never replayed automatically because its external outcome is unknown.
     */
    public Reservation reserve(ActionCommand command, long now) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            String priorCommandId = null;
            String priorState = null;
            String receiptId = null;
            try (Cursor cursor = db.rawQuery(
                    "SELECT command_id, state, receipt_id FROM processed_commands "
                            + "WHERE command_id = ? OR idempotency_key = ? LIMIT 1",
                    new String[] {command.getCommandId(), command.getIdempotencyKey()})) {
                if (cursor.moveToFirst()) {
                    priorCommandId = cursor.getString(0);
                    priorState = cursor.getString(1);
                    receiptId = cursor.isNull(2) ? null : cursor.getString(2);
                }
            }

            Reservation result;
            if (priorCommandId == null) {
                db.execSQL(
                        "INSERT INTO processed_commands(command_id, idempotency_key, proposal_id, state, created_at, updated_at) "
                                + "VALUES(?, ?, ?, 'DISPATCHING', ?, ?)",
                        new Object[] {command.getCommandId(), command.getIdempotencyKey(),
                                command.getProposalId(), now, now});
                result = new Reservation(ReservationState.NEW, command.getCommandId(), null);
            } else if ("COMPLETED".equals(priorState) && receiptId != null) {
                result = new Reservation(
                        ReservationState.COMPLETED, priorCommandId, readReceipt(db, receiptId));
            } else {
                result = new Reservation(ReservationState.UNCERTAIN, priorCommandId, null);
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    public void complete(String reservedCommandId, ActionReceipt receipt, long now) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL(
                    "INSERT OR IGNORE INTO action_receipts(receipt_id, command_id, device_id, status, error_code, "
                            + "message, device_timestamp, texture_cue, trace_id, created_at) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[] {receipt.getReceiptId(), reservedCommandId, receipt.getDeviceId(),
                            receipt.getStatus(), receipt.getErrorCode() == null ? null : receipt.getErrorCode().name(),
                            receipt.getMessage(), receipt.getDeviceTimestamp(), receipt.getTextureCue(),
                            receipt.getTraceId(), now});
            db.execSQL(
                    "UPDATE processed_commands SET state = 'COMPLETED', receipt_id = ?, updated_at = ? "
                            + "WHERE command_id = ?",
                    new Object[] {receipt.getReceiptId(), now, reservedCommandId});
            try {
                TextureFlowDatabase.enqueue(
                        db, "receipt:" + receipt.getReceiptId(), "RECEIPT_COMPLETE", reservedCommandId,
                        receipt.toJson().toString(), now);
            } catch (JSONException invalidContract) {
                throw new IllegalStateException("Could not serialize receipt contract", invalidContract);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public ActionReceipt getByCommandId(String commandId) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT receipt_id FROM processed_commands WHERE command_id = ? AND state = 'COMPLETED' LIMIT 1",
                new String[] {commandId})) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) return null;
            return readReceipt(db, cursor.getString(0));
        }
    }

    private static ActionReceipt readReceipt(SQLiteDatabase db, String receiptId) {
        try (Cursor cursor = db.query(
                "action_receipts",
                new String[] {"receipt_id", "command_id", "device_id", "status", "error_code", "message",
                        "device_timestamp", "texture_cue", "trace_id"},
                "receipt_id = ?", new String[] {receiptId}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) return null;
            ActionErrorCode error = cursor.isNull(4) ? null : ActionErrorCode.valueOf(cursor.getString(4));
            return new ActionReceipt(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    error, cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getString(8));
        }
    }
}
