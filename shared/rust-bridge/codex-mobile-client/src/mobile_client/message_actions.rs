use super::*;
use crate::conversation_uniffi::HydratedConversationItemContent;

pub(super) fn ensure_thread_is_editable(snapshot: &ThreadSnapshot) -> Result<(), RpcError> {
    if !snapshot.initial_turns_loaded || snapshot.items.is_empty() {
        return Err(action_error(
            "Load the conversation before editing or forking a message.",
        ));
    }
    if snapshot.active_turn_id.is_some() || snapshot.info.status == ThreadSummaryStatus::Active {
        return Err(action_error(
            "Wait for the current reply to finish before editing or forking a message.",
        ));
    }
    if !snapshot.queued_follow_ups.is_empty()
        || !snapshot.queued_follow_up_drafts.is_empty()
        || snapshot
            .queued_follow_up_dispatch
            .as_ref()
            .is_some_and(|value| value.awaiting_start)
    {
        return Err(action_error(
            "Send or remove queued messages before editing or forking a message.",
        ));
    }
    if snapshot
        .goal
        .as_ref()
        .is_some_and(|goal| goal.status == crate::types::AppThreadGoalStatus::Active)
    {
        return Err(action_error(
            "Pause the active goal before editing or forking a message.",
        ));
    }
    Ok(())
}

fn action_error(message: &str) -> RpcError {
    RpcError::Deserialization(message.to_string())
}

// The public index is into the loaded USER messages, not the renderable items
// or source_turn_index (which can change when older pages are hydrated).
// Edit removes the selected prompt too; fork keeps it and its response.
pub(super) fn rollback_depth_for_turn(
    snapshot: &ThreadSnapshot,
    selected_turn_index: usize,
    include_selected: bool,
) -> Result<u32, RpcError> {
    let users: Vec<_> = snapshot
        .items
        .iter()
        .enumerate()
        .filter(|(_, item)| matches!(item.content, HydratedConversationItemContent::User(_)))
        .collect();
    let (item_index, selected) = users.get(selected_turn_index).ok_or_else(|| {
        action_error("The selected message is no longer in the loaded conversation.")
    })?;
    if !selected.is_from_user_turn_boundary {
        return Err(action_error(
            "This message is not an editable user turn boundary.",
        ));
    }

    // A rollback API can only address complete turns. Do not guess when a
    // source turn contains multiple steered prompts, or an autonomous turn
    // has no user prompt: core and persisted history count these differently.
    let mut user_turns = HashSet::new();
    for (_, item) in &users[selected_turn_index..] {
        if let Some(id) = &item.source_turn_id {
            if !user_turns.insert(id) {
                return Err(action_error(
                    "This history contains multiple prompts in one turn; message rollback is not supported.",
                ));
            }
        }
    }
    if snapshot.items[*item_index..].iter().any(|item| {
        item.source_turn_id
            .as_ref()
            .is_some_and(|id| !user_turns.contains(id))
    }) {
        return Err(action_error(
            "This history contains autonomous turns; message rollback is not supported.",
        ));
    }
    // Also reject a selected steer whose earlier sibling precedes this suffix.
    if selected.source_turn_id.as_ref().is_some_and(|id| {
        users[..selected_turn_index]
            .iter()
            .any(|(_, item)| item.source_turn_id.as_ref() == Some(id))
    }) {
        return Err(action_error(
            "A steered message cannot be rolled back independently of its turn.",
        ));
    }

    let depth = users.len() - selected_turn_index - usize::from(!include_selected);
    u32::try_from(depth).map_err(|_| action_error("Rollback depth overflow."))
}

pub(super) fn user_boundary_text_for_turn(
    snapshot: &ThreadSnapshot,
    selected_turn_index: usize,
) -> Result<String, RpcError> {
    snapshot
        .items
        .iter()
        .filter_map(|item| match &item.content {
            HydratedConversationItemContent::User(data) => Some(data.text.clone()),
            _ => None,
        })
        .nth(selected_turn_index)
        .ok_or_else(|| action_error("The selected message is no longer available."))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::conversation_uniffi::{
        HydratedConversationItem, HydratedNoteData, HydratedUserMessageData,
    };

    fn item(id: &str, turn: &str, user: bool) -> HydratedConversationItem {
        HydratedConversationItem {
            id: id.to_string(),
            source_turn_id: Some(turn.to_string()),
            source_turn_index: Some(99),
            timestamp: None,
            is_from_user_turn_boundary: user,
            content: if user {
                HydratedConversationItemContent::User(HydratedUserMessageData {
                    text: id.to_string(),
                    image_data_uris: vec![],
                })
            } else {
                HydratedConversationItemContent::Note(HydratedNoteData {
                    title: "Wait".to_string(),
                    body: "2000ms".to_string(),
                })
            },
        }
    }

    fn snapshot() -> ThreadSnapshot {
        let thread: upstream::Thread = serde_json::from_value(serde_json::json!({
            "id": "history", "sessionId": "history", "preview": "", "ephemeral": false, "modelProvider": "openai",
            "createdAt": 1, "updatedAt": 2, "status": {"type":"idle"}, "cwd": "/tmp",
            "cliVersion": "1", "source": "cli", "turns": []
        }))
        .unwrap();
        let mut snapshot = ThreadSnapshot::from_info("srv", thread.into());
        snapshot.initial_turns_loaded = true;
        snapshot.items = vec![
            item("one", "t1", true),
            item("reasoning", "t1", false),
            item("tool", "t1", false),
            item("reply", "t1", false),
            item("two", "t2", true),
            item("sleep", "t2", false),
            item("answer", "t2", false),
            item("three", "t3", true),
        ];
        snapshot
    }

    #[test]
    fn rollback_counts_user_boundaries_not_tool_or_reply_items() {
        let snapshot = snapshot();
        for (index, edit, fork) in [(0, 3, 2), (1, 2, 1), (2, 1, 0)] {
            assert_eq!(
                rollback_depth_for_turn(&snapshot, index, true).unwrap(),
                edit
            );
            assert_eq!(
                rollback_depth_for_turn(&snapshot, index, false).unwrap(),
                fork
            );
        }
        assert_eq!(user_boundary_text_for_turn(&snapshot, 1).unwrap(), "two");
        assert!(rollback_depth_for_turn(&snapshot, 3, true).is_err());
    }

    #[test]
    fn loaded_tail_and_prepended_pages_address_the_same_message() {
        let mut tail = snapshot();
        tail.items.drain(..4);
        tail.older_turns_cursor = Some("older".to_string());
        assert_eq!(rollback_depth_for_turn(&tail, 0, true).unwrap(), 2);
        assert_eq!(rollback_depth_for_turn(&snapshot(), 1, true).unwrap(), 2);
    }

    #[test]
    fn ambiguous_steer_and_autonomous_boundaries_are_rejected() {
        let mut snapshot = snapshot();
        snapshot.items.push(item("steer", "t3", true));
        assert!(rollback_depth_for_turn(&snapshot, 2, true).is_err());
        assert!(rollback_depth_for_turn(&snapshot, 3, true).is_err());
        snapshot.items.pop();
        snapshot.items.push(item("autonomous", "t4", false));
        assert!(rollback_depth_for_turn(&snapshot, 1, true).is_err());
    }

    #[test]
    fn only_loaded_idle_history_can_be_mutated() {
        let mut snapshot = snapshot();
        assert!(ensure_thread_is_editable(&snapshot).is_ok());
        snapshot.initial_turns_loaded = false;
        assert!(ensure_thread_is_editable(&snapshot).is_err());
        snapshot.initial_turns_loaded = true;
        snapshot.active_turn_id = Some("turn".to_string());
        assert!(ensure_thread_is_editable(&snapshot).is_err());
        snapshot.active_turn_id = None;
        snapshot.info.status = ThreadSummaryStatus::Active;
        assert!(ensure_thread_is_editable(&snapshot).is_err());
        snapshot.info.status = ThreadSummaryStatus::Idle;
        snapshot.queued_follow_ups.push(AppQueuedFollowUpPreview {
            id: "queued".to_string(),
            kind: AppQueuedFollowUpKind::Message,
            text: "next".to_string(),
        });
        assert!(ensure_thread_is_editable(&snapshot).is_err());
    }

    fn thread_json(id: &str, count: usize) -> serde_json::Value {
        let turns: Vec<_> = (0..count).map(|i| serde_json::json!({
            "id": format!("t{i}"), "status": "completed", "itemsView": "full",
            "items": [
                {"type":"userMessage", "id":format!("user{i}"), "content":[{"type":"text", "text":format!("prompt{i}"), "textElements":[]}]},
                {"type":"sleep", "id":format!("sleep{i}"), "durationMs": 2000},
                {"type":"agentMessage", "id":format!("reply{i}"), "text":format!("reply{i}")}
            ]
        })).collect();
        serde_json::json!({
            "id":id, "sessionId":id, "preview":"prompt0", "ephemeral":false,
            "modelProvider":"openai", "createdAt":1, "updatedAt":2,
            "status":{"type":"idle"}, "cwd":"/tmp", "cliVersion":"1", "source":"cli", "turns":turns
        })
    }

    #[tokio::test]
    async fn edit_and_fork_send_distinct_depths_and_keep_authoritative_history() {
        use crate::session::connection::TestRequestHandler;
        for (edit, mode) in [
            (true, "legacy"),
            (false, "legacy"),
            (true, "modern"),
            (false, "modern"),
            (true, "timeout"),
            (false, "denied"),
        ] {
            let modern = mode == "modern";
            let failed = mode == "timeout" || mode == "denied";
            let client = MobileClient::new();
            let config = ServerConfig {
                server_id: "srv".into(),
                display_name: "fixture".into(),
                host: "127.0.0.1".into(),
                port: 0,
                websocket_url: Some("ws://127.0.0.1:0".into()),
                is_local: false,
                tls: false,
            };
            client
                .app_store
                .upsert_server(&config, ServerHealthSnapshot::Connected);
            let mut source = thread_snapshot_from_upstream_thread_with_overrides(
                "srv",
                serde_json::from_value(thread_json("original", 3)).unwrap(),
                None,
                None,
                None,
                None,
            )
            .unwrap();
            source.initial_turns_loaded = true;
            let key = source.key.clone();
            client.app_store.upsert_thread_snapshot(source);
            let requests = Arc::new(StdMutex::new(Vec::new()));
            let recorded = requests.clone();
            let handler: TestRequestHandler = Arc::new(move |request| {
                recorded.lock().unwrap().push(request.method().to_string());
                match request {
                    upstream::ClientRequest::ThreadRevert { params, .. } => {
                        assert_eq!(params.thread_id, if edit { "original" } else { "fork" });
                        assert_eq!(params.before_turn_id, if edit { "t1" } else { "t2" });
                        if mode == "timeout" {
                            return Err(RpcError::Timeout);
                        }
                        if mode == "denied" {
                            return Err(RpcError::Server {
                                code: -32602,
                                message: "revert rejected".into(),
                            });
                        }
                        if modern {
                            Ok(
                                serde_json::json!({"thread":thread_json(&params.thread_id, 0), "turnsBackwardsCursor":"retained"}),
                            )
                        } else {
                            Err(RpcError::Server {
                                code: -32601,
                                message: "method not found".into(),
                            })
                        }
                    }
                    upstream::ClientRequest::ThreadTurnsList { params, .. } => {
                        assert!(modern);
                        assert_eq!(params.cursor.as_deref(), Some("retained"));
                        assert_eq!(params.items_view, Some(upstream::TurnItemsView::Full));
                        let thread = thread_json(&params.thread_id, if edit { 1 } else { 2 });
                        let turns: Vec<_> = thread["turns"]
                            .as_array()
                            .unwrap()
                            .iter()
                            .rev()
                            .cloned()
                            .collect();
                        Ok(serde_json::json!({"data":turns,"nextCursor":null}))
                    }
                    upstream::ClientRequest::ThreadRollback { params, .. } => {
                        assert_eq!(params.thread_id, if edit { "original" } else { "fork" });
                        assert_eq!(params.num_turns, if edit { 2 } else { 1 });
                        Ok(
                            serde_json::json!({"thread":thread_json(&params.thread_id, if edit {1} else {2})}),
                        )
                    }
                    upstream::ClientRequest::ThreadArchive { params, .. } => {
                        assert!(failed && !edit);
                        assert_eq!(params.thread_id, "fork");
                        Ok(serde_json::json!({}))
                    }
                    upstream::ClientRequest::ThreadFork { params, .. } => {
                        assert!(!edit);
                        assert_eq!(params.thread_id, "original");
                        Ok(
                            serde_json::json!({"thread":thread_json("fork", 3), "model":"gpt-6-luna",
                            "modelProvider":"openai", "cwd":"/tmp", "approvalPolicy":"never",
                            "approvalsReviewer":"user", "sandbox":{"type":"readOnly"}}),
                        )
                    }
                    other => panic!("unexpected request {}", other.method()),
                }
            });
            client.sessions.write().unwrap().insert(
                "srv".into(),
                Arc::new(ServerSession::test_stub_with_handlers(
                    config,
                    Some(handler),
                    None,
                    None,
                )),
            );
            if failed {
                if edit {
                    assert!(client.edit_message(&key, 1).await.is_err());
                } else {
                    assert!(
                        client
                            .fork_thread_from_message(&key, 1, None, None, None, None, None, true)
                            .await
                            .is_err()
                    );
                }
                assert_eq!(client.snapshot_thread(&key).unwrap().items.len(), 9);
                assert_eq!(
                    *requests.lock().unwrap(),
                    if edit {
                        vec!["thread/revert"]
                    } else {
                        vec!["thread/fork", "thread/revert", "thread/archive"]
                    }
                );
                continue;
            }
            let next = if edit {
                assert_eq!(client.edit_message(&key, 1).await.unwrap(), "prompt1");
                key.clone()
            } else {
                client
                    .fork_thread_from_message(&key, 1, None, None, None, None, None, true)
                    .await
                    .unwrap()
            };
            let updated = client.snapshot_thread(&next).unwrap();
            assert!(updated.initial_turns_loaded);
            assert_eq!(updated.items.len(), if edit { 3 } else { 6 });
            if !edit {
                assert_eq!(client.snapshot_thread(&key).unwrap().items.len(), 9);
            }
            let mut expected = if edit { vec![] } else { vec!["thread/fork"] };
            expected.extend(if modern {
                vec!["thread/revert", "thread/turns/list"]
            } else {
                vec!["thread/revert", "thread/rollback"]
            });
            assert_eq!(*requests.lock().unwrap(), expected);
        }
    }
}
