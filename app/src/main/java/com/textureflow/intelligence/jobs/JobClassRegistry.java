package com.textureflow.intelligence.jobs;

import com.textureflow.intelligence.api.JobClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P6 stub: built-in local job classes only. The engine rejects unknown ids.
 */
public final class JobClassRegistry {
    public static final String TRIAGE = "triage";
    public static final String SUMMARY = "summary";
    public static final String DRAFT = "draft";

    private final Map<String, JobClass> jobs;

    public JobClassRegistry() {
        Map<String, JobClass> builtIn = new LinkedHashMap<>();
        builtIn.put(TRIAGE, new BuiltinJobClass(TRIAGE, 600, 128, 4_000L, "triage"));
        builtIn.put(SUMMARY, new BuiltinJobClass(SUMMARY, 600, 160, 3_000L, "summarizer"));
        builtIn.put(DRAFT, new BuiltinJobClass(DRAFT, 600, 160, 5_000L, "drafter"));
        this.jobs = Collections.unmodifiableMap(builtIn);
    }

    public boolean isRegistered(String id) {
        return id != null && jobs.containsKey(id);
    }

    public JobClass get(String id) {
        return jobs.get(id);
    }

    public JobClass require(String id) {
        JobClass job = get(id);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job class: " + id);
        }
        return job;
    }

    public Set<String> ids() {
        return jobs.keySet();
    }

    public Map<String, JobClass> snapshot() {
        return jobs;
    }

    public static void rejectUnknown(JobClass job) {
        Objects.requireNonNull(job, "job");
        if (job.allowDepth()) {
            throw new IllegalArgumentException("Depth jobs are not enabled: " + job.id());
        }
        new JobClassRegistry().require(job.id());
    }
}
