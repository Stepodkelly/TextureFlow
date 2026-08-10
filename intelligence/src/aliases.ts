import type {
  IdentityResolution,
  NotificationEvent,
  PersonAliasSeed,
} from "./types.js";

export const DEFAULT_PERSON_ALIASES: readonly PersonAliasSeed[] = [
  {
    personId: "person_sam",
    displayName: "Sam",
    importance: 1,
    relationship: "close contact",
    aliases: [
      { value: "Sam" },
      { value: "Sam K", packageName: "com.whatsapp" },
      { value: "@samk", packageName: "org.telegram.messenger" },
    ],
  },
  {
    personId: "person_maya",
    displayName: "Maya",
    importance: 0.85,
    relationship: "important contact",
    aliases: [
      { value: "Maya" },
      { value: "Maya K.", packageName: "com.whatsapp" },
      { value: "@mayak", packageName: "org.telegram.messenger" },
    ],
  },
];

export function normalizeAlias(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/^@/, "")
    .replace(/[^a-z0-9+]+/g, "");
}

function stableHash(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(36);
}

export class PersonAliasResolver {
  private readonly seeds: readonly PersonAliasSeed[];

  constructor(seeds: readonly PersonAliasSeed[] = DEFAULT_PERSON_ALIASES) {
    this.seeds = seeds.map((seed) => ({
      ...seed,
      importance: clamp(seed.importance),
      aliases: [...seed.aliases],
    }));
  }

  resolveEvent(event: NotificationEvent): IdentityResolution {
    const explicit = event.sender.personId
      ? this.seeds.filter((seed) => seed.personId === event.sender.personId)
      : [];
    if (explicit.length === 1 && explicit[0]) {
      return resolved(explicit[0], event.sender.displayName);
    }

    return this.resolve(event.sender.displayName, event.app.packageName);
  }

  resolveMention(value: string): IdentityResolution {
    return this.resolve(value);
  }

  private resolve(value: string, packageName?: string): IdentityResolution {
    const normalized = normalizeAlias(value);
    const appSpecific = this.matchingSeeds(normalized, packageName, true);
    const matches = appSpecific.length > 0
      ? appSpecific
      : this.matchingSeeds(normalized, packageName, false);

    if (matches.length === 1 && matches[0]) {
      return resolved(matches[0], value);
    }

    if (matches.length > 1) {
      return {
        kind: "AMBIGUOUS",
        query: value,
        candidates: matches
          .map(({ personId, displayName }) => ({ personId, displayName }))
          .sort((left, right) => left.personId.localeCompare(right.personId)),
      };
    }

    return {
      kind: "PROVISIONAL",
      personId: `person_provisional_${stableHash(`${packageName ?? "any"}:${normalized}`)}`,
      displayName: value.trim() || "Unknown sender",
      importance: 0.5,
      matchedAlias: value,
    };
  }

  private matchingSeeds(
    normalized: string,
    packageName: string | undefined,
    appSpecificOnly: boolean,
  ): PersonAliasSeed[] {
    return this.seeds.filter((seed) => {
      if (normalizeAlias(seed.displayName) === normalized && !appSpecificOnly) {
        return true;
      }
      return seed.aliases.some((alias) => {
        if (normalizeAlias(alias.value) !== normalized) {
          return false;
        }
        if (appSpecificOnly) {
          return alias.packageName === packageName;
        }
        return alias.packageName === undefined || packageName === undefined;
      });
    });
  }
}

function resolved(seed: PersonAliasSeed, matchedAlias: string): IdentityResolution {
  return {
    kind: "RESOLVED",
    personId: seed.personId,
    displayName: seed.displayName,
    importance: seed.importance,
    ...(seed.relationship ? { relationship: seed.relationship } : {}),
    matchedAlias,
  };
}

function clamp(value: number): number {
  return Math.max(0, Math.min(1, value));
}
