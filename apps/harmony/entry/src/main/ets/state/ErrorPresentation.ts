import { ClientError, ClientErrorTransportPayload, ClientErrorRpcPayload, ClientErrorInvalidParamsPayload,
  ClientErrorSerializationPayload, ClientErrorEventClosedPayload, ClientErrorMinigameGenerationFailedPayload } from '@agentbuddy/core';

// The generated Error.message contains only the variant name (e.g. Transport).
// Show the shared core's typed detail; do not parse or classify protocol strings.
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ClientError) {
    const payload = error.payload;
    if (payload instanceof ClientErrorTransportPayload || payload instanceof ClientErrorRpcPayload ||
      payload instanceof ClientErrorInvalidParamsPayload || payload instanceof ClientErrorSerializationPayload ||
      payload instanceof ClientErrorEventClosedPayload || payload instanceof ClientErrorMinigameGenerationFailedPayload) {
      return payload.value0 || fallback;
    }
  }
  return error instanceof Error && error.message ? error.message : fallback;
}
