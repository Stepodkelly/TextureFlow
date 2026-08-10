/* eslint-disable */
/**
 * Generated `api` utility.
 *
 * THIS CODE IS AUTOMATICALLY GENERATED.
 *
 * To regenerate, run `npx convex dev`.
 * @module
 */

import type * as attention from "../attention.js";
import type * as commands from "../commands.js";
import type * as demoFixtures from "../demoFixtures.js";
import type * as devices from "../devices.js";
import type * as events from "../events.js";
import type * as lib_auth from "../lib/auth.js";
import type * as lib_data from "../lib/data.js";
import type * as lib_errors from "../lib/errors.js";
import type * as lib_state from "../lib/state.js";
import type * as lib_tracing from "../lib/tracing.js";
import type * as lib_validators from "../lib/validators.js";
import type * as people from "../people.js";
import type * as proposals from "../proposals.js";
import type * as receipts from "../receipts.js";
import type * as traces from "../traces.js";

import type {
  ApiFromModules,
  FilterApi,
  FunctionReference,
} from "convex/server";

declare const fullApi: ApiFromModules<{
  attention: typeof attention;
  commands: typeof commands;
  demoFixtures: typeof demoFixtures;
  devices: typeof devices;
  events: typeof events;
  "lib/auth": typeof lib_auth;
  "lib/data": typeof lib_data;
  "lib/errors": typeof lib_errors;
  "lib/state": typeof lib_state;
  "lib/tracing": typeof lib_tracing;
  "lib/validators": typeof lib_validators;
  people: typeof people;
  proposals: typeof proposals;
  receipts: typeof receipts;
  traces: typeof traces;
}>;

/**
 * A utility for referencing Convex functions in your app's public API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = api.myModule.myFunction;
 * ```
 */
export declare const api: FilterApi<
  typeof fullApi,
  FunctionReference<any, "public">
>;

/**
 * A utility for referencing Convex functions in your app's internal API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = internal.myModule.myFunction;
 * ```
 */
export declare const internal: FilterApi<
  typeof fullApi,
  FunctionReference<any, "internal">
>;

export declare const components: {};
