//! Host-reported turn completion notifications, mobile side
//! (`docs/superpowers/specs/2026-09-24-host-push-notifications-design.md`
//! §8.1).
//!
//! When a turn starts on an alleycat host that advertises `push.v1` for the
//! thread's agent, the phone sends the host a device-signed grant
//! (`push_subscribe`); the host observes the authoritative terminal state and
//! asks the Worker to push a visible notification. Platforms only hand Rust
//! their push token (`AppClient.set_push_registration`) and ask
//! `AppClient.turn_push_state` whether a local completion notification is
//! still needed. The token itself never reaches the host: it is sealed to
//! the Worker's key (§5.5) and the host forwards the opaque blob.

mod backend;
pub(crate) mod manager;
pub(crate) mod seal;
pub(crate) mod signing;

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::broadcast;

pub(crate) use backend::IrohPushBackend;
pub(crate) use manager::{ActiveTurn, HostPushRecord, PushManager};

use crate::alleycat::{AgentInfo, AlleycatHostInfo, ParsedPairPayload};
use crate::session::events::UiEvent;
use crate::store::{AppStoreReducer, ServerHealthSnapshot};
use crate::types::{AgentRuntimeKind, ThreadKey};

/// Push provider of the registered device token.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum AppPushPlatform {
    Ios,
    Android,
}

/// APNs environment of an iOS device token.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum AppApnsEnvironment {
    Sandbox,
    Production,
}

/// The platform's current push registration.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AppPushRegistration {
    pub platform: AppPushPlatform,
    /// APNs device token (hex) or FCM registration token.
    pub token: String,
    /// Required for iOS (defaults to `Production` when absent); ignored on
    /// Android.
    pub apns_environment: Option<AppApnsEnvironment>,
    /// Worker base URL, used only for the best-effort direct revoke when a
    /// host is unreachable.
    pub worker_base_url: String,
}

/// Whether the host will push a completion notification for a turn.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum AppTurnPushState {
    /// Remote push does not apply: local / non-alleycat server, no push
    /// registration on this device, or no subscription for this turn.
    NotApplicable,
    /// The host cannot report this turn (legacy host without `push.v1`, or
    /// the agent is not observable in the host's run mode).
    Unsupported,
    /// A subscription is being sent to the host (or will be on background).
    Pending,
    /// The host accepted the subscription and will report the terminal state.
    Subscribed,
    /// The last subscribe attempt failed; retried when the app enters background.
    Failed,
}

/// Whether a server's host can report turn completions at all (`push.v1`),
/// independent of agent and push registration. Platforms surface
/// `UnsupportedHost` as "update the desktop app" instead of falling back to
/// a silent keep-alive (host push design §9).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum AppHostPushSupport {
    /// Remote push does not apply: local or non-alleycat server.
    NotApplicable,
    /// Alleycat server whose host capability is not known yet (not listed
    /// since launch, or disconnected).
    Unknown,
    /// The host advertises `push.v1` with push enabled.
    Supported,
    /// The host lacks `push.v1` (older desktop app) or has push disabled.
    UnsupportedHost,
}

/// Poll fallback for `await_server_connected` in case a store update is
/// missed (lagged receiver).
const SERVER_CONNECTED_POLL_INTERVAL: Duration = Duration::from_millis(250);

/// Runtime kind → alleycat agent name for every listed agent.
pub(crate) fn runtime_agent_names(agents: &[AgentInfo]) -> HashMap<AgentRuntimeKind, String> {
    let mut map = HashMap::new();
    for agent in agents {
        if let Some(kind) = crate::alleycat::agent_runtime_kind(&agent.name, &agent.display_name) {
            map.entry(kind).or_insert_with(|| agent.name.clone());
        }
    }
    map
}

fn active_turns(app_store: &AppStoreReducer) -> Vec<ActiveTurn> {
    app_store
        .active_turns()
        .into_iter()
        .map(|(key, turn_id, runtime_kind)| ActiveTurn {
            key,
            turn_id,
            runtime_kind,
        })
        .collect()
}

impl PushManager {
    /// Store-listener hook: subscribe on `TurnStarted`, mark done on
    /// `TurnCompleted`. Runs after the event was applied to the store.
    pub(crate) fn observe_ui_event(self: &Arc<Self>, app_store: &AppStoreReducer, event: &UiEvent) {
        match event {
            UiEvent::TurnStarted { key, turn_id } => {
                // Without the thread we cannot tell its agent; the background
                // sweep picks the turn up once the store knows it.
                let Some((runtime_kind, _)) = app_store.thread_push_context(key) else {
                    return;
                };
                let jobs = self.turn_started(ActiveTurn {
                    key: key.clone(),
                    turn_id: turn_id.clone(),
                    runtime_kind,
                });
                self.spawn_jobs(jobs);
            }
            UiEvent::TurnCompleted { key, turn_id, .. } => self.turn_completed(key, turn_id),
            _ => {}
        }
    }
}

impl crate::MobileClient {
    /// Platform push registration changed (`None`: no token, notification
    /// permission denied, or signed out).
    pub(crate) fn set_push_registration(&self, registration: Option<AppPushRegistration>) {
        let jobs = self
            .push_manager
            .set_registration(registration, active_turns(&self.app_store));
        self.push_manager.spawn_jobs(jobs);
    }

    /// See [`AppTurnPushState`]. `turn_id = None` means the thread's active turn.
    pub(crate) fn turn_push_state(
        &self,
        key: &ThreadKey,
        turn_id: Option<&str>,
    ) -> AppTurnPushState {
        if matches!(
            self.app_store.server_health_and_locality(&key.server_id),
            Some((_, true))
        ) {
            return AppTurnPushState::NotApplicable;
        }
        let (runtime_kind, active_turn_id) = self
            .app_store
            .thread_push_context(key)
            .unwrap_or_else(|| ("codex".to_string(), None));
        self.push_manager
            .turn_push_state(key, turn_id, active_turn_id.as_deref(), &runtime_kind)
    }

    /// See [`AppHostPushSupport`].
    pub(crate) fn host_push_support(&self, server_id: &str) -> AppHostPushSupport {
        if matches!(
            self.app_store.server_health_and_locality(server_id),
            Some((_, true))
        ) {
            return AppHostPushSupport::NotApplicable;
        }
        self.push_manager.host_push_support(server_id)
    }

    /// Lifecycle hook: retry failed subscriptions of active turns.
    pub(crate) fn push_on_app_background(&self) {
        let jobs = self
            .push_manager
            .app_entered_background(active_turns(&self.app_store));
        self.push_manager.spawn_jobs(jobs);
    }

    /// Record the host capability block returned by `list_agents` for an
    /// alleycat server.
    pub(crate) fn record_alleycat_push_host(
        &self,
        server_id: &str,
        params: &ParsedPairPayload,
        host: Option<AlleycatHostInfo>,
        runtime_agents: HashMap<AgentRuntimeKind, String>,
    ) {
        self.push_manager.record_host(
            server_id,
            HostPushRecord {
                params: params.clone(),
                host,
                runtime_agents,
            },
        );
    }

    /// User disconnected / unpaired `server_id`: revoke its subscriptions.
    pub(crate) fn push_on_server_removed(&self, server_id: &str) {
        let jobs = self.push_manager.server_removed(server_id);
        self.push_manager.spawn_jobs(jobs);
    }

    /// Resolve once `server_id` reports `Connected`, or `false` after `timeout`.
    pub(crate) async fn await_server_connected(&self, server_id: &str, timeout: Duration) -> bool {
        let is_connected = || {
            matches!(
                self.app_store.server_health_and_locality(server_id),
                Some((ServerHealthSnapshot::Connected, _))
            )
        };
        // Subscribe before the first check so a transition in between is seen.
        let mut updates = self.app_store.subscribe();
        if is_connected() {
            return true;
        }
        let deadline = tokio::time::Instant::now() + timeout;
        loop {
            let now = tokio::time::Instant::now();
            if now >= deadline {
                return is_connected();
            }
            let wait = (deadline - now).min(SERVER_CONNECTED_POLL_INTERVAL);
            if let Ok(Err(broadcast::error::RecvError::Closed)) =
                tokio::time::timeout(wait, updates.recv()).await
            {
                tokio::time::sleep(wait).await;
            }
            if is_connected() {
                return true;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::alleycat::{AgentWire, AlleycatHostPush};
    use crate::session::connection::ServerConfig;

    fn server_config(server_id: &str, is_local: bool) -> ServerConfig {
        ServerConfig {
            server_id: server_id.to_string(),
            display_name: server_id.to_string(),
            host: "127.0.0.1".to_string(),
            port: 0,
            websocket_url: None,
            is_local,
            tls: false,
        }
    }

    fn agent(name: &str, display_name: &str) -> AgentInfo {
        AgentInfo {
            name: name.to_string(),
            display_name: display_name.to_string(),
            wire: AgentWire::Jsonl,
            available: true,
            presentation: None,
            capabilities: None,
        }
    }

    #[test]
    fn runtime_agent_names_maps_aliases() {
        let map = runtime_agent_names(&[
            agent("codex", "Codex"),
            agent("claude-code", "Claude Code"),
            agent("pi.dev", "Pi"),
        ]);
        assert_eq!(map.get("codex").map(String::as_str), Some("codex"));
        assert_eq!(map.get("claude").map(String::as_str), Some("claude-code"));
        assert_eq!(map.get("pi").map(String::as_str), Some("pi.dev"));
    }

    #[test]
    fn local_server_turn_push_state_is_not_applicable() {
        let client = crate::MobileClient::new();
        client.app_store.upsert_server(
            &server_config("local", true),
            ServerHealthSnapshot::Connected,
        );
        let key = ThreadKey {
            server_id: "local".to_string(),
            thread_id: "t".to_string(),
        };
        assert_eq!(
            client.turn_push_state(&key, Some("u")),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            client.turn_push_state(&key, None),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            client.host_push_support("local"),
            AppHostPushSupport::NotApplicable
        );
    }

    #[test]
    fn alleycat_server_capability_drives_turn_push_state() {
        let client = crate::MobileClient::new();
        let node_id = signing::device_id(&iroh::SecretKey::from_bytes(&[1u8; 32]));
        let server_id = format!("alleycat:{node_id}");
        let params = ParsedPairPayload {
            version: 1,
            node_id,
            token: "pair".to_string(),
            relay: None,
            host_name: None,
        };
        client.app_store.upsert_server(
            &server_config(&server_id, false),
            ServerHealthSnapshot::Connected,
        );
        let key = ThreadKey {
            server_id: server_id.clone(),
            thread_id: "t".to_string(),
        };
        // Unknown to the push manager (never listed): not applicable.
        assert_eq!(
            client.turn_push_state(&key, None),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            client.host_push_support(&server_id),
            AppHostPushSupport::Unknown
        );

        client.record_alleycat_push_host(&server_id, &params, None, HashMap::new());
        assert_eq!(
            client.turn_push_state(&key, None),
            AppTurnPushState::Unsupported
        );
        assert_eq!(
            client.host_push_support(&server_id),
            AppHostPushSupport::UnsupportedHost
        );

        client.record_alleycat_push_host(
            &server_id,
            &params,
            Some(AlleycatHostInfo {
                features: vec!["push.v1".to_string()],
                push: Some(AlleycatHostPush {
                    enabled: true,
                    agents: vec!["codex".to_string()],
                }),
            }),
            HashMap::new(),
        );
        // Supported, but no registration yet.
        assert_eq!(
            client.turn_push_state(&key, Some("u")),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            client.host_push_support(&server_id),
            AppHostPushSupport::Supported
        );

        client.disconnect_server(&server_id);
        assert_eq!(
            client.turn_push_state(&key, None),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            client.host_push_support(&server_id),
            AppHostPushSupport::Unknown
        );
    }

    #[tokio::test]
    async fn await_server_connected_resolves_on_health_change() {
        let client = Arc::new(crate::MobileClient::new());
        client.app_store.upsert_server(
            &server_config("srv", false),
            ServerHealthSnapshot::Connecting,
        );
        let waiter = {
            let client = Arc::clone(&client);
            tokio::spawn(async move {
                client
                    .await_server_connected("srv", Duration::from_secs(5))
                    .await
            })
        };
        tokio::time::sleep(Duration::from_millis(20)).await;
        client
            .app_store
            .update_server_health("srv", ServerHealthSnapshot::Connected);
        assert!(waiter.await.expect("join"));
    }

    #[tokio::test]
    async fn await_server_connected_times_out() {
        let client = crate::MobileClient::new();
        client.app_store.upsert_server(
            &server_config("srv", false),
            ServerHealthSnapshot::Disconnected,
        );
        assert!(
            !client
                .await_server_connected("srv", Duration::from_millis(50))
                .await
        );
        assert!(
            !client
                .await_server_connected("missing", Duration::from_millis(10))
                .await
        );
        client
            .app_store
            .update_server_health("srv", ServerHealthSnapshot::Connected);
        assert!(
            client
                .await_server_connected("srv", Duration::from_millis(0))
                .await
        );
    }
}
