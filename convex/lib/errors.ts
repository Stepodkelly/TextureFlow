import { ConvexError } from "convex/values";

import { DomainInvariantError } from "./state";

export function fail(code: string, message: string): never {
  throw new ConvexError({ code, message });
}

export function rethrowDomain(error: unknown): never {
  if (error instanceof DomainInvariantError) {
    fail(error.code, error.message);
  }
  throw error;
}
