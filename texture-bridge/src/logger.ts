export type LogLevel = "debug" | "info" | "warn" | "error";

export interface Logger {
  log(level: LogLevel, event: string, details?: Record<string, unknown>): void;
}

export class StderrLogger implements Logger {
  log(level: LogLevel, event: string, details: Record<string, unknown> = {}): void {
    const entry = {
      timestamp: new Date().toISOString(),
      level,
      event,
      ...details
    };
    console.error(JSON.stringify(entry));
  }
}

export const silentLogger: Logger = {
  log: () => undefined
};
