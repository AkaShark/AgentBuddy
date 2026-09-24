//! `PushManager`: decides when to subscribe a turn for host-reported
//! completion pushes, drives the alleycat `push_subscribe` /
//! `push_unsubscribe` ops, and answers `turn_push_state` (host push design
//! §8.1).
//!
//! State changes are synchronous and return a list of [`PushJob`]s; the jobs
//! are then executed asynchronously (network I/O never runs under the state
//! lock). Jobs for the same server run serially behind a per-server async
//! lock so an unsubscribe and the follow-up subscribe reach the host in
//! order. Every subscribe attempt carries an attempt number, and results are
//! only applied when the entry still expects that attempt, so stale in-flight
//! work can never overwrite newer state.
//!
//! Contract v2 (design §5): the push token only travels inside a target
//! sealed to the Worker (§5.5); grants and revokes carry `aud`; ids are
//! checked against the §13 rules before anything is signed.

use std::collections::HashMap;
use std::str::FromStr;
use std::sync::{Arc, Mutex as StdMutex, MutexGuard};

use async_trait::async_trait;
use iroh::{EndpointId, SecretKey};
use serde::Serialize;
use tracing::{debug, info, warn};

use super::seal::{SealError, SealTarget};
use super::signing::{self, GrantFields};
use super::{AppApnsEnvironment, AppPushPlatform, AppPushRegistration, AppTurnPushState};
use crate::alleycat::{
    AlleycatHostInfo, AlleycatPushError, ParsedPairPayload, PushSubscribeArgs, PushSubscribeGrant,
    PushSubscribeOutcome, PushSubscribeTarget, PushUnsubscribeScope,
};
use crate::types::{AgentRuntimeKind, ThreadKey};

/// Grant lifetime: `issued = now`, `expires = now + 24h` (the Worker caps
/// grants at 48h).
pub(crate) const GRANT_LIFETIME_SECS: u64 = 24 * 60 * 60;
/// How long a completed turn keeps answering `turn_push_state` with its
/// final subscription state, so platforms can still decide whether to post
/// a local completion notification after `TurnCompleted`.
pub(crate) const COMPLETED_RETENTION_SECS: u64 = 15 * 60;
/// §13: at most this many live subscriptions per host; further turns on
/// that host are reported as `Unsupported` and never sent.
pub(crate) const MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER: usize = 64;

/// Injectable side effects for [`PushManager`]: device key, clock, nonces,
/// target sealing, the alleycat push ops and the direct Worker revoke.
#[async_trait]
pub(crate) trait PushBackend: Send + Sync {
    /// The device iroh key (the alleycat endpoint's key), if known.
    fn device_secret_key(&self) -> Option<SecretKey>;
    fn now_unix_secs(&self) -> u64;
    /// Fresh 32-hex-character CSPRNG nonce.
    fn new_nonce(&self) -> String;
    /// Seal the push target to the Worker key (design §5.5).
    fn seal_target(&self, target: &SealTarget<'_>) -> Result<String, SealError>;
    async fn subscribe(
        &self,
        host: &ParsedPairPayload,
        args: PushSubscribeArgs,
    ) -> Result<PushSubscribeOutcome, AlleycatPushError>;
    async fn unsubscribe(
        &self,
        host: &ParsedPairPayload,
        scope: PushUnsubscribeScope,
    ) -> Result<(), AlleycatPushError>;
    /// `POST {worker_base_url}/v2/subscriptions/revoke` (design §7.1).
    async fn revoke_direct(
        &self,
        worker_base_url: &str,
        request: DirectRevokeRequest,
    ) -> Result<(), String>;
}

/// Body of the device-signed Worker revoke (design §7.1).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DirectRevokeRequest {
    pub host_id: String,
    pub device_id: String,
    pub scope: String,
    pub timestamp: u64,
    pub nonce: String,
    pub signature: String,
}

/// A turn the store currently considers active.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ActiveTurn {
    pub key: ThreadKey,
    pub turn_id: String,
    pub runtime_kind: AgentRuntimeKind,
}

/// What the manager knows about one alleycat host (per server id).
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct HostPushRecord {
    /// Connection parameters for the one-shot push ops.
    pub params: ParsedPairPayload,
    /// `host` block from `list_agents`; `None` for legacy hosts.
    pub host: Option<AlleycatHostInfo>,
    /// Runtime kind → alleycat agent name, from `list_agents`.
    pub runtime_agents: HashMap<AgentRuntimeKind, String>,
}

impl HostPushRecord {
    fn supports_push(&self) -> bool {
        self.host
            .as_ref()
            .is_some_and(AlleycatHostInfo::supports_push_v1)
    }

    /// The alleycat agent name to subscribe for `runtime_kind`, if the host
    /// can observe that agent's terminal state.
    fn push_agent_for_runtime(&self, runtime_kind: &str) -> Option<String> {
        let agents = self.host.as_ref()?.push_agents();
        let mapped = self
            .runtime_agents
            .get(runtime_kind)
            .map(String::as_str)
            .unwrap_or(runtime_kind);
        agents
            .iter()
            .find(|agent| agent.as_str() == mapped)
            .or_else(|| {
                agents.iter().find(|agent| {
                    crate::alleycat::agent_runtime_kind(agent, agent).as_deref()
                        == Some(runtime_kind)
                })
            })
            .cloned()
    }
}

/// Lowercase hex host id for grants and revokes.
fn normalized_host_id(params: &ParsedPairPayload) -> String {
    EndpointId::from_str(params.node_id.trim())
        .map(|id| id.to_string())
        .unwrap_or_else(|_| params.node_id.trim().to_ascii_lowercase())
}

/// Host id from an `alleycat:<64 hex>` server id, for revoking a host this
/// launch has no record of (§13, cold start).
fn host_id_from_server_id(server_id: &str) -> Option<String> {
    let hex = server_id.strip_prefix("alleycat:")?;
    (hex.len() == 64 && hex.bytes().all(|byte| byte.is_ascii_hexdigit()))
        .then(|| hex.to_ascii_lowercase())
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(crate) struct TurnKey {
    pub server_id: String,
    pub thread_id: String,
    pub turn_id: String,
}

impl TurnKey {
    fn new(key: &ThreadKey, turn_id: &str) -> Self {
        Self {
            server_id: key.server_id.clone(),
            thread_id: key.thread_id.clone(),
            turn_id: turn_id.to_string(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum EntryStatus {
    /// A subscribe attempt is planned or in flight.
    Pending,
    /// The host accepted the subscription.
    Subscribed,
    /// The last attempt failed; retried when the app enters background.
    Failed,
    /// The host refused the op as unsupported; never retried.
    Unsupported,
}

impl EntryStatus {
    fn as_push_state(self) -> AppTurnPushState {
        match self {
            Self::Pending => AppTurnPushState::Pending,
            Self::Subscribed => AppTurnPushState::Subscribed,
            Self::Failed => AppTurnPushState::Failed,
            Self::Unsupported => AppTurnPushState::Unsupported,
        }
    }
}

#[derive(Debug, Clone)]
struct TurnEntry {
    agent: String,
    status: EntryStatus,
    attempt: u64,
    /// When the current attempt was planned (≈ its grant's `issued`); the
    /// entry is dropped once that grant would have expired.
    planned_at: u64,
    completed_at: Option<u64>,
}

/// Planned network work, executed by [`PushManager::run_jobs`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum PushJob {
    Subscribe {
        turn: TurnKey,
        attempt: u64,
        agent: String,
        host: ParsedPairPayload,
    },
    UnsubscribeTurn {
        server_id: String,
        host: ParsedPairPayload,
        agent: String,
        thread_id: String,
        turn_id: String,
    },
    /// `push_unsubscribe {all: true}`, falling back to the device-signed
    /// Worker revoke when the host op fails.
    UnsubscribeAll {
        server_id: String,
        host: ParsedPairPayload,
        worker_base_url: Option<String>,
    },
    /// Device-signed Worker revoke only: the host is unknown this launch
    /// (unpaired after a cold start), so there is no way to reach it.
    RevokeDirect {
        server_id: String,
        host_id: String,
        worker_base_url: String,
    },
}

impl PushJob {
    fn server_id(&self) -> &str {
        match self {
            Self::Subscribe { turn, .. } => &turn.server_id,
            Self::UnsubscribeTurn { server_id, .. }
            | Self::UnsubscribeAll { server_id, .. }
            | Self::RevokeDirect { server_id, .. } => server_id,
        }
    }
}

enum Eligibility {
    /// Not an alleycat host (local server, SSH, ...): remote push does not apply.
    NotApplicable,
    /// Alleycat host without `push.v1`, or the agent is not observable.
    Unsupported,
    /// Eligible host/agent, but the platform has no push registration.
    NoRegistration,
    Eligible {
        agent: String,
        host: ParsedPairPayload,
    },
}

#[derive(Default)]
struct PushState {
    registration: Option<AppPushRegistration>,
    last_worker_base_url: Option<String>,
    hosts: HashMap<String, HostPushRecord>,
    turns: HashMap<TurnKey, TurnEntry>,
    next_attempt: u64,
    /// Host id → unix second of this device's latest direct Worker revoke.
    /// The Worker rejects grants issued at or before that second (§5.3).
    revoked_at: HashMap<String, u64>,
}

impl PushState {
    fn eligibility(&self, server_id: &str, runtime_kind: &str) -> Eligibility {
        let Some(record) = self.hosts.get(server_id) else {
            return Eligibility::NotApplicable;
        };
        if !record.supports_push() {
            return Eligibility::Unsupported;
        }
        let Some(agent) = record.push_agent_for_runtime(runtime_kind) else {
            return Eligibility::Unsupported;
        };
        if self.registration.is_none() {
            return Eligibility::NoRegistration;
        }
        Eligibility::Eligible {
            agent,
            host: record.params.clone(),
        }
    }

    fn next_attempt(&mut self) -> u64 {
        self.next_attempt += 1;
        self.next_attempt
    }

    /// Subscriptions on `server_id` the host may still be holding for us.
    fn live_subscriptions(&self, server_id: &str) -> usize {
        self.turns
            .iter()
            .filter(|(key, entry)| {
                key.server_id == server_id
                    && entry.completed_at.is_none()
                    && entry.status != EntryStatus::Unsupported
            })
            .count()
    }

    /// Subscribe `turn` unless it already has an entry (dedupe by
    /// `(server, thread, turn)`) or is not eligible. Turns whose ids break
    /// the §13 rules, or beyond the per-server cap, are recorded as
    /// `Unsupported` without contacting the host.
    fn plan_subscribe(&mut self, turn: &ActiveTurn, now: u64) -> Option<PushJob> {
        let turn_key = TurnKey::new(&turn.key, &turn.turn_id);
        if self.turns.contains_key(&turn_key) {
            return None;
        }
        let Eligibility::Eligible { agent, host } =
            self.eligibility(&turn.key.server_id, &turn.runtime_kind)
        else {
            return None;
        };
        let refused = if !signing::is_valid_agent(&agent)
            || !signing::is_valid_ref_id(&turn_key.thread_id)
            || !signing::is_valid_ref_id(&turn_key.turn_id)
        {
            warn!(
                "PushManager: agent/thread/turn id breaks push id rules; not subscribing server_id={}",
                turn_key.server_id
            );
            true
        } else if self.live_subscriptions(&turn_key.server_id)
            >= MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER
        {
            warn!(
                "PushManager: {} live push subscriptions on server_id={}; not subscribing another turn",
                MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER, turn_key.server_id
            );
            true
        } else {
            false
        };
        let attempt = self.next_attempt();
        self.turns.insert(
            turn_key.clone(),
            TurnEntry {
                agent: agent.clone(),
                status: if refused {
                    EntryStatus::Unsupported
                } else {
                    EntryStatus::Pending
                },
                attempt,
                planned_at: now,
                completed_at: None,
            },
        );
        (!refused).then_some(PushJob::Subscribe {
            turn: turn_key,
            attempt,
            agent,
            host,
        })
    }

    fn prune(&mut self, now: u64) {
        self.turns.retain(|_, entry| {
            let expired = now.saturating_sub(entry.planned_at) >= GRANT_LIFETIME_SECS;
            let stale_completed = entry
                .completed_at
                .is_some_and(|at| now.saturating_sub(at) >= COMPLETED_RETENTION_SECS);
            !expired && !stale_completed
        });
        self.revoked_at.retain(|_, revoked| *revoked >= now);
    }

    /// `issued` for a new grant to `host_id`: never within the second of this
    /// device's own revoke (the Worker requires `issued` strictly later).
    fn grant_issued_at(&self, host_id: &str, now: u64) -> u64 {
        match self.revoked_at.get(host_id) {
            Some(&revoked) if revoked >= now => revoked + 1,
            _ => now,
        }
    }

    fn worker_base_url(&self) -> Option<String> {
        self.registration
            .as_ref()
            .map(|registration| registration.worker_base_url.clone())
            .or_else(|| self.last_worker_base_url.clone())
    }
}

fn same_push_identity(a: &AppPushRegistration, b: &AppPushRegistration) -> bool {
    a.platform == b.platform && a.token == b.token && a.apns_environment == b.apns_environment
}

/// Normalize a platform registration: trim fields, force the APNs
/// environment to `None` on Android, default iOS to `Production` when the
/// platform could not tell (App Store / TestFlight builds), and treat an
/// empty token as "no registration".
pub(crate) fn normalize_registration(
    registration: Option<AppPushRegistration>,
) -> Option<AppPushRegistration> {
    let registration = registration?;
    let token = registration.token.trim().to_string();
    if token.is_empty() {
        return None;
    }
    let apns_environment = match registration.platform {
        AppPushPlatform::Android => None,
        AppPushPlatform::Ios => Some(
            registration
                .apns_environment
                .unwrap_or(AppApnsEnvironment::Production),
        ),
    };
    Some(AppPushRegistration {
        platform: registration.platform,
        token,
        apns_environment,
        worker_base_url: registration
            .worker_base_url
            .trim()
            .trim_end_matches('/')
            .to_string(),
    })
}

pub(crate) struct PushManager {
    backend: Arc<dyn PushBackend>,
    state: StdMutex<PushState>,
    server_locks: StdMutex<HashMap<String, Arc<tokio::sync::Mutex<()>>>>,
}

impl PushManager {
    pub(crate) fn new(backend: Arc<dyn PushBackend>) -> Self {
        Self {
            backend,
            state: StdMutex::new(PushState::default()),
            server_locks: StdMutex::new(HashMap::new()),
        }
    }

    fn state(&self) -> MutexGuard<'_, PushState> {
        match self.state.lock() {
            Ok(guard) => guard,
            Err(error) => {
                warn!("PushManager: recovering poisoned state lock");
                error.into_inner()
            }
        }
    }

    fn server_lock(&self, server_id: &str) -> Arc<tokio::sync::Mutex<()>> {
        let mut locks = match self.server_locks.lock() {
            Ok(guard) => guard,
            Err(error) => error.into_inner(),
        };
        Arc::clone(
            locks
                .entry(server_id.to_string())
                .or_insert_with(|| Arc::new(tokio::sync::Mutex::new(()))),
        )
    }

    // ── State transitions (synchronous; return planned jobs) ─────────────

    /// Record (or refresh) an alleycat host's push capability for `server_id`.
    pub(crate) fn record_host(&self, server_id: &str, record: HostPushRecord) {
        debug!(
            "PushManager: host capability server_id={} push_v1={} agents={:?}",
            server_id,
            record.supports_push(),
            record
                .host
                .as_ref()
                .map(|host| host.push_agents().to_vec())
                .unwrap_or_default()
        );
        self.state().hosts.insert(server_id.to_string(), record);
    }

    /// `TurnStarted`: subscribe exactly once per `(server, thread, turn)`.
    pub(crate) fn turn_started(&self, turn: ActiveTurn) -> Vec<PushJob> {
        let now = self.backend.now_unix_secs();
        let mut state = self.state();
        state.prune(now);
        state.plan_subscribe(&turn, now).into_iter().collect()
    }

    /// `TurnCompleted`: the host reports the terminal state on its own; keep
    /// the entry briefly so `turn_push_state` still answers for this turn.
    pub(crate) fn turn_completed(&self, key: &ThreadKey, turn_id: &str) {
        let now = self.backend.now_unix_secs();
        let mut state = self.state();
        if let Some(entry) = state.turns.get_mut(&TurnKey::new(key, turn_id))
            && entry.completed_at.is_none()
        {
            entry.completed_at = Some(now);
        }
        state.prune(now);
    }

    /// App entered background: retry failed subscriptions of still-active
    /// turns and subscribe active turns whose `TurnStarted` was missed.
    pub(crate) fn app_entered_background(&self, active_turns: Vec<ActiveTurn>) -> Vec<PushJob> {
        let now = self.backend.now_unix_secs();
        let mut state = self.state();
        state.prune(now);
        let mut jobs = Vec::new();
        for turn in &active_turns {
            let turn_key = TurnKey::new(&turn.key, &turn.turn_id);
            let retry = state.turns.get(&turn_key).and_then(|entry| {
                (entry.status == EntryStatus::Failed && entry.completed_at.is_none())
                    .then(|| entry.agent.clone())
            });
            if let Some(agent) = retry {
                let host = match state.eligibility(&turn.key.server_id, &turn.runtime_kind) {
                    Eligibility::Eligible { host, .. } => host,
                    _ => continue,
                };
                let attempt = state.next_attempt();
                if let Some(entry) = state.turns.get_mut(&turn_key) {
                    entry.status = EntryStatus::Pending;
                    entry.attempt = attempt;
                    entry.planned_at = now;
                }
                jobs.push(PushJob::Subscribe {
                    turn: turn_key,
                    attempt,
                    agent,
                    host,
                });
            } else if let Some(job) = state.plan_subscribe(turn, now) {
                jobs.push(job);
            }
        }
        jobs
    }

    /// New platform registration (or `None`: no token, permission denied,
    /// signed out). `active_turns` comes from the store so turns that
    /// started before a registration existed get subscribed too.
    pub(crate) fn set_registration(
        &self,
        registration: Option<AppPushRegistration>,
        active_turns: Vec<ActiveTurn>,
    ) -> Vec<PushJob> {
        let registration = normalize_registration(registration);
        let now = self.backend.now_unix_secs();
        let mut state = self.state();
        state.prune(now);
        let previous = state.registration.clone();
        let mut jobs = Vec::new();
        match (previous, registration) {
            (None, None) => {}
            (Some(previous), None) => {
                info!("PushManager: registration cleared; unsubscribing all hosts");
                state.registration = None;
                state.last_worker_base_url = Some(previous.worker_base_url.clone());
                state.turns.clear();
                let mut servers: Vec<(&String, &HostPushRecord)> = state
                    .hosts
                    .iter()
                    .filter(|(_, record)| record.supports_push())
                    .collect();
                servers.sort_by(|a, b| a.0.cmp(b.0));
                for (server_id, record) in servers {
                    jobs.push(PushJob::UnsubscribeAll {
                        server_id: server_id.clone(),
                        host: record.params.clone(),
                        worker_base_url: Some(previous.worker_base_url.clone()),
                    });
                }
            }
            (None, Some(registration)) => {
                state.last_worker_base_url = Some(registration.worker_base_url.clone());
                state.registration = Some(registration);
                jobs.extend(
                    active_turns
                        .iter()
                        .filter_map(|turn| state.plan_subscribe(turn, now)),
                );
            }
            (Some(previous), Some(registration)) => {
                let token_changed = !same_push_identity(&previous, &registration);
                state.last_worker_base_url = Some(registration.worker_base_url.clone());
                state.registration = Some(registration);
                if token_changed {
                    info!("PushManager: push token changed; re-subscribing active turns");
                    jobs.extend(Self::plan_resubscribe_all(&mut state, now));
                }
                jobs.extend(
                    active_turns
                        .iter()
                        .filter_map(|turn| state.plan_subscribe(turn, now)),
                );
            }
        }
        jobs
    }

    /// Token changed: for every live (not completed, not unsupported) entry,
    /// drop the old host subscription, then subscribe again with a new grant.
    fn plan_resubscribe_all(state: &mut PushState, now: u64) -> Vec<PushJob> {
        let mut live: Vec<TurnKey> = state
            .turns
            .iter()
            .filter(|(_, entry)| {
                entry.completed_at.is_none() && entry.status != EntryStatus::Unsupported
            })
            .map(|(key, _)| key.clone())
            .collect();
        live.sort_by(|a, b| {
            (&a.server_id, &a.thread_id, &a.turn_id).cmp(&(&b.server_id, &b.thread_id, &b.turn_id))
        });
        let mut jobs = Vec::new();
        for turn_key in live {
            let Some(host) = state
                .hosts
                .get(&turn_key.server_id)
                .filter(|record| record.supports_push())
                .map(|record| record.params.clone())
            else {
                state.turns.remove(&turn_key);
                continue;
            };
            let attempt = state.next_attempt();
            let Some(entry) = state.turns.get_mut(&turn_key) else {
                continue;
            };
            entry.status = EntryStatus::Pending;
            entry.attempt = attempt;
            entry.planned_at = now;
            let agent = entry.agent.clone();
            jobs.push(PushJob::UnsubscribeTurn {
                server_id: turn_key.server_id.clone(),
                host: host.clone(),
                agent: agent.clone(),
                thread_id: turn_key.thread_id.clone(),
                turn_id: turn_key.turn_id.clone(),
            });
            jobs.push(PushJob::Subscribe {
                turn: turn_key,
                attempt,
                agent,
                host,
            });
        }
        jobs
    }

    /// User disconnected / unpaired `server_id`: forget it and revoke this
    /// device's subscriptions on that host. Without a host record (unpaired
    /// before it connected this launch) the host id comes from the
    /// `alleycat:<hostId>` server id and only the Worker revoke is sent.
    pub(crate) fn server_removed(&self, server_id: &str) -> Vec<PushJob> {
        let mut state = self.state();
        state.turns.retain(|key, _| key.server_id != server_id);
        match state.hosts.remove(server_id) {
            Some(record) if record.supports_push() => {
                info!("PushManager: server removed; unsubscribing server_id={server_id}");
                vec![PushJob::UnsubscribeAll {
                    server_id: server_id.to_string(),
                    host: record.params,
                    worker_base_url: state.worker_base_url(),
                }]
            }
            Some(_) => Vec::new(),
            None => {
                let Some(host_id) = host_id_from_server_id(server_id) else {
                    return Vec::new();
                };
                let Some(worker_base_url) = state.worker_base_url().filter(|url| !url.is_empty())
                else {
                    return Vec::new();
                };
                info!(
                    "PushManager: unknown host removed; direct Worker revoke server_id={server_id}"
                );
                vec![PushJob::RevokeDirect {
                    server_id: server_id.to_string(),
                    host_id,
                    worker_base_url,
                }]
            }
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────

    /// Push state for `turn_id` (or the thread's active turn when `None`).
    pub(crate) fn turn_push_state(
        &self,
        key: &ThreadKey,
        turn_id: Option<&str>,
        active_turn_id: Option<&str>,
        runtime_kind: &str,
    ) -> AppTurnPushState {
        let now = self.backend.now_unix_secs();
        let mut state = self.state();
        state.prune(now);
        let turn_id = turn_id.or(active_turn_id);
        if let Some(turn_id) = turn_id
            && let Some(entry) = state.turns.get(&TurnKey::new(key, turn_id))
        {
            return entry.status.as_push_state();
        }
        match state.eligibility(&key.server_id, runtime_kind) {
            Eligibility::NotApplicable | Eligibility::NoRegistration => {
                AppTurnPushState::NotApplicable
            }
            Eligibility::Unsupported => AppTurnPushState::Unsupported,
            // Eligible, active, but not subscribed yet (missed `TurnStarted`):
            // the background sweep will subscribe it.
            Eligibility::Eligible { .. } => match turn_id {
                Some(turn_id) if Some(turn_id) == active_turn_id => AppTurnPushState::Pending,
                _ => AppTurnPushState::NotApplicable,
            },
        }
    }

    // ── Job execution ────────────────────────────────────────────────────

    /// Execute `jobs`: serially per server (in order), servers in parallel.
    pub(crate) async fn run_jobs(self: &Arc<Self>, jobs: Vec<PushJob>) {
        let mut groups: Vec<(String, Vec<PushJob>)> = Vec::new();
        for job in jobs {
            let server_id = job.server_id().to_string();
            match groups.iter_mut().find(|(id, _)| *id == server_id) {
                Some((_, group)) => group.push(job),
                None => groups.push((server_id, vec![job])),
            }
        }
        let runs = groups.into_iter().map(|(server_id, group)| {
            let manager = Arc::clone(self);
            async move {
                let lock = manager.server_lock(&server_id);
                let _guard = lock.lock().await;
                for job in group {
                    manager.run_job(job).await;
                }
            }
        });
        futures::future::join_all(runs).await;
    }

    /// Fire-and-forget `run_jobs` on the shared runtime.
    pub(crate) fn spawn_jobs(self: &Arc<Self>, jobs: Vec<PushJob>) {
        if jobs.is_empty() {
            return;
        }
        let manager = Arc::clone(self);
        crate::MobileClient::spawn_detached(async move { manager.run_jobs(jobs).await });
    }

    async fn run_job(&self, job: PushJob) {
        match job {
            PushJob::Subscribe {
                turn,
                attempt,
                agent,
                host,
            } => self.run_subscribe(turn, attempt, agent, host).await,
            PushJob::UnsubscribeTurn {
                server_id,
                host,
                agent,
                thread_id,
                turn_id,
            } => {
                let scope = PushUnsubscribeScope::Turn {
                    agent,
                    thread_id,
                    turn_id,
                };
                if let Err(error) = self.backend.unsubscribe(&host, scope).await {
                    warn!(
                        "PushManager: push_unsubscribe (turn) failed server_id={server_id} error={error}"
                    );
                }
            }
            PushJob::UnsubscribeAll {
                server_id,
                host,
                worker_base_url,
            } => {
                self.run_unsubscribe_all(&server_id, &host, worker_base_url.as_deref())
                    .await
            }
            PushJob::RevokeDirect {
                server_id,
                host_id,
                worker_base_url,
            } => {
                self.run_direct_revoke(&server_id, &host_id, &worker_base_url)
                    .await
            }
        }
    }

    async fn run_subscribe(
        &self,
        turn: TurnKey,
        attempt: u64,
        agent: String,
        host: ParsedPairPayload,
    ) {
        let registration = {
            let state = self.state();
            let Some(entry) = state.turns.get(&turn) else {
                return;
            };
            if entry.attempt != attempt
                || entry.status != EntryStatus::Pending
                || entry.completed_at.is_some()
            {
                return;
            }
            let Some(registration) = state.registration.clone() else {
                return;
            };
            registration
        };
        let Some(device_key) = self.backend.device_secret_key() else {
            warn!(
                "PushManager: no device key; cannot sign grant server_id={}",
                turn.server_id
            );
            self.apply_attempt_result(&turn, attempt, EntryStatus::Failed);
            return;
        };
        let aud = match signing::worker_origin(&registration.worker_base_url) {
            Ok(aud) => aud,
            Err(error) => {
                warn!(
                    "PushManager: unusable worker URL; cannot sign grant server_id={} error={error}",
                    turn.server_id
                );
                self.apply_attempt_result(&turn, attempt, EntryStatus::Failed);
                return;
            }
        };
        let host_id = normalized_host_id(&host);
        let device_id = signing::device_id(&device_key);
        let sealed = match self.backend.seal_target(&SealTarget {
            platform: registration.platform,
            token: &registration.token,
            apns_environment: registration.apns_environment,
            device_id: &device_id,
            host_id: &host_id,
        }) {
            Ok(sealed) => sealed,
            Err(error) => {
                warn!(
                    "PushManager: sealing push target failed server_id={} error={error}",
                    turn.server_id
                );
                self.apply_attempt_result(&turn, attempt, EntryStatus::Failed);
                return;
            }
        };
        let now = self.backend.now_unix_secs();
        let issued = self.state().grant_issued_at(&host_id, now);
        let expires = issued + GRANT_LIFETIME_SECS;
        let nonce = self.backend.new_nonce();
        let fields = GrantFields {
            aud: &aud,
            host_id: &host_id,
            device_id: &device_id,
            platform: registration.platform,
            environment: registration.apns_environment,
            sealed_target: &sealed,
            agent: &agent,
            thread_id: &turn.thread_id,
            turn_id: &turn.turn_id,
            issued,
            expires,
            nonce: &nonce,
        };
        let signature = signing::sign_grant(&device_key, &fields);
        let args = PushSubscribeArgs {
            agent: agent.clone(),
            thread_id: turn.thread_id.clone(),
            turn_id: turn.turn_id.clone(),
            target: PushSubscribeTarget {
                platform: signing::platform_wire(registration.platform).to_string(),
                apns_environment: registration
                    .apns_environment
                    .map(|environment| signing::environment_wire(environment).to_string()),
                sealed,
            },
            grant: PushSubscribeGrant {
                device_id,
                issued,
                expires,
                nonce,
                signature,
            },
        };
        let status = match self.backend.subscribe(&host, args).await {
            Ok(outcome) => {
                info!(
                    "PushManager: subscribed server_id={} thread_id={} turn_id={} host_state={:?} terminal={}",
                    turn.server_id,
                    turn.thread_id,
                    turn.turn_id,
                    outcome.subscription,
                    outcome.terminal.is_some()
                );
                EntryStatus::Subscribed
            }
            Err(AlleycatPushError::Unsupported) => {
                info!(
                    "PushManager: host does not support push_subscribe server_id={}",
                    turn.server_id
                );
                EntryStatus::Unsupported
            }
            Err(error) => {
                warn!(
                    "PushManager: push_subscribe failed server_id={} thread_id={} turn_id={} error={error}",
                    turn.server_id, turn.thread_id, turn.turn_id
                );
                EntryStatus::Failed
            }
        };
        self.apply_attempt_result(&turn, attempt, status);
    }

    fn apply_attempt_result(&self, turn: &TurnKey, attempt: u64, status: EntryStatus) {
        let mut state = self.state();
        if let Some(entry) = state.turns.get_mut(turn)
            && entry.attempt == attempt
        {
            entry.status = status;
        }
    }

    async fn run_unsubscribe_all(
        &self,
        server_id: &str,
        host: &ParsedPairPayload,
        worker_base_url: Option<&str>,
    ) {
        let error = match self
            .backend
            .unsubscribe(host, PushUnsubscribeScope::All)
            .await
        {
            Ok(()) => {
                info!("PushManager: push_unsubscribe all ok server_id={server_id}");
                return;
            }
            Err(error) => error,
        };
        warn!(
            "PushManager: push_unsubscribe all failed server_id={server_id} error={error}; trying direct revoke"
        );
        let Some(worker_base_url) = worker_base_url.filter(|url| !url.is_empty()) else {
            debug!("PushManager: no worker base URL for direct revoke server_id={server_id}");
            return;
        };
        self.run_direct_revoke(server_id, &normalized_host_id(host), worker_base_url)
            .await;
    }

    /// Best-effort device-signed `scope=all` Worker revoke (design §5.3).
    async fn run_direct_revoke(&self, server_id: &str, host_id: &str, worker_base_url: &str) {
        let aud = match signing::worker_origin(worker_base_url) {
            Ok(aud) => aud,
            Err(error) => {
                warn!(
                    "PushManager: unusable worker URL for direct revoke server_id={server_id} error={error}"
                );
                return;
            }
        };
        let Some(device_key) = self.backend.device_secret_key() else {
            debug!("PushManager: no device key for direct revoke server_id={server_id}");
            return;
        };
        let timestamp = self.backend.now_unix_secs();
        let nonce = self.backend.new_nonce();
        // Recorded before sending: whether or not the request lands, later
        // grants for this host are issued after this second.
        {
            let mut state = self.state();
            let revoked = state.revoked_at.entry(host_id.to_string()).or_insert(0);
            *revoked = (*revoked).max(timestamp);
        }
        let request = DirectRevokeRequest {
            signature: signing::sign_revoke(&device_key, &aud, host_id, timestamp, &nonce),
            device_id: signing::device_id(&device_key),
            host_id: host_id.to_string(),
            scope: signing::REVOKE_SCOPE_ALL.to_string(),
            timestamp,
            nonce,
        };
        match self.backend.revoke_direct(worker_base_url, request).await {
            Ok(()) => info!("PushManager: direct revoke ok server_id={server_id}"),
            Err(error) => warn!(
                "PushManager: direct revoke failed server_id={server_id} error={error} (expiry is the backstop)"
            ),
        }
    }

    #[cfg(test)]
    fn has_host(&self, server_id: &str) -> bool {
        self.state().hosts.contains_key(server_id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::alleycat::{AlleycatHostPush, PushSubscriptionStatus};
    use crate::push::seal::{self, WorkerSealKey};
    use std::collections::VecDeque;
    use std::sync::atomic::{AtomicU64, Ordering};

    const NOW: u64 = 1_790_300_000;
    const WORKER_ORIGIN: &str = "https://worker.example.com";
    /// Test Worker sealing secret; the fake backend seals to its public key
    /// so tests can open what the manager sent.
    const TEST_SEAL_SECRET: [u8; 32] = [9u8; 32];

    fn test_seal_public_hex() -> String {
        hex::encode(
            x25519_dalek::PublicKey::from(&x25519_dalek::StaticSecret::from(TEST_SEAL_SECRET))
                .as_bytes(),
        )
    }

    /// Open a sealed target sent for `(host_id, device_id)` and return its
    /// plaintext JSON.
    fn open_sealed(sealed: &str, host_id: &str, device_id: &str) -> serde_json::Value {
        let (kid, plaintext) =
            seal::open_target(TEST_SEAL_SECRET, sealed, &format!("{host_id}|{device_id}"))
                .expect("sealed target opens with the test Worker key");
        assert_eq!(kid, 1);
        serde_json::from_str(&plaintext).expect("plaintext is JSON")
    }

    fn sealed_token(args: &PushSubscribeArgs, host_id: &str) -> String {
        open_sealed(&args.target.sealed, host_id, &args.grant.device_id)["token"]
            .as_str()
            .expect("token")
            .to_string()
    }

    #[derive(Debug, Clone, PartialEq, Eq)]
    enum Call {
        Subscribe {
            node_id: String,
            args: PushSubscribeArgs,
        },
        Unsubscribe {
            node_id: String,
            scope: PushUnsubscribeScope,
        },
        Revoke {
            url: String,
            request: DirectRevokeRequest,
        },
    }

    struct FakeBackend {
        key: Option<SecretKey>,
        now: AtomicU64,
        nonce: AtomicU64,
        calls: StdMutex<Vec<Call>>,
        subscribe_results: StdMutex<VecDeque<Result<PushSubscribeOutcome, AlleycatPushError>>>,
        unsubscribe_results: StdMutex<VecDeque<Result<(), AlleycatPushError>>>,
    }

    impl FakeBackend {
        fn new() -> Arc<Self> {
            Arc::new(Self {
                key: Some(SecretKey::from_bytes(&[2u8; 32])),
                now: AtomicU64::new(NOW),
                nonce: AtomicU64::new(0),
                calls: StdMutex::new(Vec::new()),
                subscribe_results: StdMutex::new(VecDeque::new()),
                unsubscribe_results: StdMutex::new(VecDeque::new()),
            })
        }

        fn calls(&self) -> Vec<Call> {
            self.calls.lock().unwrap().clone()
        }

        fn take_calls(&self) -> Vec<Call> {
            std::mem::take(&mut *self.calls.lock().unwrap())
        }

        fn push_subscribe_result(&self, result: Result<PushSubscribeOutcome, AlleycatPushError>) {
            self.subscribe_results.lock().unwrap().push_back(result);
        }

        fn push_unsubscribe_result(&self, result: Result<(), AlleycatPushError>) {
            self.unsubscribe_results.lock().unwrap().push_back(result);
        }

        fn advance(&self, secs: u64) {
            self.now.fetch_add(secs, Ordering::SeqCst);
        }
    }

    #[async_trait]
    impl PushBackend for FakeBackend {
        fn device_secret_key(&self) -> Option<SecretKey> {
            self.key.clone()
        }

        fn now_unix_secs(&self) -> u64 {
            self.now.load(Ordering::SeqCst)
        }

        fn new_nonce(&self) -> String {
            format!("{:032x}", self.nonce.fetch_add(1, Ordering::SeqCst) + 1)
        }

        fn seal_target(&self, target: &SealTarget<'_>) -> Result<String, SealError> {
            let public_hex = test_seal_public_hex();
            seal::seal_target(
                &WorkerSealKey {
                    kid: 1,
                    public_hex: &public_hex,
                },
                target,
            )
        }

        async fn subscribe(
            &self,
            host: &ParsedPairPayload,
            args: PushSubscribeArgs,
        ) -> Result<PushSubscribeOutcome, AlleycatPushError> {
            self.calls.lock().unwrap().push(Call::Subscribe {
                node_id: host.node_id.clone(),
                args,
            });
            self.subscribe_results
                .lock()
                .unwrap()
                .pop_front()
                .unwrap_or(Ok(PushSubscribeOutcome {
                    subscription: PushSubscriptionStatus::Registered,
                    terminal: None,
                }))
        }

        async fn unsubscribe(
            &self,
            host: &ParsedPairPayload,
            scope: PushUnsubscribeScope,
        ) -> Result<(), AlleycatPushError> {
            self.calls.lock().unwrap().push(Call::Unsubscribe {
                node_id: host.node_id.clone(),
                scope,
            });
            self.unsubscribe_results
                .lock()
                .unwrap()
                .pop_front()
                .unwrap_or(Ok(()))
        }

        async fn revoke_direct(
            &self,
            worker_base_url: &str,
            request: DirectRevokeRequest,
        ) -> Result<(), String> {
            self.calls.lock().unwrap().push(Call::Revoke {
                url: worker_base_url.to_string(),
                request,
            });
            Ok(())
        }
    }

    fn host_node(seed: u8) -> String {
        signing::device_id(&SecretKey::from_bytes(&[seed; 32]))
    }

    fn server_id(seed: u8) -> String {
        format!("alleycat:{}", host_node(seed))
    }

    fn params(seed: u8) -> ParsedPairPayload {
        ParsedPairPayload {
            version: 1,
            node_id: host_node(seed),
            token: format!("pair-token-{seed}"),
            relay: None,
            host_name: None,
        }
    }

    fn push_host(agents: &[&str]) -> Option<AlleycatHostInfo> {
        Some(AlleycatHostInfo {
            features: vec!["push.v1".to_string()],
            push: Some(AlleycatHostPush {
                enabled: true,
                agents: agents.iter().map(|agent| agent.to_string()).collect(),
            }),
        })
    }

    fn record(seed: u8, host: Option<AlleycatHostInfo>) -> HostPushRecord {
        HostPushRecord {
            params: params(seed),
            host,
            runtime_agents: HashMap::from([
                ("codex".to_string(), "codex".to_string()),
                ("claude".to_string(), "claude".to_string()),
            ]),
        }
    }

    fn registration(token: &str) -> AppPushRegistration {
        AppPushRegistration {
            platform: AppPushPlatform::Ios,
            token: token.to_string(),
            apns_environment: Some(AppApnsEnvironment::Production),
            worker_base_url: "https://worker.example.com/".to_string(),
        }
    }

    fn turn(seed: u8, thread: &str, turn: &str, runtime: &str) -> ActiveTurn {
        ActiveTurn {
            key: ThreadKey {
                server_id: server_id(seed),
                thread_id: thread.to_string(),
            },
            turn_id: turn.to_string(),
            runtime_kind: runtime.to_string(),
        }
    }

    fn key(seed: u8, thread: &str) -> ThreadKey {
        ThreadKey {
            server_id: server_id(seed),
            thread_id: thread.to_string(),
        }
    }

    fn manager_with(backend: &Arc<FakeBackend>) -> Arc<PushManager> {
        let backend: Arc<dyn PushBackend> = Arc::clone(backend) as Arc<dyn PushBackend>;
        Arc::new(PushManager::new(backend))
    }

    /// A manager with host 1 (push.v1, codex+claude) and a registration.
    fn ready_manager(backend: &Arc<FakeBackend>) -> Arc<PushManager> {
        let manager = manager_with(backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex", "claude"])));
        assert!(
            manager
                .set_registration(Some(registration("tok-1")), Vec::new())
                .is_empty()
        );
        manager
    }

    fn subscribe_calls(calls: &[Call]) -> Vec<(String, PushSubscribeArgs)> {
        calls
            .iter()
            .filter_map(|call| match call {
                Call::Subscribe { node_id, args } => Some((node_id.clone(), args.clone())),
                _ => None,
            })
            .collect()
    }

    fn state_of(manager: &PushManager, seed: u8, thread: &str, turn_id: &str) -> AppTurnPushState {
        manager.turn_push_state(&key(seed, thread), Some(turn_id), Some(turn_id), "codex")
    }

    #[test]
    fn non_alleycat_server_is_not_applicable() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.set_registration(Some(registration("tok")), Vec::new());
        let local = ActiveTurn {
            key: ThreadKey {
                server_id: "local".to_string(),
                thread_id: "t".to_string(),
            },
            turn_id: "u".to_string(),
            runtime_kind: "codex".to_string(),
        };
        assert!(manager.turn_started(local.clone()).is_empty());
        assert_eq!(
            manager.turn_push_state(&local.key, Some("u"), Some("u"), "codex"),
            AppTurnPushState::NotApplicable
        );
    }

    #[test]
    fn legacy_host_without_push_v1_is_unsupported() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, None));
        manager.set_registration(Some(registration("tok")), Vec::new());
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Unsupported
        );
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), None, None, "codex"),
            AppTurnPushState::Unsupported
        );
    }

    #[test]
    fn host_with_push_disabled_is_unsupported() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        let mut host = push_host(&["codex"]).unwrap();
        host.push.as_mut().unwrap().enabled = false;
        manager.record_host(&server_id(1), record(1, Some(host)));
        manager.set_registration(Some(registration("tok")), Vec::new());
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Unsupported
        );
    }

    #[test]
    fn agent_not_in_host_push_agents_is_unsupported() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["claude"])));
        manager.set_registration(Some(registration("tok")), Vec::new());
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), Some("u"), Some("u"), "codex"),
            AppTurnPushState::Unsupported
        );
        // The listed agent is eligible on the same host.
        assert_eq!(manager.turn_started(turn(1, "t2", "u2", "claude")).len(), 1);
    }

    #[test]
    fn runtime_kind_maps_to_alleycat_agent_name() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        let mut host_record = record(1, push_host(&["claude-code"]));
        host_record.runtime_agents =
            HashMap::from([("claude".to_string(), "claude-code".to_string())]);
        manager.record_host(&server_id(1), host_record);
        manager.set_registration(Some(registration("tok")), Vec::new());
        let jobs = manager.turn_started(turn(1, "t", "u", "claude"));
        assert!(matches!(
            jobs.as_slice(),
            [PushJob::Subscribe { agent, .. }] if agent == "claude-code"
        ));
    }

    #[test]
    fn no_registration_means_no_subscribe() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex"])));
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::NotApplicable
        );
        assert!(backend.calls().is_empty());
    }

    #[tokio::test]
    async fn turn_started_subscribes_exactly_once_with_signed_grant() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "thread-1", "turn-1", "codex"));
        assert_eq!(jobs.len(), 1);
        assert_eq!(
            state_of(&manager, 1, "thread-1", "turn-1"),
            AppTurnPushState::Pending
        );
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "thread-1", "turn-1"),
            AppTurnPushState::Subscribed
        );

        let calls = subscribe_calls(&backend.calls());
        assert_eq!(calls.len(), 1);
        let (node_id, args) = &calls[0];
        assert_eq!(node_id, &host_node(1));
        assert_eq!(args.agent, "codex");
        assert_eq!(args.thread_id, "thread-1");
        assert_eq!(args.turn_id, "turn-1");
        assert_eq!(args.target.platform, "ios");
        assert_eq!(args.target.apns_environment.as_deref(), Some("production"));
        let device_key = SecretKey::from_bytes(&[2u8; 32]);
        assert_eq!(args.grant.device_id, signing::device_id(&device_key));
        assert_eq!(args.grant.issued, NOW);
        assert_eq!(args.grant.expires, NOW + GRANT_LIFETIME_SECS);
        assert!(args.grant.expires - args.grant.issued <= 48 * 60 * 60);
        assert_eq!(args.grant.nonce.len(), 32);
        // The sealed target binds token, platform, environment, device, host.
        assert_eq!(
            open_sealed(&args.target.sealed, &host_node(1), &args.grant.device_id),
            serde_json::json!({
                "v": 1,
                "platform": "ios",
                "token": "tok-1",
                "apnsEnvironment": "production",
                "deviceId": args.grant.device_id,
                "hostId": host_node(1),
            })
        );
        let expected_signature = signing::sign_grant(
            &device_key,
            &GrantFields {
                aud: WORKER_ORIGIN,
                host_id: &host_node(1),
                device_id: &args.grant.device_id,
                platform: AppPushPlatform::Ios,
                environment: Some(AppApnsEnvironment::Production),
                sealed_target: &args.target.sealed,
                agent: "codex",
                thread_id: "thread-1",
                turn_id: "turn-1",
                issued: NOW,
                expires: NOW + GRANT_LIFETIME_SECS,
                nonce: &args.grant.nonce,
            },
        );
        assert_eq!(args.grant.signature, expected_signature);
    }

    #[tokio::test]
    async fn duplicate_turn_started_does_not_resubscribe() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let first = manager.turn_started(turn(1, "t", "u", "codex"));
        let second = manager.turn_started(turn(1, "t", "u", "codex"));
        assert_eq!(first.len(), 1);
        assert!(second.is_empty());
        manager.run_jobs(first).await;
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        assert_eq!(subscribe_calls(&backend.calls()).len(), 1);
        // A new turn on the same thread is a new subscription.
        let next = manager.turn_started(turn(1, "t", "u2", "codex"));
        assert_eq!(next.len(), 1);
    }

    #[tokio::test]
    async fn subscribe_errors_map_to_states() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        backend.push_subscribe_result(Err(AlleycatPushError::Unsupported));
        backend.push_subscribe_result(Err(AlleycatPushError::Transport("down".into())));
        backend.push_subscribe_result(Err(AlleycatPushError::Rejected("bad".into())));
        let mut jobs = manager.turn_started(turn(1, "t", "a", "codex"));
        jobs.extend(manager.turn_started(turn(1, "t", "b", "codex")));
        jobs.extend(manager.turn_started(turn(1, "t", "c", "codex")));
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "t", "a"),
            AppTurnPushState::Unsupported
        );
        assert_eq!(state_of(&manager, 1, "t", "b"), AppTurnPushState::Failed);
        assert_eq!(state_of(&manager, 1, "t", "c"), AppTurnPushState::Failed);
    }

    #[tokio::test]
    async fn missing_device_key_fails_without_network() {
        let backend = Arc::new(FakeBackend {
            key: None,
            ..Arc::into_inner(FakeBackend::new()).unwrap()
        });
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        assert_eq!(state_of(&manager, 1, "t", "u"), AppTurnPushState::Failed);
        assert!(backend.calls().is_empty());
    }

    #[tokio::test]
    async fn background_retries_failed_active_turns_only() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        backend.push_subscribe_result(Err(AlleycatPushError::Transport("down".into())));
        backend.push_subscribe_result(Err(AlleycatPushError::Transport("down".into())));
        let mut jobs = manager.turn_started(turn(1, "t", "active", "codex"));
        jobs.extend(manager.turn_started(turn(1, "t2", "done", "codex")));
        manager.run_jobs(jobs).await;
        manager.turn_completed(&key(1, "t2"), "done");
        backend.take_calls();

        let retry = manager.app_entered_background(vec![turn(1, "t", "active", "codex")]);
        assert_eq!(retry.len(), 1);
        assert_eq!(
            state_of(&manager, 1, "t", "active"),
            AppTurnPushState::Pending
        );
        manager.run_jobs(retry).await;
        assert_eq!(
            state_of(&manager, 1, "t", "active"),
            AppTurnPushState::Subscribed
        );
        let calls = subscribe_calls(&backend.take_calls());
        assert_eq!(calls.len(), 1);
        assert_eq!(calls[0].1.turn_id, "active");
        // Subscribed turns are not retried again.
        assert!(
            manager
                .app_entered_background(vec![turn(1, "t", "active", "codex")])
                .is_empty()
        );
    }

    #[tokio::test]
    async fn background_subscribes_active_turns_missed_at_turn_started() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.app_entered_background(vec![
            turn(1, "t", "u", "codex"),
            turn(1, "t2", "u2", "unknown-agent"),
        ]);
        assert_eq!(jobs.len(), 1);
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Subscribed
        );
    }

    #[tokio::test]
    async fn turn_completed_keeps_state_then_prunes() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        manager.turn_completed(&key(1, "t"), "u");
        // Platforms query right after completion (thread no longer active).
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), Some("u"), None, "codex"),
            AppTurnPushState::Subscribed
        );
        // A completed turn is never re-subscribed.
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        backend.advance(COMPLETED_RETENTION_SECS);
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), Some("u"), None, "codex"),
            AppTurnPushState::NotApplicable
        );
    }

    #[tokio::test]
    async fn completed_before_send_skips_subscribe() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.turn_completed(&key(1, "t"), "u");
        manager.run_jobs(jobs).await;
        assert!(backend.calls().is_empty());
    }

    #[tokio::test]
    async fn token_change_unsubscribes_old_then_resubscribes_with_new_grant() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        let done = manager.turn_started(turn(1, "t", "old", "codex"));
        manager.run_jobs(done).await;
        manager.turn_completed(&key(1, "t"), "old");
        backend.take_calls();

        let jobs = manager.set_registration(Some(registration("tok-2")), Vec::new());
        assert_eq!(
            jobs.len(),
            2,
            "only the live turn is re-subscribed: {jobs:?}"
        );
        assert_eq!(state_of(&manager, 1, "t", "u"), AppTurnPushState::Pending);
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Subscribed
        );

        let calls = backend.take_calls();
        assert_eq!(calls.len(), 2);
        assert_eq!(
            calls[0],
            Call::Unsubscribe {
                node_id: host_node(1),
                scope: PushUnsubscribeScope::Turn {
                    agent: "codex".into(),
                    thread_id: "t".into(),
                    turn_id: "u".into(),
                },
            }
        );
        let Call::Subscribe { args, .. } = &calls[1] else {
            panic!("expected subscribe, got {:?}", calls[1]);
        };
        assert_eq!(args.turn_id, "u");
        assert_eq!(sealed_token(args, &host_node(1)), "tok-2");
    }

    #[tokio::test]
    async fn same_token_registration_is_idempotent() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        backend.take_calls();
        let mut same = registration("tok-1");
        same.worker_base_url = "https://other.example.com".into();
        assert!(
            manager
                .set_registration(Some(same), vec![turn(1, "t", "u", "codex")])
                .is_empty()
        );
    }

    #[tokio::test]
    async fn stale_in_flight_attempt_is_ignored_after_token_change() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let stale = manager.turn_started(turn(1, "t", "u", "codex"));
        let fresh = manager.set_registration(Some(registration("tok-2")), Vec::new());
        // The fresh unsubscribe + subscribe run first; the stale attempt is
        // then skipped because the entry expects a newer attempt.
        manager.run_jobs(fresh).await;
        manager.run_jobs(stale).await;
        let subscribes = subscribe_calls(&backend.calls());
        assert_eq!(subscribes.len(), 1);
        assert_eq!(sealed_token(&subscribes[0].1, &host_node(1)), "tok-2");
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Subscribed
        );
    }

    #[tokio::test]
    async fn registration_arrival_subscribes_active_turns() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex"])));
        assert!(manager.turn_started(turn(1, "t", "u", "codex")).is_empty());
        let jobs =
            manager.set_registration(Some(registration("tok")), vec![turn(1, "t", "u", "codex")]);
        assert_eq!(jobs.len(), 1);
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::Subscribed
        );
    }

    #[tokio::test]
    async fn registration_cleared_unsubscribes_all_push_hosts() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        manager.record_host(&server_id(2), record(2, push_host(&["codex"])));
        manager.record_host(&server_id(3), record(3, None));
        let mut jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        jobs.extend(manager.turn_started(turn(2, "t", "u", "codex")));
        manager.run_jobs(jobs).await;
        backend.take_calls();

        let jobs = manager.set_registration(None, Vec::new());
        assert_eq!(jobs.len(), 2, "legacy host 3 is skipped: {jobs:?}");
        manager.run_jobs(jobs).await;
        let mut unsubscribed: Vec<(String, PushUnsubscribeScope)> = backend
            .take_calls()
            .into_iter()
            .map(|call| match call {
                Call::Unsubscribe { node_id, scope } => (node_id, scope),
                other => panic!("unexpected call {other:?}"),
            })
            .collect();
        unsubscribed.sort_by(|a, b| a.0.cmp(&b.0));
        let mut expected = vec![
            (host_node(1), PushUnsubscribeScope::All),
            (host_node(2), PushUnsubscribeScope::All),
        ];
        expected.sort_by(|a, b| a.0.cmp(&b.0));
        assert_eq!(unsubscribed, expected);
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::NotApplicable
        );
        // Nothing left to do on a second clear.
        assert!(manager.set_registration(None, Vec::new()).is_empty());
    }

    #[tokio::test]
    async fn unreachable_host_falls_back_to_signed_worker_revoke() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        backend.push_unsubscribe_result(Err(AlleycatPushError::Transport("offline".into())));
        let jobs = manager.set_registration(None, Vec::new());
        manager.run_jobs(jobs).await;
        let calls = backend.take_calls();
        assert_eq!(calls.len(), 2);
        assert!(matches!(
            &calls[0],
            Call::Unsubscribe {
                scope: PushUnsubscribeScope::All,
                ..
            }
        ));
        let Call::Revoke { url, request } = &calls[1] else {
            panic!("expected direct revoke, got {:?}", calls[1]);
        };
        assert_eq!(url, "https://worker.example.com");
        let device_key = SecretKey::from_bytes(&[2u8; 32]);
        assert_eq!(request.host_id, host_node(1));
        assert_eq!(request.device_id, signing::device_id(&device_key));
        assert_eq!(request.scope, "all");
        assert_eq!(request.timestamp, NOW);
        assert_eq!(
            request.signature,
            signing::sign_revoke(
                &device_key,
                WORKER_ORIGIN,
                &host_node(1),
                NOW,
                &request.nonce
            )
        );
        let body = serde_json::to_value(request).expect("serialize");
        for field in [
            "hostId",
            "deviceId",
            "scope",
            "timestamp",
            "nonce",
            "signature",
        ] {
            assert!(body.get(field).is_some(), "missing {field} in {body}");
        }
    }

    #[tokio::test]
    async fn server_removed_unsubscribes_only_that_server() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        manager.record_host(&server_id(2), record(2, push_host(&["codex"])));
        let mut jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        jobs.extend(manager.turn_started(turn(2, "t", "u", "codex")));
        manager.run_jobs(jobs).await;
        backend.take_calls();

        let jobs = manager.server_removed(&server_id(1));
        assert_eq!(jobs.len(), 1);
        manager.run_jobs(jobs).await;
        assert_eq!(
            backend.take_calls(),
            vec![Call::Unsubscribe {
                node_id: host_node(1),
                scope: PushUnsubscribeScope::All
            }]
        );
        assert!(!manager.has_host(&server_id(1)));
        assert_eq!(
            state_of(&manager, 1, "t", "u"),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            state_of(&manager, 2, "t", "u"),
            AppTurnPushState::Subscribed
        );
        // Removing it again only reaches the Worker (no host record left);
        // a non-alleycat server is a no-op.
        assert!(matches!(
            manager.server_removed(&server_id(1)).as_slice(),
            [PushJob::RevokeDirect { host_id, .. }] if *host_id == host_node(1)
        ));
        assert!(manager.server_removed("local").is_empty());
    }

    #[tokio::test]
    async fn server_removed_skips_legacy_host() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, None));
        assert!(manager.server_removed(&server_id(1)).is_empty());
    }

    #[tokio::test]
    async fn same_thread_id_on_two_hosts_is_isolated() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        manager.record_host(&server_id(2), record(2, push_host(&["codex"])));
        backend.push_subscribe_result(Ok(PushSubscribeOutcome {
            subscription: PushSubscriptionStatus::Pending,
            terminal: None,
        }));
        backend.push_subscribe_result(Err(AlleycatPushError::Transport("down".into())));
        let first = manager.turn_started(turn(1, "same", "turn", "codex"));
        let second = manager.turn_started(turn(2, "same", "turn", "codex"));
        assert_eq!(first.len(), 1);
        assert_eq!(second.len(), 1, "same ids on another host are not deduped");
        manager.run_jobs(first).await;
        manager.run_jobs(second).await;
        assert_eq!(
            state_of(&manager, 1, "same", "turn"),
            AppTurnPushState::Subscribed
        );
        assert_eq!(
            state_of(&manager, 2, "same", "turn"),
            AppTurnPushState::Failed
        );
        let calls = subscribe_calls(&backend.calls());
        assert_eq!(calls[0].0, host_node(1));
        assert_eq!(calls[1].0, host_node(2));
        // Grants are bound to their host.
        assert_ne!(calls[0].1.grant.signature, calls[1].1.grant.signature);
    }

    #[test]
    fn active_turn_without_entry_reports_pending_when_eligible() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), None, Some("u"), "codex"),
            AppTurnPushState::Pending
        );
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), Some("other"), Some("u"), "codex"),
            AppTurnPushState::NotApplicable
        );
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), None, None, "codex"),
            AppTurnPushState::NotApplicable
        );
    }

    #[test]
    fn expired_entries_are_pruned_and_resubscribed() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        assert_eq!(manager.turn_started(turn(1, "t", "u", "codex")).len(), 1);
        backend.advance(GRANT_LIFETIME_SECS);
        // The old grant has expired; a still-active turn gets a fresh one.
        assert_eq!(
            manager
                .app_entered_background(vec![turn(1, "t", "u", "codex")])
                .len(),
            1
        );
    }

    #[test]
    fn registration_normalization() {
        assert_eq!(normalize_registration(None), None);
        let blank = AppPushRegistration {
            token: "  ".into(),
            ..registration("x")
        };
        assert_eq!(normalize_registration(Some(blank)), None);
        let ios = normalize_registration(Some(AppPushRegistration {
            apns_environment: None,
            ..registration(" tok ")
        }))
        .unwrap();
        assert_eq!(ios.token, "tok");
        assert_eq!(ios.apns_environment, Some(AppApnsEnvironment::Production));
        assert_eq!(ios.worker_base_url, "https://worker.example.com");
        let android = normalize_registration(Some(AppPushRegistration {
            platform: AppPushPlatform::Android,
            apns_environment: Some(AppApnsEnvironment::Sandbox),
            ..registration("fcm")
        }))
        .unwrap();
        assert_eq!(android.apns_environment, None);
    }

    fn store_with_thread(seed: u8, thread: &str, runtime: &str) -> crate::store::AppStoreReducer {
        let reducer = crate::store::AppStoreReducer::new();
        let mut snapshot = crate::store::ThreadSnapshot::from_info(
            &server_id(seed),
            crate::types::ThreadInfo {
                id: thread.to_string(),
                title: None,
                model: None,
                status: crate::types::ThreadSummaryStatus::Idle,
                preview: None,
                cwd: None,
                path: None,
                model_provider: None,
                agent_nickname: None,
                agent_role: None,
                parent_thread_id: None,
                forked_from_id: None,
                agent_status: None,
                created_at: None,
                updated_at: None,
            },
        );
        snapshot.agent_runtime_kind = runtime.to_string();
        reducer.upsert_thread_snapshot(snapshot);
        reducer
    }

    async fn wait_for_state(
        manager: &PushManager,
        seed: u8,
        thread: &str,
        turn_id: &str,
        expected: AppTurnPushState,
    ) {
        for _ in 0..200 {
            if manager.turn_push_state(&key(seed, thread), Some(turn_id), None, "codex") == expected
            {
                return;
            }
            tokio::time::sleep(std::time::Duration::from_millis(5)).await;
        }
        panic!("turn never reached {expected:?}");
    }

    #[tokio::test]
    async fn store_listener_hook_subscribes_on_turn_started_and_marks_completion() {
        use crate::session::events::UiEvent;
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let store = store_with_thread(1, "t", "claude");
        let started = UiEvent::TurnStarted {
            key: key(1, "t"),
            turn_id: "u".to_string(),
        };
        store.apply_ui_event(&started);
        manager.observe_ui_event(&store, &started);
        // Replayed / duplicate events are deduped.
        manager.observe_ui_event(&store, &started);
        wait_for_state(&manager, 1, "t", "u", AppTurnPushState::Subscribed).await;
        let calls = subscribe_calls(&backend.calls());
        assert_eq!(calls.len(), 1);
        assert_eq!(calls[0].1.agent, "claude");

        let completed = UiEvent::TurnCompleted {
            key: key(1, "t"),
            turn_id: "u".to_string(),
            error: None,
        };
        store.apply_ui_event(&completed);
        manager.observe_ui_event(&store, &completed);
        backend.advance(COMPLETED_RETENTION_SECS);
        assert_eq!(
            manager.turn_push_state(&key(1, "t"), Some("u"), None, "claude"),
            AppTurnPushState::NotApplicable
        );
    }

    #[tokio::test]
    async fn store_listener_hook_ignores_threads_unknown_to_the_store() {
        use crate::session::events::UiEvent;
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        let store = crate::store::AppStoreReducer::new();
        let started = UiEvent::TurnStarted {
            key: key(1, "missing"),
            turn_id: "u".to_string(),
        };
        store.apply_ui_event(&started);
        manager.observe_ui_event(&store, &started);
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        assert!(backend.calls().is_empty());
        // The store's active turns feed the background sweep once known.
        let store = store_with_thread(1, "missing", "codex");
        store.apply_ui_event(&started);
        let active: Vec<ActiveTurn> = store
            .active_turns()
            .into_iter()
            .map(|(key, turn_id, runtime_kind)| ActiveTurn {
                key,
                turn_id,
                runtime_kind,
            })
            .collect();
        assert_eq!(active.len(), 1);
        let jobs = manager.app_entered_background(active);
        manager.run_jobs(jobs).await;
        assert_eq!(
            state_of(&manager, 1, "missing", "u"),
            AppTurnPushState::Subscribed
        );
    }

    #[tokio::test]
    async fn android_registration_sends_no_apns_environment() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex"])));
        manager.set_registration(
            Some(AppPushRegistration {
                platform: AppPushPlatform::Android,
                token: "fcm-token".into(),
                apns_environment: None,
                worker_base_url: "https://worker.example.com".into(),
            }),
            Vec::new(),
        );
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        let calls = subscribe_calls(&backend.calls());
        assert_eq!(calls.len(), 1);
        assert_eq!(calls[0].1.target.platform, "android");
        assert_eq!(calls[0].1.target.apns_environment, None);
        let plaintext = open_sealed(
            &calls[0].1.target.sealed,
            &host_node(1),
            &calls[0].1.grant.device_id,
        );
        assert_eq!(plaintext["platform"], "android");
        assert_eq!(plaintext["token"], "fcm-token");
        assert!(plaintext["apnsEnvironment"].is_null());
    }

    #[tokio::test]
    async fn push_subscribe_request_carries_sealed_target_and_never_the_raw_token() {
        const TOKEN: &str = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1";
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex"])));
        manager.set_registration(Some(registration(TOKEN)), Vec::new());
        let jobs = manager.turn_started(turn(1, "thread-1", "turn-1", "codex"));
        manager.run_jobs(jobs).await;
        let calls = subscribe_calls(&backend.calls());
        assert_eq!(calls.len(), 1);
        let args = &calls[0].1;

        let json =
            crate::alleycat::push_subscribe_request_json("pair-token-1".into(), args.clone());
        assert!(!json.contains(TOKEN), "raw push token leaked: {json}");
        assert!(!format!("{args:?}").contains(TOKEN));
        let value: serde_json::Value = serde_json::from_str(&json).expect("json");
        assert_eq!(value["op"], "push_subscribe");
        let mut target_keys: Vec<&str> = value["target"]
            .as_object()
            .expect("target object")
            .keys()
            .map(String::as_str)
            .collect();
        target_keys.sort_unstable();
        assert_eq!(target_keys, ["apns_environment", "platform", "sealed"]);
        assert_eq!(value["target"]["sealed"], args.target.sealed.as_str());
        assert_eq!(value["target"]["apns_environment"], "production");
        // Only the Worker key opens it, and it names this device and host.
        assert_eq!(sealed_token(args, &host_node(1)), TOKEN);
    }

    #[tokio::test]
    async fn ids_breaking_push_rules_are_unsupported_without_contacting_host() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        let mut host_record = record(1, push_host(&["codex", "bad agent"]));
        host_record.runtime_agents = HashMap::from([
            ("codex".to_string(), "codex".to_string()),
            ("claude".to_string(), "bad agent".to_string()),
        ]);
        manager.record_host(&server_id(1), host_record);
        manager.set_registration(Some(registration("tok")), Vec::new());
        let long_thread = "t".repeat(signing::MAX_REF_ID_BYTES + 1);
        let cases = [
            turn(1, "t", "u", "claude"),
            turn(1, &long_thread, "u", "codex"),
            turn(1, "t", "turn\n1", "codex"),
            turn(1, "t", "", "codex"),
        ];
        for case in &cases {
            assert!(manager.turn_started(case.clone()).is_empty(), "{case:?}");
            assert_eq!(
                manager.turn_push_state(
                    &case.key,
                    Some(&case.turn_id),
                    Some(&case.turn_id),
                    &case.runtime_kind
                ),
                AppTurnPushState::Unsupported,
                "{case:?}"
            );
        }
        // Never retried on background, never re-subscribed on token change.
        assert!(manager.app_entered_background(cases.to_vec()).is_empty());
        assert!(
            manager
                .set_registration(Some(registration("tok-2")), cases.to_vec())
                .is_empty()
        );
        assert!(backend.calls().is_empty());
        // A well-formed turn on the same host still subscribes.
        assert_eq!(manager.turn_started(turn(1, "t", "ok", "codex")).len(), 1);
    }

    #[tokio::test]
    async fn live_subscriptions_are_capped_per_server() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        manager.record_host(&server_id(2), record(2, push_host(&["codex"])));
        let mut jobs = Vec::new();
        for index in 0..MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER {
            jobs.extend(manager.turn_started(turn(1, "t", &format!("u{index}"), "codex")));
        }
        assert_eq!(jobs.len(), MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER);
        assert!(
            manager
                .turn_started(turn(1, "t", "over", "codex"))
                .is_empty()
        );
        assert_eq!(
            state_of(&manager, 1, "t", "over"),
            AppTurnPushState::Unsupported
        );
        // Other hosts have their own budget.
        assert_eq!(manager.turn_started(turn(2, "t", "u", "codex")).len(), 1);
        manager.run_jobs(jobs).await;
        assert_eq!(
            subscribe_calls(&backend.calls()).len(),
            MAX_TRACKED_SUBSCRIPTIONS_PER_SERVER
        );
        // Subscribed turns keep their slot; a finished turn frees one.
        assert!(
            manager
                .turn_started(turn(1, "t", "over2", "codex"))
                .is_empty()
        );
        manager.turn_completed(&key(1, "t"), "u0");
        assert_eq!(manager.turn_started(turn(1, "t", "next", "codex")).len(), 1);
    }

    #[tokio::test]
    async fn cold_start_unpair_derives_host_id_and_revokes_at_worker() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.set_registration(Some(registration("tok")), Vec::new());
        // No host record this launch; the server id may carry upper-case hex.
        let removed = format!("alleycat:{}", host_node(1).to_ascii_uppercase());
        let jobs = manager.server_removed(&removed);
        assert_eq!(
            jobs,
            vec![PushJob::RevokeDirect {
                server_id: removed.clone(),
                host_id: host_node(1),
                worker_base_url: WORKER_ORIGIN.to_string(),
            }]
        );
        manager.run_jobs(jobs).await;
        let calls = backend.take_calls();
        assert_eq!(calls.len(), 1, "only the Worker is contacted: {calls:?}");
        let Call::Revoke { url, request } = &calls[0] else {
            panic!("expected direct revoke, got {:?}", calls[0]);
        };
        assert_eq!(url, WORKER_ORIGIN);
        assert_eq!(request.host_id, host_node(1));
        assert_eq!(request.scope, "all");
        let device_key = SecretKey::from_bytes(&[2u8; 32]);
        assert_eq!(request.device_id, signing::device_id(&device_key));
        assert_eq!(
            request.signature,
            signing::sign_revoke(
                &device_key,
                WORKER_ORIGIN,
                &host_node(1),
                NOW,
                &request.nonce
            )
        );
        // Server ids that do not name an alleycat host revoke nothing.
        for server in [
            "local".to_string(),
            "alleycat:not-hex".to_string(),
            format!("alleycat:{}", &host_node(1)[..63]),
            format!("alleycat:{}0", host_node(1)),
            format!("ssh:{}", host_node(1)),
        ] {
            assert!(manager.server_removed(&server).is_empty(), "{server}");
        }
    }

    #[test]
    fn cold_start_unpair_without_worker_url_does_nothing() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        assert!(manager.server_removed(&server_id(1)).is_empty());
    }

    #[tokio::test]
    async fn grant_is_never_issued_in_the_second_of_own_revoke() {
        let backend = FakeBackend::new();
        let manager = ready_manager(&backend);
        manager.record_host(&server_id(2), record(2, push_host(&["codex"])));
        backend.push_unsubscribe_result(Err(AlleycatPushError::Transport("offline".into())));
        backend.push_unsubscribe_result(Ok(()));
        let jobs = manager.set_registration(None, Vec::new());
        manager.run_jobs(jobs).await;
        let revokes: Vec<DirectRevokeRequest> = backend
            .take_calls()
            .into_iter()
            .filter_map(|call| match call {
                Call::Revoke { request, .. } => Some(request),
                _ => None,
            })
            .collect();
        assert_eq!(revokes.len(), 1);
        assert_eq!(revokes[0].timestamp, NOW);
        let revoked_host = revokes[0].host_id.clone();
        let other_seed = if revoked_host == host_node(1) { 2 } else { 1 };
        let revoked_seed = if other_seed == 1 { 2 } else { 1 };

        // Re-registered and turns start within the same second.
        manager.set_registration(Some(registration("tok-1")), Vec::new());
        let mut jobs = manager.turn_started(turn(revoked_seed, "t", "u", "codex"));
        jobs.extend(manager.turn_started(turn(other_seed, "t", "u", "codex")));
        manager.run_jobs(jobs).await;
        let calls = subscribe_calls(&backend.take_calls());
        assert_eq!(calls.len(), 2);
        let (_, revoked_args) = calls
            .iter()
            .find(|(node, _)| *node == revoked_host)
            .expect("grant for the revoked host");
        let (_, other_args) = calls
            .iter()
            .find(|(node, _)| *node != revoked_host)
            .expect("grant for the other host");
        let grant = &revoked_args.grant;
        assert_eq!(grant.issued, NOW + 1);
        assert_eq!(grant.expires, NOW + 1 + GRANT_LIFETIME_SECS);
        let device_key = SecretKey::from_bytes(&[2u8; 32]);
        assert_eq!(
            grant.signature,
            signing::sign_grant(
                &device_key,
                &GrantFields {
                    aud: WORKER_ORIGIN,
                    host_id: &revoked_host,
                    device_id: &grant.device_id,
                    platform: AppPushPlatform::Ios,
                    environment: Some(AppApnsEnvironment::Production),
                    sealed_target: &revoked_args.target.sealed,
                    agent: "codex",
                    thread_id: "t",
                    turn_id: "u",
                    issued: NOW + 1,
                    expires: NOW + 1 + GRANT_LIFETIME_SECS,
                    nonce: &grant.nonce,
                },
            )
        );
        // A host this device did not revoke is unaffected.
        assert_eq!(other_args.grant.issued, NOW);

        // Once the clock has moved past the revoke second, grants use now.
        backend.advance(5);
        let jobs = manager.turn_started(turn(revoked_seed, "t", "u2", "codex"));
        manager.run_jobs(jobs).await;
        let calls = subscribe_calls(&backend.take_calls());
        assert_eq!(calls[0].1.grant.issued, NOW + 5);
    }

    #[tokio::test]
    async fn unusable_worker_url_fails_without_contacting_host() {
        let backend = FakeBackend::new();
        let manager = manager_with(&backend);
        manager.record_host(&server_id(1), record(1, push_host(&["codex"])));
        manager.set_registration(
            Some(AppPushRegistration {
                worker_base_url: "http://worker.example.com".into(),
                ..registration("tok")
            }),
            Vec::new(),
        );
        let jobs = manager.turn_started(turn(1, "t", "u", "codex"));
        manager.run_jobs(jobs).await;
        assert_eq!(state_of(&manager, 1, "t", "u"), AppTurnPushState::Failed);
        assert!(backend.calls().is_empty());
    }
}
