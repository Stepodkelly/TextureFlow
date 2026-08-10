package com.textureflow.texture;

/** Canonical semantic cues shared with shared/contracts/domain.ts. */
public enum TextureCue {
    LISTENING_STARTED(Priority.NORMAL, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 260),
    CONTENT_MOVEMENT(Priority.LOW, SpeechPolicy.SUPPRESS_UNDER_SPEECH, RepeatPolicy.RATE_LIMITED, 110),
    FOCUS_ENTERED(Priority.LOW, SpeechPolicy.SUPPRESS_UNDER_SPEECH, RepeatPolicy.RATE_LIMITED, 100),
    ATTENTION_URGENT(Priority.HIGH, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 300),
    PROPOSAL_READY(Priority.NORMAL, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 320),
    CONFIRMATION_REQUIRED(Priority.HIGH, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 380),
    EXECUTION_STARTED(Priority.HIGH, SpeechPolicy.SUPPRESS_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 260),
    ACTION_DISPATCHED(Priority.CRITICAL, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 420),
    ACTION_FAILED(Priority.CRITICAL, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 460),
    CANCELLED(Priority.HIGH, SpeechPolicy.DUCK_UNDER_SPEECH, RepeatPolicy.ONCE_PER_CORRELATION, 300);

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    public enum SpeechPolicy {
        ALLOW,
        DUCK_UNDER_SPEECH,
        SUPPRESS_UNDER_SPEECH
    }

    public enum RepeatPolicy {
        ONCE,
        ONCE_PER_CORRELATION,
        RATE_LIMITED
    }

    private final Priority priority;
    private final SpeechPolicy speechPolicy;
    private final RepeatPolicy repeatPolicy;
    private final long durationMs;

    TextureCue(Priority priority, SpeechPolicy speechPolicy, RepeatPolicy repeatPolicy, long durationMs) {
        this.priority = priority;
        this.speechPolicy = speechPolicy;
        this.repeatPolicy = repeatPolicy;
        this.durationMs = durationMs;
    }

    public Priority priority() {
        return priority;
    }

    public SpeechPolicy speechPolicy() {
        return speechPolicy;
    }

    public RepeatPolicy repeatPolicy() {
        return repeatPolicy;
    }

    public long durationMs() {
        return durationMs;
    }
}
