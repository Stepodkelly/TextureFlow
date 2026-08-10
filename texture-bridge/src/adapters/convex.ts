import { randomUUID } from "node:crypto";
import { ConvexHttpClient } from "convex/browser";
import { makeFunctionReference } from "convex/server";
import { z } from "zod";
import {
  ActionProposalSchema,
  ActionReceiptSchema,
  NotificationEventSchema,
  TextureCommandSchema,
  type ActionProposal,
  type NotificationEvent
} from "../contracts.js";
import { BridgeError } from "../errors.js";
import {
  BridgeStatusSchema,
  PersonContextSchema,
  type BridgeStatus,
  type ConfirmationResult,
  type PersonContext
} from "../types.js";
import type {
  PrepareActionInput,
  RequestContext,
  TextureBackend
} from "./backend.js";

export interface ConvexCaller {
  query(functionName: string, args: Record<string, unknown>): Promise<unknown>;
  mutation(functionName: string, args: Record<string, unknown>): Promise<unknown>;
}

export class HttpConvexCaller implements ConvexCaller {
  private readonly client: ConvexHttpClient;

  constructor(url: string, authToken?: string) {
    this.client = new ConvexHttpClient(url);
    if (authToken) {
      this.client.setAuth(authToken);
    }
  }

  async query(functionName: string, args: Record<string, unknown>): Promise<unknown> {
    const reference = makeFunctionReference<"query">(functionName);
    return this.client.query(reference, args);
  }

  async mutation(functionName: string, args: Record<string, unknown>): Promise<unknown> {
    const reference = makeFunctionReference<"mutation">(functionName);
    return this.client.mutation(reference, args);
  }
}

export const defaultConvexFunctions = {
  status: "devices:status",
  listAttention: "attention:list",
  readEvent: "events:get",
  personContext: "people:context",
  createProposal: "proposals:create",
  proposalDetails: "proposals:get",
  reviseProposal: "proposals:revise",
  cancelProposal: "proposals:cancel",
  confirmProposal: "proposals:confirm",
  receiptByCommand: "receipts:getByCommand"
} as const;

export type ConvexFunctionNames = typeof defaultConvexFunctions;

const CoreProposalSchema = ActionProposalSchema.extend({
  revision: z.number().int().positive()
});

const ProposalMutationResultSchema = z.object({
  proposal: CoreProposalSchema
});

const ProposalResultSchema = z.discriminatedUnion("ok", [
  z.object({ ok: z.literal(true), proposal: CoreProposalSchema }),
  z.object({
    ok: z.literal(false),
    error: z.object({ code: z.string().min(1), message: z.string().min(1) })
  })
]);

const ConfirmMutationResultSchema = z.discriminatedUnion("ok", [
  z.object({
    ok: z.literal(true),
    proposal: CoreProposalSchema,
    command: TextureCommandSchema,
    receipt: ActionReceiptSchema.nullish()
  }),
  z.object({
    ok: z.literal(false),
    error: z.object({ code: z.string().min(1), message: z.string().min(1) })
  })
]);

const CorePersonContextSchema = z.discriminatedUnion("ambiguous", [
  z.object({
    ambiguous: z.literal(true),
    candidates: z.array(
      z.object({
        personId: z.string().min(1),
        displayName: z.string().min(1),
        relationshipLabel: z.string().optional()
      })
    )
  }),
  z.object({
    ambiguous: z.literal(false),
    person: z.object({
      personId: z.string().min(1),
      displayName: z.string().min(1),
      relationshipLabel: z.string().optional()
    }),
    identities: z.array(
      z.object({
        packageName: z.string().min(1),
        handle: z.string().min(1)
      })
    ),
    recentEvents: z.array(z.object({ eventId: z.string().min(1) }))
  })
]);

export interface ConvexAdapterOptions {
  functions?: ConvexFunctionNames;
  bridgeToken?: string;
  receiptTimeoutMs?: number;
  receiptPollMs?: number;
  proposalTtlMs?: number;
}

export class ConvexAdapter implements TextureBackend {
  private readonly functions: ConvexFunctionNames;
  private readonly bridgeToken: string | undefined;
  private readonly receiptTimeoutMs: number;
  private readonly receiptPollMs: number;
  private readonly proposalTtlMs: number;

  constructor(
    private readonly caller: ConvexCaller,
    options: ConvexAdapterOptions = {}
  ) {
    this.functions = options.functions ?? defaultConvexFunctions;
    this.bridgeToken = options.bridgeToken;
    this.receiptTimeoutMs = options.receiptTimeoutMs ?? 15_000;
    this.receiptPollMs = options.receiptPollMs ?? 250;
    this.proposalTtlMs = options.proposalTtlMs ?? 90_000;
  }

  async getStatus(context: RequestContext): Promise<BridgeStatus> {
    const result = await this.caller.query(this.functions.status, {
      actor: this.actor(context.ownerId)
    });
    return BridgeStatusSchema.parse(result);
  }

  async listAttention(
    context: RequestContext,
    limit: number
  ): Promise<NotificationEvent[]> {
    const result = await this.caller.query(this.functions.listAttention, {
      actor: this.actor(context.ownerId),
      limit
    });
    return z.array(NotificationEventSchema).parse(result);
  }

  async readEvent(
    context: RequestContext,
    eventId: string
  ): Promise<NotificationEvent> {
    const result = await this.caller.query(this.functions.readEvent, {
      actor: this.actor(context.ownerId),
      eventId
    });
    return NotificationEventSchema.parse(result);
  }

  async messagesFrom(
    context: RequestContext,
    personName: string
  ): Promise<NotificationEvent[]> {
    return (await this.personContext(context, personName)).recentEvents;
  }

  async personContext(
    context: RequestContext,
    personName: string
  ): Promise<PersonContext> {
    const raw = await this.caller.query(this.functions.personContext, {
      actor: this.actor(context.ownerId),
      displayName: personName,
      eventLimit: 10
    });
    const result = CorePersonContextSchema.parse(raw);
    if (result.ambiguous) {
      throw new BridgeError(
        "AMBIGUOUS_PERSON",
        `More than one person matches ${personName}: ${result.candidates
          .map((candidate) => candidate.displayName)
          .join(", ")}.`
      );
    }

    const recentEvents = await Promise.all(
      result.recentEvents.map((event) => this.readEvent(context, event.eventId))
    );
    const appLabels = new Map(
      recentEvents.map((event) => [event.app.packageName, event.app.label])
    );
    return PersonContextSchema.parse({
      personId: result.person.personId,
      displayName: result.person.displayName,
      identities: result.identities.map((identity) => ({
        appLabel: appLabels.get(identity.packageName) ?? identity.packageName,
        handle: identity.handle
      })),
      summary: result.person.relationshipLabel
        ? `${result.person.relationshipLabel}; ${recentEvents.length} recent notification${recentEvents.length === 1 ? "" : "s"}.`
        : `${recentEvents.length} recent notification${recentEvents.length === 1 ? "" : "s"}.`,
      openRequests: [],
      recentEvents
    });
  }

  async prepareAction(input: PrepareActionInput): Promise<ActionProposal> {
    const event = await this.readEvent(input, input.eventId);
    const result = await this.caller.mutation(this.functions.createProposal, {
      actor: this.actor(input.ownerId),
      proposalId: `proposal_${randomUUID()}`,
      sessionId: input.sessionId,
      eventId: input.eventId,
      actionType: input.actionType,
      payload: input.payload,
      spokenPreview: this.spokenPreview(event, input.actionType, input.payload),
      expiresAt: this.proposalExpiry(),
      traceId: input.traceId
    });
    return ProposalMutationResultSchema.parse(result).proposal;
  }

  async reviseReply(
    context: RequestContext,
    proposalId: string,
    message: string
  ): Promise<ActionProposal> {
    const proposal = await this.proposal(context, proposalId);
    const event = await this.readEvent(context, proposal.eventId);
    const payload = { message };
    const raw = await this.caller.mutation(this.functions.reviseProposal, {
      actor: this.actor(context.ownerId),
      proposalId,
      expectedRevision: proposal.revision,
      payload,
      spokenPreview: this.spokenPreview(event, "REPLY", payload),
      expiresAt: this.proposalExpiry()
    });
    const result = ProposalResultSchema.parse(raw);
    if (!result.ok) {
      throw new BridgeError(result.error.code, result.error.message);
    }
    return result.proposal;
  }

  async cancelProposal(
    context: RequestContext,
    proposalId: string
  ): Promise<ActionProposal> {
    const result = await this.caller.mutation(this.functions.cancelProposal, {
      actor: this.actor(context.ownerId),
      proposalId
    });
    return ProposalMutationResultSchema.parse(result).proposal;
  }

  async confirmProposal(
    context: RequestContext,
    proposalId: string
  ): Promise<ConfirmationResult> {
    const proposal = await this.proposal(context, proposalId);
    const event = await this.readEvent(context, proposal.eventId);
    const raw = await this.caller.mutation(this.functions.confirmProposal, {
      actor: this.actor(context.ownerId),
      proposalId,
      sessionId: context.sessionId,
      expectedRevision: proposal.revision
    });
    const confirmed = ConfirmMutationResultSchema.parse(raw);
    if (!confirmed.ok) {
      throw new BridgeError(confirmed.error.code, confirmed.error.message);
    }
    const result: ConfirmationResult = {
      proposal: confirmed.proposal,
      command: confirmed.command,
      event,
      ...(confirmed.receipt ? { receipt: confirmed.receipt } : {})
    };
    if (result.receipt) {
      return result;
    }

    const deadline = Date.now() + this.receiptTimeoutMs;
    while (Date.now() < deadline) {
      const receiptRaw = await this.caller.query(this.functions.receiptByCommand, {
        actor: this.actor(context.ownerId),
        commandId: result.command.commandId
      });
      if (receiptRaw !== null && receiptRaw !== undefined) {
        return {
          ...result,
          receipt: ActionReceiptSchema.parse(receiptRaw)
        };
      }
      await new Promise((resolve) => setTimeout(resolve, this.receiptPollMs));
    }

    return result;
  }

  private async proposal(
    context: RequestContext,
    proposalId: string
  ): Promise<z.infer<typeof CoreProposalSchema>> {
    const result = await this.caller.query(this.functions.proposalDetails, {
      actor: this.actor(context.ownerId),
      proposalId
    });
    return CoreProposalSchema.parse(result);
  }

  private actor(ownerId: string): Record<string, unknown> {
    return {
      ownerId,
      role: "BRIDGE",
      ...(this.bridgeToken ? { token: this.bridgeToken } : {})
    };
  }

  private proposalExpiry(): string {
    return new Date(Date.now() + this.proposalTtlMs).toISOString();
  }

  private spokenPreview(
    event: NotificationEvent,
    actionType: ActionProposal["actionType"],
    payload: Record<string, string | number | boolean>
  ): string {
    if (actionType === "REPLY") {
      return `Reply to ${event.sender.displayName} on ${event.app.label}: ${String(payload.message ?? "")}`;
    }
    if (actionType === "SNOOZE") {
      return `Snooze ${event.sender.displayName}'s notification on ${event.app.label} for ${String(payload.minutes ?? "")} minutes.`;
    }
    return `Dismiss ${event.sender.displayName}'s notification on ${event.app.label}.`;
  }
}

export function describeConvexError(error: unknown): BridgeError {
  if (error instanceof BridgeError) {
    return error;
  }
  return new BridgeError(
    "NETWORK_ERROR",
    error instanceof Error ? error.message : "TextureFlow Core is unavailable."
  );
}
