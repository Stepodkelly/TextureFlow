import type {
  ActionProposal,
  ActionReceipt,
  NotificationEvent
} from "./contracts.js";
import type { BridgeStatus, PersonContext } from "./types.js";

function body(event: NotificationEvent): string {
  return event.body?.trim() || "No message preview is available.";
}

export function formatStatus(status: BridgeStatus): string {
  const mode = status.mode === "REHEARSAL" ? "Rehearsal mode." : "Live mode.";
  const device = status.device.online && !status.device.stale
    ? `${status.device.label} is online.`
    : `${status.device.label} is unavailable or stale.`;
  return `${mode} ${device} ${status.activeEventCount} active attention item${status.activeEventCount === 1 ? "" : "s"}.`;
}

export function formatAttention(events: NotificationEvent[]): string {
  if (events.length === 0) {
    return "Nothing currently needs your attention.";
  }
  const summaries = events.map((event, index) => {
    const urgency = event.priority.level === "URGENT" ? "Urgent: " : "";
    return `${index + 1}. ${urgency}${event.sender.displayName} on ${event.app.label}: ${body(event)}`;
  });
  return `${events.length} item${events.length === 1 ? "" : "s"} need attention. ${summaries.join(" ")}`;
}

export function formatEvent(event: NotificationEvent): string {
  return `${event.sender.displayName} on ${event.app.label} said: ${body(event)} ${event.priority.reason}`;
}

export function formatMessages(personName: string, events: NotificationEvent[]): string {
  if (events.length === 0) {
    return `There are no active messages from ${personName}.`;
  }
  const messages = events.map((event) => `${event.app.label}: ${body(event)}`).join(" ");
  return `${events.length} active message${events.length === 1 ? "" : "s"} from ${personName}. ${messages}`;
}

export function formatPersonContext(context: PersonContext): string {
  const request = context.openRequests[0];
  return request
    ? `${context.summary} The current request is: ${request}`
    : context.summary;
}

export function formatProposal(proposal: ActionProposal): string {
  return `${proposal.spokenPreview} Say confirm to authorize this action, or cancel.`;
}

export function formatCancellation(proposal: ActionProposal): string {
  return `Cancelled the ${proposal.actionType.toLowerCase()} proposal. Nothing was executed.`;
}

export function formatReceipt(
  event: NotificationEvent,
  proposal: ActionProposal,
  receipt: ActionReceipt
): string {
  if (receipt.status === "DISPATCHED") {
    if (proposal.actionType === "REPLY") {
      return `The reply to ${event.sender.displayName} was dispatched through ${event.app.label}.`;
    }
    return `The ${proposal.actionType.toLowerCase()} action was dispatched on ${event.app.label}.`;
  }
  return `The ${proposal.actionType.toLowerCase()} action failed on the phone. ${receipt.message}`;
}
