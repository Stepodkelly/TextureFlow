import type { NotificationEvent } from "../contracts.js";

export function buildFixtureEvents(now: Date): NotificationEvent[] {
  const minutesAgo = (minutes: number) =>
    new Date(now.getTime() - minutes * 60_000).toISOString();

  return [
    {
      contractVersion: 1,
      eventId: "evt_fixture_sam_whatsapp",
      deviceId: "pixel_fixture",
      app: {
        packageName: "com.whatsapp",
        label: "WhatsApp"
      },
      sender: {
        displayName: "Sam",
        personId: "person_sam"
      },
      conversationLabel: "Sam",
      body: "I'm downstairs. The door is locked.",
      postedAt: minutesAgo(2),
      updatedAt: minutesAgo(2),
      version: 2,
      status: "ACTIVE",
      capabilities: ["REPLY", "DISMISS", "SNOOZE"],
      priority: {
        score: 0.94,
        level: "URGENT",
        reason: "A close contact is waiting outside."
      }
    },
    {
      contractVersion: 1,
      eventId: "evt_fixture_maya_telegram",
      deviceId: "pixel_fixture",
      app: {
        packageName: "org.telegram.messenger",
        label: "Telegram"
      },
      sender: {
        displayName: "Maya",
        personId: "person_maya"
      },
      conversationLabel: "Dinner plans",
      body: "Are we still meeting at nine?",
      postedAt: minutesAgo(8),
      updatedAt: minutesAgo(8),
      version: 1,
      status: "ACTIVE",
      capabilities: ["REPLY", "DISMISS"],
      priority: {
        score: 0.73,
        level: "IMPORTANT",
        reason: "Maya asked a direct question about tonight."
      }
    },
    {
      contractVersion: 1,
      eventId: "evt_fixture_calendar",
      deviceId: "pixel_fixture",
      app: {
        packageName: "com.google.android.calendar",
        label: "Calendar"
      },
      sender: {
        displayName: "Calendar"
      },
      conversationLabel: "VoiceOS demo",
      body: "VoiceOS demo begins in thirty minutes.",
      postedAt: minutesAgo(12),
      updatedAt: minutesAgo(12),
      version: 1,
      status: "ACTIVE",
      capabilities: ["DISMISS", "SNOOZE"],
      priority: {
        score: 0.61,
        level: "IMPORTANT",
        reason: "A scheduled event is approaching."
      }
    }
  ];
}
