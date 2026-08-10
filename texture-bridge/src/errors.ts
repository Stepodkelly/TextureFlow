import { ZodError } from "zod";

export class BridgeError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "BridgeError";
    this.code = code;
  }
}

export function normalizeError(error: unknown): BridgeError {
  if (error instanceof BridgeError) {
    return error;
  }

  if (error instanceof ZodError) {
    return new BridgeError(
      "INVALID_INPUT",
      error.issues.map((issue) => issue.message).join("; ")
    );
  }

  if (error instanceof Error) {
    return new BridgeError("BRIDGE_ERROR", error.message);
  }

  return new BridgeError("BRIDGE_ERROR", "TextureFlow could not complete the request.");
}
