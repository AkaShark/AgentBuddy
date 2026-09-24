export interface RegisterRequest {
  platform: "ios" | "android"
  pushToken: string
  apnsEnvironment?: "production" | "sandbox"
  contentState?: ContentState
  intervalSeconds?: number
  ttlSeconds?: number
}

export interface ContentState {
  phase?: string
  elapsedSeconds?: number
  toolCallCount?: number
  activeThreadCount?: number
  serverId?: string
  threadId?: string
}

export interface Env {
  PUSH_REGISTRATION: DurableObjectNamespace
  RATE_LIMITER: DurableObjectNamespace
  HOST_CHANNEL: DurableObjectNamespace
  // Secrets
  APNS_TEAM_ID: string
  APNS_KEY_ID: string
  APNS_PRIVATE_KEY: string
  FCM_PROJECT_ID: string
  FCM_CLIENT_EMAIL: string
  FCM_PRIVATE_KEY: string
  DEBUG_PUSH_ADMIN_TOKEN?: string
  // Vars (wrangler.toml [vars])
  APNS_TOPIC?: string
  DEBUG_PUSH_ENABLED?: string
  LEGACY_KEEPALIVE_ENABLED?: string
  BLOCKED_HOST_IDS?: string
}
