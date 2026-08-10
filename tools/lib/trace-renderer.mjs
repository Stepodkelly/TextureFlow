const SERVICE_LABELS = {
  QA_HARNESS: "Harness",
  TEXTUREFLOW_BRIDGE: "Bridge",
  TEXTUREFLOW_CORE: "Core",
  ANDROID_MOBILE: "Android",
  TEXTURE_ENGINE: "Texture",
};

function pad(value, width) {
  return String(value).padEnd(width, " ");
}

function durationMs(first, last) {
  if (!first || !last) return 0;
  return Math.max(0, Date.parse(last.occurredAt) - Date.parse(first.occurredAt));
}

export function renderTrace(envelope) {
  const events = Array.isArray(envelope) ? envelope : envelope.traceEvents;
  if (!Array.isArray(events)) {
    throw new Error("Trace input must be an array or contain traceEvents[]");
  }

  const mode = Array.isArray(envelope) ? "UNVERIFIED_INPUT" : envelope.mode;
  const label =
    mode === "REHEARSAL"
      ? "REHEARSAL - NO LIVE ACTIONS OR RECEIPTS"
      : "UNVERIFIED TRACE INPUT - DO NOT CLAIM EXECUTION";
  const first = events[0];
  const lines = [
    "=".repeat(label.length + 4),
    `  ${label}`,
    "=".repeat(label.length + 4),
    `Trace: ${first?.traceId ?? envelope.traceId ?? "unknown"}`,
    "",
    `${pad("#", 5)} ${pad("+ms", 8)} ${pad("Service", 12)} ${pad("Outcome", 9)} Event`,
    `${"-".repeat(5)} ${"-".repeat(8)} ${"-".repeat(12)} ${"-".repeat(9)} ${"-".repeat(34)}`,
  ];

  for (const event of events) {
    const elapsed = first
      ? Math.max(0, Date.parse(event.occurredAt) - Date.parse(first.occurredAt))
      : 0;
    lines.push(
      `${pad(event.sequence ?? "?", 5)} ${pad(elapsed, 8)} ${pad(
        SERVICE_LABELS[event.service] ?? event.service ?? "unknown",
        12,
      )} ${pad(event.outcome ?? "?", 9)} ${event.name ?? "UNKNOWN_EVENT"}`,
    );
  }

  lines.push(
    "",
    `Elapsed: ${durationMs(first, events.at(-1))} ms`,
  );

  if (!Array.isArray(envelope) && envelope.terminal) {
    lines.push(
      `Terminal: ${envelope.terminal.status} (${envelope.terminal.code})`,
      `Device receipt observed: ${envelope.terminal.deviceReceiptObserved ? "yes" : "no"}`,
      envelope.terminal.message,
    );
  }
  return lines.join("\n");
}

