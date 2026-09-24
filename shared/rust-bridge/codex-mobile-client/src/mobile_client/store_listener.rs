use super::*;
use std::time::Duration;

const SUBAGENT_METADATA_HYDRATE_DELAYS_MS: [u64; 3] = [150, 800, 2500];
/// Quiet period before a lag recovery pass runs, so a burst of `Lagged`
/// errors (from the UI-event channel and/or several server event readers)
/// collapses into a single authoritative refresh, and consecutive passes
/// leave the transport room to drain.
const LAG_RECOVERY_DEBOUNCE: Duration = Duration::from_millis(250);
/// Threads re-fetched per lag recovery pass. A lag means the client is
/// already under load, so the rest wait for a later pass.
const LAG_RECOVERY_THREADS_PER_PASS: usize = 4;
/// Upper bound for one thread refresh in a lag recovery pass, so a hung
/// request cannot stall recovery (and every later lag) forever.
const LAG_RECOVERY_REFRESH_TIMEOUT: Duration = Duration::from_secs(20);

pub(super) fn spawn_store_listener(
    app_store: Arc<AppStoreReducer>,
    sessions: Arc<RwLock<HashMap<String, Arc<ServerSession>>>>,
    lag_recovery: Arc<StoreLagRecovery>,
    push_manager: Arc<crate::push::PushManager>,
    mut rx: broadcast::Receiver<UiEvent>,
) {
    MobileClient::spawn_detached(async move {
        loop {
            match rx.recv().await {
                Ok(event) => {
                    app_store.apply_ui_event(&event);
                    push_manager.observe_ui_event(&app_store, &event);
                    maybe_hydrate_collab_agent_metadata(
                        Arc::clone(&app_store),
                        Arc::clone(&sessions),
                        &event,
                    );
                    if let UiEvent::TurnCompleted { key, turn_id, .. } = &event {
                        maybe_send_next_local_queued_follow_up(&app_store, &sessions, key, turn_id);
                    }
                }
                Err(broadcast::error::RecvError::Closed) => break,
                Err(broadcast::error::RecvError::Lagged(skipped)) => {
                    warn!("MobileClient: lagged {skipped} UI events");
                    // The dropped events were never applied to the store and
                    // may belong to any server.
                    lag_recovery.trigger(None);
                }
            }
        }
    });
}

/// The process-wide `MobileClient` that owns `app_store`, if initialized.
///
/// Background tasks spawned from `MobileClient::new()` only hold store and
/// session handles; like `run_post_reconnect_resubscribe` they reach the
/// owning client through the shared singleton. A singleton that owns a
/// different store (e.g. while a test-local `MobileClient` is running) is
/// ignored so work never lands on the wrong client.
fn owning_mobile_client(app_store: &Arc<AppStoreReducer>) -> Option<Arc<MobileClient>> {
    crate::ffi::shared::shared_mobile_client_if_initialized()
        .filter(|client| Arc::ptr_eq(&client.app_store, app_store))
}

#[derive(Default)]
struct LagRecoveryState {
    /// A recovery task is pending or running. Later triggers only widen the
    /// scope below and are picked up by that task.
    scheduled: bool,
    all_servers: bool,
    servers: HashSet<String>,
    /// Lower-priority threads a capped pass left for a later pass.
    deferred: Vec<ThreadKey>,
}

impl LagRecoveryState {
    fn is_idle(&self) -> bool {
        !self.all_servers && self.servers.is_empty() && self.deferred.is_empty()
    }
}

/// Clears `scheduled` when the recovery task ends without doing so itself
/// (e.g. `recover` panicked), so later lags can still schedule a recovery.
struct LagRecoveryScheduleGuard<'a> {
    recovery: &'a StoreLagRecovery,
    armed: bool,
}

impl<'a> LagRecoveryScheduleGuard<'a> {
    fn new(recovery: &'a StoreLagRecovery) -> Self {
        Self {
            recovery,
            armed: true,
        }
    }
}

impl Drop for LagRecoveryScheduleGuard<'_> {
    fn drop(&mut self) {
        if self.armed {
            self.recovery.state().scheduled = false;
        }
    }
}

/// Brings the store back to an authoritative state after a broadcast
/// receiver lagged and dropped events: re-fetches the active thread and the
/// loaded threads of the affected servers with
/// `force_refresh_thread_authoritative`, then emits `FullResync` so platforms
/// reload the corrected snapshot. Triggers are debounced and coalesced; at
/// most one recovery task runs at a time. Each pass refreshes at most
/// `LAG_RECOVERY_THREADS_PER_PASS` threads in priority order (see
/// `plan_lag_recovery_pass`); the rest are deferred to later passes.
pub(super) struct StoreLagRecovery {
    app_store: Arc<AppStoreReducer>,
    debounce: Duration,
    state: StdMutex<LagRecoveryState>,
}

impl StoreLagRecovery {
    pub(super) fn new(app_store: Arc<AppStoreReducer>) -> Arc<Self> {
        Self::with_debounce(app_store, LAG_RECOVERY_DEBOUNCE)
    }

    fn with_debounce(app_store: Arc<AppStoreReducer>, debounce: Duration) -> Arc<Self> {
        Arc::new(Self {
            app_store,
            debounce,
            state: StdMutex::new(LagRecoveryState::default()),
        })
    }

    fn state(&self) -> std::sync::MutexGuard<'_, LagRecoveryState> {
        match self.state.lock() {
            Ok(guard) => guard,
            Err(error) => {
                warn!("MobileClient: recovering poisoned lag recovery lock");
                error.into_inner()
            }
        }
    }

    /// Schedule a recovery for `server_id`, or for every connected server
    /// when `None` (the shared UI-event channel carries all servers).
    pub(super) fn trigger(self: &Arc<Self>, server_id: Option<&str>) {
        let spawn = {
            let mut state = self.state();
            match server_id {
                Some(server_id) => {
                    state.servers.insert(server_id.to_string());
                }
                None => state.all_servers = true,
            }
            !std::mem::replace(&mut state.scheduled, true)
        };
        if spawn {
            let recovery = Arc::clone(self);
            MobileClient::spawn_detached(async move { recovery.run().await });
        }
    }

    async fn run(self: Arc<Self>) {
        let mut schedule_guard = LagRecoveryScheduleGuard::new(&self);
        loop {
            tokio::time::sleep(self.debounce).await;
            let (all_servers, servers, deferred) = {
                let mut state = self.state();
                if state.is_idle() {
                    state.scheduled = false;
                    schedule_guard.armed = false;
                    return;
                }
                (
                    std::mem::take(&mut state.all_servers),
                    std::mem::take(&mut state.servers),
                    std::mem::take(&mut state.deferred),
                )
            };
            self.recover(all_servers, servers, deferred).await;
        }
    }

    async fn recover(&self, all_servers: bool, servers: HashSet<String>, deferred: Vec<ThreadKey>) {
        // Only a pass answering a new lag emits `FullResync`; deferred passes
        // publish their refreshed threads through the per-thread upserts.
        let answers_new_lag = all_servers || !servers.is_empty();
        if let Some(client) = owning_mobile_client(&self.app_store) {
            let connected: HashSet<String> = client.sessions_read().keys().cloned().collect();
            let server_ids: Vec<String> = if all_servers {
                connected.iter().cloned().collect()
            } else {
                servers
                    .into_iter()
                    .filter(|server_id| connected.contains(server_id))
                    .collect()
            };
            let deferred = deferred
                .into_iter()
                .filter(|key| connected.contains(&key.server_id));
            let (now, later) = plan_lag_recovery_pass(
                &self.app_store.snapshot(),
                &server_ids,
                deferred,
                LAG_RECOVERY_THREADS_PER_PASS,
            );
            info!(
                "MobileClient: lag recovery refreshing servers={:?} thread_count={} deferred_count={}",
                server_ids,
                now.len(),
                later.len()
            );
            for key in now {
                match tokio::time::timeout(
                    LAG_RECOVERY_REFRESH_TIMEOUT,
                    client.force_refresh_thread_authoritative(&key.server_id, &key.thread_id),
                )
                .await
                {
                    Ok(Ok(_)) => {}
                    Ok(Err(error)) => warn!(
                        "MobileClient: lag recovery refresh failed server_id={} thread_id={}: {}",
                        key.server_id, key.thread_id, error
                    ),
                    Err(_) => warn!(
                        "MobileClient: lag recovery refresh timed out after {:?} server_id={} thread_id={}",
                        LAG_RECOVERY_REFRESH_TIMEOUT, key.server_id, key.thread_id
                    ),
                }
            }
            if !later.is_empty() {
                self.state().deferred.extend(later);
            }
        } else {
            debug!("MobileClient: lag recovery has no owning client; emitting resync only");
        }
        if answers_new_lag {
            self.app_store.emit_full_resync();
        }
    }
}

/// Picks the threads one lag recovery pass re-fetches: the resubscribe set
/// of `server_ids` plus `deferred` leftovers, ordered active thread first,
/// then threads with a running turn, then the other loaded threads. Returns
/// at most `cap` threads for this pass and the remainder for a later one.
fn plan_lag_recovery_pass(
    snapshot: &AppSnapshot,
    server_ids: &[String],
    deferred: impl IntoIterator<Item = ThreadKey>,
    cap: usize,
) -> (Vec<ThreadKey>, Vec<ThreadKey>) {
    let mut seen = HashSet::new();
    let mut keys: Vec<ThreadKey> = server_ids
        .iter()
        .flat_map(|server_id| threads_to_resubscribe(snapshot, server_id))
        .chain(
            deferred
                .into_iter()
                .filter(|key| snapshot.threads.contains_key(key)),
        )
        .filter(|key| seen.insert(key.clone()))
        .collect();
    keys.sort_by_key(|key| {
        if snapshot.active_thread.as_ref() == Some(key) {
            0
        } else if snapshot
            .threads
            .get(key)
            .is_some_and(|thread| thread.active_turn_id.is_some())
        {
            1
        } else {
            2
        }
    });
    let later = keys.split_off(cap.min(keys.len()));
    (keys, later)
}

fn maybe_hydrate_collab_agent_metadata(
    app_store: Arc<AppStoreReducer>,
    sessions: Arc<RwLock<HashMap<String, Arc<ServerSession>>>>,
    event: &UiEvent,
) {
    let Some((server_id, receiver_thread_ids)) = collab_receiver_thread_ids(event) else {
        return;
    };
    if receiver_thread_ids.is_empty() {
        return;
    }

    for thread_id in receiver_thread_ids {
        if !subagent_label_missing(&app_store, &server_id, &thread_id) {
            continue;
        }
        let app_store = Arc::clone(&app_store);
        let sessions = Arc::clone(&sessions);
        let server_id = server_id.clone();
        MobileClient::spawn_detached(async move {
            for delay_ms in std::iter::once(0_u64).chain(SUBAGENT_METADATA_HYDRATE_DELAYS_MS) {
                if !subagent_label_missing(&app_store, &server_id, &thread_id) {
                    return;
                }
                if delay_ms > 0 {
                    tokio::time::sleep(tokio::time::Duration::from_millis(delay_ms)).await;
                    if !subagent_label_missing(&app_store, &server_id, &thread_id) {
                        return;
                    }
                }

                let session = match sessions.read() {
                    Ok(guard) => guard.get(&server_id).cloned(),
                    Err(error) => {
                        warn!("MobileClient: recovering poisoned sessions read lock");
                        error.into_inner().get(&server_id).cloned()
                    }
                };
                let Some(session) = session else {
                    return;
                };
                if !session_is_current(&sessions, &server_id, &session) {
                    return;
                }

                match read_thread_response_from_app_server(Arc::clone(&session), &thread_id, false)
                    .await
                {
                    Ok(response) => {
                        if !session_is_current(&sessions, &server_id, &session) {
                            return;
                        }
                        if let Err(error) = upsert_thread_snapshot_from_app_server_read_response(
                            &app_store, &server_id, response,
                        ) {
                            warn!(
                                "MobileClient: failed to hydrate collab receiver metadata for server={} thread={}: {}",
                                server_id, thread_id, error
                            );
                            continue;
                        }
                    }
                    Err(error) => {
                        warn!(
                            "MobileClient: failed to read collab receiver metadata for server={} thread={}: {}",
                            server_id, thread_id, error
                        );
                    }
                }
            }
        });
    }
}

fn collab_receiver_thread_ids(event: &UiEvent) -> Option<(String, Vec<String>)> {
    match event {
        UiEvent::ItemStarted { key, notification } => match &notification.item {
            upstream::ThreadItem::CollabAgentToolCall {
                receiver_thread_ids,
                ..
            } if !receiver_thread_ids.is_empty() => Some((
                key.server_id.clone(),
                normalized_thread_ids(receiver_thread_ids.iter().map(String::as_str)),
            )),
            _ => None,
        },
        UiEvent::ItemCompleted { key, notification } => match &notification.item {
            upstream::ThreadItem::CollabAgentToolCall {
                receiver_thread_ids,
                ..
            } if !receiver_thread_ids.is_empty() => Some((
                key.server_id.clone(),
                normalized_thread_ids(receiver_thread_ids.iter().map(String::as_str)),
            )),
            _ => None,
        },
        UiEvent::RawNotification {
            server_id,
            method,
            params,
        } if method.contains("collab") => {
            let ids = params
                .get("receiver_agents")
                .and_then(serde_json::Value::as_array)
                .into_iter()
                .flatten()
                .filter_map(|value| value.get("thread_id"))
                .filter_map(serde_json::Value::as_str);
            let ids = normalized_thread_ids(ids);
            (!ids.is_empty()).then(|| (server_id.clone(), ids))
        }
        _ => None,
    }
}

fn normalized_thread_ids<'a>(thread_ids: impl IntoIterator<Item = &'a str>) -> Vec<String> {
    let mut seen = HashSet::new();
    let mut normalized = Vec::new();
    for thread_id in thread_ids {
        let trimmed = thread_id.trim();
        if trimmed.is_empty() || !seen.insert(trimmed.to_string()) {
            continue;
        }
        normalized.push(trimmed.to_string());
    }
    normalized
}

fn subagent_label_missing(app_store: &AppStoreReducer, server_id: &str, thread_id: &str) -> bool {
    let snapshot = app_store.snapshot();
    let key = ThreadKey {
        server_id: server_id.to_string(),
        thread_id: thread_id.to_string(),
    };
    snapshot.threads.get(&key).is_none_or(|thread| {
        thread
            .info
            .agent_nickname
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .is_none()
            && thread
                .info
                .agent_role
                .as_deref()
                .map(str::trim)
                .filter(|value| !value.is_empty())
                .is_none()
    })
}

/// Auto-send the next locally queued follow-up once `TurnCompleted` for
/// `completed_turn_id` has been applied. The reducer marks the draft
/// dispatched atomically (`claim_queued_follow_up_dispatch`), so duplicate
/// `TurnCompleted` events cannot send twice; only the network round-trip is
/// detached, so event consumption never stalls on it. A successful
/// `turn/start` dequeues the draft by id. A definite server rejection
/// releases the dispatch so a later `TurnCompleted` retries it; an ambiguous
/// failure (transport error, timeout) may have started the turn anyway, so
/// the dispatch is kept, only no longer awaiting its answer: the turn's
/// `TurnStarted` / user message still dequeues the draft by id, and only a
/// completion of a different turn retries it.
fn maybe_send_next_local_queued_follow_up(
    app_store: &Arc<AppStoreReducer>,
    sessions: &Arc<RwLock<HashMap<String, Arc<ServerSession>>>>,
    key: &ThreadKey,
    completed_turn_id: &str,
) {
    let has_session = match sessions.read() {
        Ok(guard) => guard.contains_key(&key.server_id),
        Err(error) => {
            warn!("MobileClient: recovering poisoned sessions read lock");
            error.into_inner().contains_key(&key.server_id)
        }
    };
    if !has_session {
        return;
    }
    let Some(draft) = app_store.claim_queued_follow_up_dispatch(key, completed_turn_id) else {
        return;
    };

    let app_store = Arc::clone(app_store);
    let key = key.clone();
    MobileClient::spawn_detached(async move {
        let Some(client) = owning_mobile_client(&app_store) else {
            app_store.release_queued_follow_up_dispatch(&key, &draft.preview.id);
            warn!(
                "MobileClient: failed to autosend queued follow-up for {} thread {}: owning MobileClient is not available",
                key.server_id, key.thread_id
            );
            return;
        };
        match client
            .send_queued_follow_up_turn(&key, draft.inputs.clone())
            .await
        {
            Ok(()) => app_store.complete_queued_follow_up_dispatch(&key, &draft.preview.id),
            Err(error @ RpcError::Server { .. }) => {
                app_store.release_queued_follow_up_dispatch(&key, &draft.preview.id);
                warn!(
                    "MobileClient: server rejected queued follow-up for {} thread {}: {}",
                    key.server_id, key.thread_id, error
                );
            }
            Err(error) => {
                app_store.mark_queued_follow_up_dispatch_unconfirmed(&key, &draft.preview.id);
                warn!(
                    "MobileClient: queued follow-up autosend outcome unknown for {} thread {}: {}",
                    key.server_id, key.thread_id, error
                );
            }
        }
    });
}

impl MobileClient {
    /// Start a turn for a locally queued follow-up draft. Uses
    /// `request_value_for_server`, the untyped twin of the
    /// `request_typed_for_server` path `start_turn` uses, so the
    /// request reaches the thread's runtime (codex / claude / pi / opencode)
    /// with the same model and permission normalization, without re-entering
    /// `start_turn`'s queue/steer/overlay logic.
    pub(super) async fn send_queued_follow_up_turn(
        &self,
        key: &ThreadKey,
        inputs: Vec<upstream::UserInput>,
    ) -> Result<(), RpcError> {
        self.request_value_for_server(
            &key.server_id,
            upstream::ClientRequest::TurnStart {
                request_id: upstream::RequestId::Integer(crate::next_request_id()),
                params: upstream::TurnStartParams {
                    thread_id: key.thread_id.clone(),
                    input: inputs,
                    ..Default::default()
                },
            },
        )
        .await
        .map(|_| ())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::QueuedFollowUpDraft;

    #[test]
    fn collab_receiver_thread_ids_extracts_spawn_agent_targets() {
        let event = UiEvent::ItemCompleted {
            key: ThreadKey {
                server_id: "srv".to_string(),
                thread_id: "parent".to_string(),
            },
            notification: upstream::ItemCompletedNotification {
                item: upstream::ThreadItem::CollabAgentToolCall {
                    id: "call-1".to_string(),
                    tool: upstream::CollabAgentTool::SpawnAgent,
                    status: upstream::CollabAgentToolCallStatus::Completed,
                    sender_thread_id: "parent".to_string(),
                    receiver_thread_ids: vec![
                        " child-1 ".to_string(),
                        "child-2".to_string(),
                        "child-1".to_string(),
                    ],
                    prompt: None,
                    model: None,
                    reasoning_effort: None,
                    agents_states: HashMap::new(),
                },
                thread_id: "parent".to_string(),
                turn_id: "turn-1".to_string(),
                completed_at_ms: 0,
            },
        };

        assert_eq!(
            collab_receiver_thread_ids(&event),
            Some((
                "srv".to_string(),
                vec!["child-1".to_string(), "child-2".to_string()],
            ))
        );
    }

    #[test]
    fn collab_receiver_thread_ids_extracts_legacy_receiver_agents() {
        let event = UiEvent::RawNotification {
            server_id: "srv".to_string(),
            method: "codex/event/collab_wait_end".to_string(),
            params: serde_json::json!({
                "receiver_agents": [
                    { "thread_id": "child-1" },
                    { "thread_id": " child-2 " },
                    { "thread_id": "child-1" }
                ]
            }),
        };

        assert_eq!(
            collab_receiver_thread_ids(&event),
            Some((
                "srv".to_string(),
                vec!["child-1".to_string(), "child-2".to_string()],
            ))
        );
    }

    fn idle_thread(server_id: &str, thread_id: &str) -> ThreadSnapshot {
        ThreadSnapshot::from_info(
            server_id,
            ThreadInfo {
                id: thread_id.to_string(),
                title: Some("Thread".to_string()),
                model: None,
                status: ThreadSummaryStatus::Idle,
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
        )
    }

    fn text_draft(text: &str) -> QueuedFollowUpDraft {
        queued_follow_up_draft_from_inputs(
            &[upstream::UserInput::Text {
                text: text.to_string(),
                text_elements: Vec::new(),
            }],
            AppQueuedFollowUpKind::Message,
        )
        .expect("draft")
    }

    fn follow_up_key() -> ThreadKey {
        ThreadKey {
            server_id: "srv".to_string(),
            thread_id: "thread-1".to_string(),
        }
    }

    fn reducer_with_queue(key: &ThreadKey, drafts: &[&QueuedFollowUpDraft]) -> AppStoreReducer {
        let reducer = AppStoreReducer::new();
        let mut thread = idle_thread(&key.server_id, &key.thread_id);
        thread.queued_follow_up_drafts = drafts.iter().map(|draft| (*draft).clone()).collect();
        reducer.upsert_thread_snapshot(thread);
        reducer
    }

    /// Platform-visible queued follow-up preview ids, in order.
    fn queued_ids(reducer: &AppStoreReducer, key: &ThreadKey) -> Vec<String> {
        reducer
            .thread_snapshot(key)
            .expect("thread exists")
            .queued_follow_ups
            .into_iter()
            .map(|preview| preview.id)
            .collect()
    }

    fn claimed_id(reducer: &AppStoreReducer, key: &ThreadKey, turn_id: &str) -> Option<String> {
        reducer
            .claim_queued_follow_up_dispatch(key, turn_id)
            .map(|draft| draft.preview.id)
    }

    fn turn_started(reducer: &AppStoreReducer, key: &ThreadKey, turn_id: &str) {
        reducer.apply_ui_event(&UiEvent::TurnStarted {
            key: key.clone(),
            turn_id: turn_id.to_string(),
        });
    }

    fn turn_completed(reducer: &AppStoreReducer, key: &ThreadKey, turn_id: &str) {
        reducer.apply_ui_event(&UiEvent::TurnCompleted {
            key: key.clone(),
            turn_id: turn_id.to_string(),
            error: None,
        });
    }

    #[test]
    fn duplicate_turn_completed_does_not_resend_queued_follow_up() {
        let key = follow_up_key();
        let (first, second) = (text_draft("first"), text_draft("second"));
        let reducer = reducer_with_queue(&key, &[&first, &second]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        // Duplicate completion while turn/start is outstanding.
        assert_eq!(claimed_id(&reducer, &key, "turn-0"), None);
        // No other draft is dispatched while that send is outstanding.
        assert_eq!(claimed_id(&reducer, &key, "turn-stale"), None);

        reducer.complete_queued_follow_up_dispatch(&key, &first.preview.id);
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);
        // Duplicate completion after turn/start answered but before the
        // autosent turn's TurnStarted: must not send the next draft.
        assert_eq!(claimed_id(&reducer, &key, "turn-0"), None);
    }

    #[test]
    fn successful_autosend_dequeues_by_id_without_turn_started() {
        let key = follow_up_key();
        let (first, second) = (text_draft("first"), text_draft("second"));
        let reducer = reducer_with_queue(&key, &[&first, &second]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        reducer.complete_queued_follow_up_dispatch(&key, &first.preview.id);
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);

        // The autosent turn's TurnStarted is lost; its completion still
        // sends the next draft.
        turn_completed(&reducer, &key, "turn-1");
        assert_eq!(
            claimed_id(&reducer, &key, "turn-1"),
            Some(second.preview.id.clone())
        );
        reducer.complete_queued_follow_up_dispatch(&key, &second.preview.id);
        assert!(queued_ids(&reducer, &key).is_empty());
    }

    #[test]
    fn turn_started_after_user_deleted_dispatched_draft_keeps_next_draft() {
        let key = follow_up_key();
        let (first, second) = (text_draft("first"), text_draft("second"));
        let reducer = reducer_with_queue(&key, &[&first, &second]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        // The user deletes the dispatched draft while turn/start is in flight.
        reducer.remove_thread_follow_up_draft(&key, &first.preview.id);
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);
        assert_eq!(claimed_id(&reducer, &key, "turn-stale"), None);

        turn_started(&reducer, &key, "turn-1");
        reducer.complete_queued_follow_up_dispatch(&key, &first.preview.id);
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);

        turn_completed(&reducer, &key, "turn-1");
        assert_eq!(
            claimed_id(&reducer, &key, "turn-1"),
            Some(second.preview.id.clone())
        );
    }

    #[test]
    fn rejected_autosend_releases_dispatch_for_retry() {
        let key = follow_up_key();
        let first = text_draft("first");
        let reducer = reducer_with_queue(&key, &[&first]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        reducer.release_queued_follow_up_dispatch(&key, &first.preview.id);

        assert_eq!(queued_ids(&reducer, &key), vec![first.preview.id.clone()]);
        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone()),
            "a released draft is retried by the next TurnCompleted"
        );
    }

    #[test]
    fn ambiguous_autosend_failure_keeps_dispatch_until_turn_starts() {
        let key = follow_up_key();
        let (first, second) = (text_draft("first"), text_draft("second"));
        let reducer = reducer_with_queue(&key, &[&first, &second]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        // turn/start failed with a transport error; the server may have
        // started the turn anyway.
        reducer.mark_queued_follow_up_dispatch_unconfirmed(&key, &first.preview.id);
        assert_eq!(
            queued_ids(&reducer, &key),
            vec![first.preview.id.clone(), second.preview.id.clone()]
        );
        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            None,
            "a duplicate TurnCompleted must not re-send the possibly accepted draft"
        );

        // The server did accept it: its TurnStarted dequeues it by id.
        turn_started(&reducer, &key, "turn-1");
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);
        turn_completed(&reducer, &key, "turn-1");
        assert_eq!(
            claimed_id(&reducer, &key, "turn-1"),
            Some(second.preview.id.clone())
        );
    }

    #[test]
    fn ambiguous_autosend_failure_retries_after_a_different_turn_completes() {
        let key = follow_up_key();
        let first = text_draft("first");
        let reducer = reducer_with_queue(&key, &[&first]);

        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );
        reducer.mark_queued_follow_up_dispatch_unconfirmed(&key, &first.preview.id);
        assert_eq!(claimed_id(&reducer, &key, "turn-0"), None);

        // No start ever arrived; a later completion of another turn retries
        // the same draft instead of jamming the queue.
        assert_eq!(
            claimed_id(&reducer, &key, "turn-1"),
            Some(first.preview.id.clone())
        );
        // That retry awaits its own answer again.
        assert_eq!(claimed_id(&reducer, &key, "turn-2"), None);
    }

    #[test]
    fn authoritative_refresh_keeps_queued_follow_up_dispatch() {
        let key = follow_up_key();
        let (first, second) = (text_draft("first"), text_draft("second"));
        let reducer = reducer_with_queue(&key, &[&first, &second]);
        assert_eq!(
            claimed_id(&reducer, &key, "turn-0"),
            Some(first.preview.id.clone())
        );

        // Lag recovery replaces the thread with a server snapshot.
        reducer.upsert_thread_snapshot(idle_thread(&key.server_id, &key.thread_id));

        assert_eq!(
            queued_ids(&reducer, &key),
            vec![first.preview.id.clone(), second.preview.id.clone()]
        );
        assert_eq!(claimed_id(&reducer, &key, "turn-0"), None);
        reducer.complete_queued_follow_up_dispatch(&key, &first.preview.id);
        assert_eq!(queued_ids(&reducer, &key), vec![second.preview.id.clone()]);
    }

    #[test]
    fn lag_recovery_pass_prioritizes_active_then_running_threads_and_caps() {
        let reducer = AppStoreReducer::new();
        let key = |server_id: &str, thread_id: &str| ThreadKey {
            server_id: server_id.to_string(),
            thread_id: thread_id.to_string(),
        };
        let loaded = |server_id: &str, thread_id: &str, running: bool| {
            let mut thread = idle_thread(server_id, thread_id);
            thread.initial_turns_loaded = true;
            thread.active_turn_id = running.then(|| format!("turn-{thread_id}"));
            thread
        };
        for thread_id in ["idle-1", "idle-2", "idle-3"] {
            reducer.upsert_thread_snapshot(loaded("srv", thread_id, false));
        }
        reducer.upsert_thread_snapshot(loaded("srv", "running-1", true));
        reducer.upsert_thread_snapshot(loaded("srv", "running-2", true));
        reducer.upsert_thread_snapshot(loaded("srv", "active", false));
        reducer.upsert_thread_snapshot(idle_thread("srv", "never-opened"));
        reducer.upsert_thread_snapshot(loaded("other", "running-elsewhere", true));
        reducer.set_active_thread(Some(key("srv", "active")));
        let snapshot = reducer.snapshot();

        let (now, later) = plan_lag_recovery_pass(&snapshot, &["srv".to_string()], [], 4);

        assert_eq!(now.len(), 4);
        assert_eq!(now[0], key("srv", "active"));
        let running: HashSet<ThreadKey> = now[1..3].iter().cloned().collect();
        assert_eq!(
            running,
            HashSet::from([key("srv", "running-1"), key("srv", "running-2")])
        );
        assert!(now[3].thread_id.starts_with("idle-"));
        assert_eq!(later.len(), 2);
        assert!(later.iter().all(|key| key.thread_id.starts_with("idle-")));

        // A later pass drains the deferred threads, dropping duplicates and
        // threads that were removed meanwhile.
        let mut deferred = later.clone();
        deferred.push(later[0].clone());
        deferred.push(key("srv", "removed"));
        let (now, rest) = plan_lag_recovery_pass(&snapshot, &[], deferred, 4);
        assert_eq!(now, later);
        assert!(rest.is_empty());
    }

    #[tokio::test]
    async fn lag_recovery_schedule_guard_reenables_recovery_after_panic() {
        let reducer = Arc::new(AppStoreReducer::new());
        let mut updates = reducer.subscribe();
        let recovery =
            StoreLagRecovery::with_debounce(Arc::clone(&reducer), Duration::from_millis(20));
        recovery.state().scheduled = true;

        let panicked = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _guard = LagRecoveryScheduleGuard::new(&recovery);
            panic!("simulated lag recovery panic");
        }));

        assert!(panicked.is_err());
        assert!(!recovery.state().scheduled);
        recovery.trigger(None);
        wait_for_lag_recovery_idle(&recovery).await;
        assert_eq!(drain_full_resyncs(&mut updates), 1);
    }

    async fn wait_for_lag_recovery_idle(recovery: &StoreLagRecovery) {
        let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
        while recovery.state().scheduled {
            assert!(
                tokio::time::Instant::now() < deadline,
                "lag recovery did not finish"
            );
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }

    fn drain_full_resyncs(updates: &mut broadcast::Receiver<AppStoreUpdateRecord>) -> usize {
        let mut count = 0;
        while let Ok(update) = updates.try_recv() {
            if matches!(update, AppStoreUpdateRecord::FullResync) {
                count += 1;
            }
        }
        count
    }

    #[tokio::test]
    async fn lag_recovery_coalesces_burst_into_single_full_resync() {
        let reducer = Arc::new(AppStoreReducer::new());
        let mut updates = reducer.subscribe();
        let recovery =
            StoreLagRecovery::with_debounce(Arc::clone(&reducer), Duration::from_millis(20));

        for _ in 0..50 {
            recovery.trigger(None);
            recovery.trigger(Some("srv"));
        }
        assert!(recovery.state().scheduled);
        wait_for_lag_recovery_idle(&recovery).await;
        assert_eq!(
            drain_full_resyncs(&mut updates),
            1,
            "a burst of lags should recover once"
        );

        // A later lag schedules a fresh recovery.
        recovery.trigger(Some("srv"));
        wait_for_lag_recovery_idle(&recovery).await;
        assert_eq!(drain_full_resyncs(&mut updates), 1);
    }
}
