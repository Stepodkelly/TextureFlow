package com.textureflow.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionReceipt;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.actions.LiveActionRegistry;
import com.textureflow.actions.NotificationActionExecutor;
import com.textureflow.actions.NotificationControl;
import com.textureflow.data.ActionReceiptStore;
import com.textureflow.data.DeviceIdentity;
import com.textureflow.data.ListenerHealthStore;
import com.textureflow.data.NotificationRepository;
import com.textureflow.data.OutboxStore;
import com.textureflow.data.TextureFlowDatabase;
import com.textureflow.policy.CommandPolicy;

public final class NotificationRuntime {
    private static final String OWNER_PREFERENCES = "textureflow-owner";
    private static final String OWNER_ID = "owner-id";
    private static volatile NotificationRuntime instance;

    private final Context context;
    private final String deviceId;
    private final TextureFlowDatabase database;
    private final NotificationRepository notifications;
    private final OutboxStore outbox;
    private final ActionReceiptStore receipts;
    private final ListenerHealthStore health;
    private final LiveActionRegistry liveActions;

    private NotificationRuntime(Context context) {
        this.context = context.getApplicationContext();
        this.deviceId = DeviceIdentity.getOrCreate(this.context);
        this.database = new TextureFlowDatabase(this.context);
        this.notifications = new NotificationRepository(database);
        this.outbox = new OutboxStore(database);
        this.receipts = new ActionReceiptStore(database);
        this.health = new ListenerHealthStore(database);
        this.liveActions = new LiveActionRegistry();
    }

    public static NotificationRuntime get(Context context) {
        NotificationRuntime current = instance;
        if (current != null) return current;
        synchronized (NotificationRuntime.class) {
            if (instance == null) instance = new NotificationRuntime(context);
            return instance;
        }
    }

    public String getDeviceId() { return deviceId; }
    public NotificationRepository notifications() { return notifications; }
    public OutboxStore outbox() { return outbox; }
    public ActionReceiptStore receipts() { return receipts; }
    public ListenerHealthStore health() { return health; }
    public LiveActionRegistry liveActions() { return liveActions; }

    /** Called by authenticated transport setup; this is an opaque account ID, not a secret token. */
    public void configureOwner(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner ID is required");
        }
        boolean saved = context.getSharedPreferences(OWNER_PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(OWNER_ID, ownerId).commit();
        if (!saved) throw new IllegalStateException("Could not persist TextureFlow owner ID");
    }

    public synchronized ActionReceipt execute(
            ActionCommand command, ConfirmedProposal confirmation, NotificationControl control) {
        String ownerId = context.getSharedPreferences(OWNER_PREFERENCES, Context.MODE_PRIVATE)
                .getString(OWNER_ID, null);
        NotificationActionExecutor executor = new NotificationActionExecutor(
                context, deviceId, liveActions, notifications, receipts,
                new CommandPolicy(ownerId, deviceId));
        return executor.execute(command, confirmation, control);
    }

}
