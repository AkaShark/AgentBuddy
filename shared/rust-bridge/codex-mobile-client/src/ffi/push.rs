//! UniFFI surface for host-reported turn completion notifications
//! (host push design §8.1). Kept out of `client.rs` so the push surface
//! stays in one place.

use std::sync::Arc;
use std::time::Duration;

use super::client::AppClient;
use crate::push::{AppHostPushSupport, AppPushRegistration, AppTurnPushState};
use crate::types::ThreadKey;

#[uniffi::export(async_runtime = "tokio")]
impl AppClient {
    /// Hand Rust the platform push registration. Pass `None` when there is
    /// no token, notification permission is denied, or the user signed
    /// out: Rust then revokes this device's subscriptions on every host.
    /// Call again whenever the token changes; active turns are re-subscribed
    /// with the new token.
    pub fn set_push_registration(&self, registration: Option<AppPushRegistration>) {
        self.inner.set_push_registration(registration);
    }

    /// Whether the host will push a completion notification for `turn_id`
    /// on `key` (`None` = the thread's active turn). Platforms post a local
    /// completion notification only when this is not `Subscribed`, and can
    /// surface `Unsupported` as "update the desktop app to get completion
    /// notifications".
    pub fn turn_push_state(&self, key: ThreadKey, turn_id: Option<String>) -> AppTurnPushState {
        self.inner.turn_push_state(&key, turn_id.as_deref())
    }

    /// Whether `server_id`'s host can report turn completions at all.
    /// Platforms show "update the desktop app to get completion
    /// notifications" for `UnsupportedHost` (host push design §9).
    pub fn host_push_support(&self, server_id: String) -> AppHostPushSupport {
        self.inner.host_push_support(&server_id)
    }

    /// Resolve `true` once `server_id` is connected, or `false` after
    /// `timeout_ms`. Used when a notification tap cold-starts the app.
    pub async fn await_server_connected(&self, server_id: String, timeout_ms: u64) -> bool {
        let inner = Arc::clone(&self.inner);
        self.rt
            .spawn(async move {
                inner
                    .await_server_connected(&server_id, Duration::from_millis(timeout_ms))
                    .await
            })
            .await
            .unwrap_or(false)
    }
}
