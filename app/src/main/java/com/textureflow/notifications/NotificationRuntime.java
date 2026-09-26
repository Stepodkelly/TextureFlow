package com.textureflow.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionReceipt;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.actions.LiveActionRegistry;
import com.textureflow.actions.NotificationActionExecutor;
import com.textureflow.actions.NotificationControl;
import com.textureflow.bank.BankStore;
import com.textureflow.bank.HandshakeRefill;
import com.textureflow.bank.NotificationBank;
import com.textureflow.bank.OutboundMessageOutbox;
import com.textureflow.data.ActionReceiptStore;
import com.textureflow.data.DeviceIdentity;
import com.textureflow.data.ListenerHealthStore;
import com.textureflow.data.NotificationRepository;
import com.textureflow.data.OutboxStore;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.data.TextureFlowDatabase;
import com.textureflow.intelligence.api.AttentionEngine;
import com.textureflow.intelligence.api.EventSignal;
import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.engine.DefaultAttentionEngine;
import com.textureflow.intelligence.engine.EventStore;
import com.textureflow.intelligence.engine.PersonKeys;
import com.textureflow.intelligence.engine.StoredEvent;
import com.textureflow.intelligence.ledger.IntelligenceLedger;
import com.textureflow.intelligence.ledger.SqliteLedger;
import com.textureflow.intelligence.model.CapabilityProbe;
import com.textureflow.intelligence.model.ModelLifecycle;
import com.textureflow.intelligence.model.ModelPorts;
import com.textureflow.policy.CommandPolicy;

import java.util.ArrayList;
import java.util.List;

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
    private final IntelligenceLedger ledger;
    private final ModelLifecycle modelLifecycle;
    private final LiveActionRegistry liveActions;
    private final NotificationBank bank;
    private final Object engineLock = new Object();
    private volatile AttentionEngine attentionEngine;

    private NotificationRuntime(Context context) {
        this.context = context.getApplicationContext();
        this.deviceId = DeviceIdentity.getOrCreate(this.context);
        this.database = new TextureFlowDatabase(this.context);
        this.notifications = new NotificationRepository(database);
        this.outbox = new OutboxStore(database);
        this.receipts = new ActionReceiptStore(database);
        this.health = new ListenerHealthStore(database);
        this.ledger = new SqliteLedger(database);
        this.modelLifecycle = ModelPorts.lifecycle(this.context);
        this.liveActions = new LiveActionRegistry();
        this.bank = new NotificationBank(
                new BankStore(this.context),
                new OutboundMessageOutbox(this.context),
                new HandshakeRefill(this.context));
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
    public IntelligenceLedger ledger() { return ledger; }
    public ModelLifecycle modelLifecycle() { return modelLifecycle; }
    public LiveActionRegistry liveActions() { return liveActions; }
    public NotificationBank bank() { return bank; }

    public AttentionEngine attention() {
        AttentionEngine current = attentionEngine;
        if (current != null) {
            return current;
        }
        synchronized (engineLock) {
            if (attentionEngine == null) {
                CapabilityProfile profile = CapabilityProbe.fromAndroid(context);
                try {
                    ledger.saveCapabilityProfile(profile);
                } catch (RuntimeException ignored) {
                    // Probe persist must not block capture.
                }
                attentionEngine = new DefaultAttentionEngine(
                        new RepositoryEventStore(),
                        ledger,
                        modelLifecycle,
                        profile);
            }
            return attentionEngine;
        }
    }

    /** Alias so Stream I's runtime lookup finds the engine. */
    public AttentionEngine attentionEngine() {
        return attention();
    }

    public void setAttentionEngine(AttentionEngine engine) {
        synchronized (engineLock) {
            attentionEngine = engine;
        }
    }

    /** Never waits for a model. {@link AttentionEngine#onEvent} only enqueues. */
    public void enqueueAttention(EventSignal signal) {
        if (signal == null) {
            return;
        }
        attention().onEvent(signal);
    }

    static EventSignal attentionSignal(StoredNotificationEvent event, EventSignal.Kind kind) {
        return new EventSignal(
                kind,
                event.getEventId(),
                event.getVersion(),
                PersonKeys.resolve(event.getSenderName(), event.getPackageName()),
                event.getPackageName());
    }

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

    private final class RepositoryEventStore implements EventStore {
        @Override
        public List<StoredEvent> getLiveForPerson(String personId) {
            List<StoredEvent> live = new ArrayList<>();
            for (StoredNotificationEvent event : notifications.getLiveEvents()) {
                StoredEvent mapped = mapEvent(event);
                if (personId == null || personId.isEmpty() || personId.equals(mapped.getPersonId())) {
                    live.add(mapped);
                }
            }
            return live;
        }

        @Override
        public StoredEvent getByEventId(String eventId) {
            StoredNotificationEvent event = notifications.getEvent(eventId);
            return event == null ? null : mapEvent(event);
        }

        private StoredEvent mapEvent(StoredNotificationEvent event) {
            return new StoredEvent(
                    event.getEventId(),
                    event.getVersion(),
                    PersonKeys.resolve(event.getSenderName(), event.getPackageName()),
                    event.getPackageName(),
                    event.getAppLabel() == null ? "" : event.getAppLabel(),
                    event.getSenderName() == null ? "" : event.getSenderName(),
                    event.getBody() == null ? "" : event.getBody(),
                    event.getPostedAt(),
                    event.getStatus());
        }
    }

}
