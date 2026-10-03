use super::*;
use crate::conversation_uniffi::{HydratedConversationItem, HydratedConversationItemContent};

fn user_items(snapshot: &ThreadSnapshot) -> Vec<&HydratedConversationItem> {
    snapshot
        .items
        .iter()
        .filter(|item| matches!(item.content, HydratedConversationItemContent::User(_)))
        .collect()
}

impl MobileClient {
    /// Remove the selected prompt and later turns, returning its composer text.
    pub async fn edit_message(
        &self,
        key: &ThreadKey,
        selected_turn_index: u32,
    ) -> Result<String, RpcError> {
        self.get_session(&key.server_id)?;
        let current = self.snapshot_thread(key)?;
        ensure_thread_is_editable(&current)?;
        let index = selected_turn_index as usize;
        let depth = rollback_depth_for_turn(&current, index, true)?;
        let text = user_boundary_text_for_turn(&current, index)?;
        let before = user_items(&current)[index].source_turn_id.clone();
        self.trim_message_history(&current, before, depth).await?;
        self.set_active_thread(Some(key.clone()));
        Ok(text)
    }

    /// Keep the selected prompt and its reply in a separate thread.
    pub async fn fork_thread_from_message(
        &self,
        key: &ThreadKey,
        selected_turn_index: u32,
        cwd: Option<String>,
        model: Option<String>,
        approval_policy: Option<crate::types::AppAskForApproval>,
        sandbox: Option<crate::types::AppSandboxMode>,
        developer_instructions: Option<String>,
        persist_extended_history: bool,
    ) -> Result<ThreadKey, RpcError> {
        self.get_session(&key.server_id)?;
        let source = self.snapshot_thread(key)?;
        ensure_thread_is_editable(&source)?;
        let index = selected_turn_index as usize;
        let source_depth = rollback_depth_for_turn(&source, index, false)?;
        let selected_id = user_items(&source)[index].source_turn_id.clone();
        let developer_instructions =
            crate::local_runtime_instructions::splice_local_runtime_developer_instructions(
                self,
                &key.server_id,
                developer_instructions,
            );
        let response = self
            .server_thread_fork(
                &key.server_id,
                crate::types::AppForkThreadRequest {
                    thread_id: key.thread_id.clone(),
                    model,
                    cwd,
                    approval_policy,
                    sandbox,
                    developer_instructions,
                    persist_extended_history,
                    exclude_turns: false,
                }
                .try_into()
                .map_err(|e: crate::RpcClientError| RpcError::Deserialization(e.to_string()))?,
            )
            .await
            .map_err(|e| RpcError::Deserialization(e.to_string()))?;
        let mut fork = thread_snapshot_from_upstream_thread_with_overrides(
            &key.server_id,
            response.thread,
            Some(response.model),
            response
                .reasoning_effort
                .map(|value| reasoning_effort_string(value.into())),
            Some(response.approval_policy.into()),
            Some(response.sandbox.into()),
        )
        .map_err(RpcError::Deserialization)?;
        fork.initial_turns_loaded = true;
        fork.agent_runtime_kind = source.agent_runtime_kind.clone();
        let next_key = fork.key.clone();
        self.app_store.upsert_thread_snapshot(fork.clone());

        let trimmed: Result<(), RpcError> = async {
            let users = user_items(&fork);
            // Fork responses contain the full history, while the original may
            // only have its latest page. Resolve by stable source turn ID.
            let fork_index = if let Some(id) = &selected_id {
                users
                    .iter()
                    .position(|item| item.source_turn_id.as_ref() == Some(id))
                    .ok_or_else(|| {
                        RpcError::Deserialization(
                            "The selected turn was not preserved by the fork.".into(),
                        )
                    })?
            } else {
                users
                    .len()
                    .checked_sub(source_depth as usize + 1)
                    .ok_or_else(|| {
                        RpcError::Deserialization("The fork returned incomplete history.".into())
                    })?
            };
            let depth = rollback_depth_for_turn(&fork, fork_index, false)?;
            if depth > 0 {
                let before = users
                    .get(fork_index + 1)
                    .and_then(|item| item.source_turn_id.clone());
                self.trim_message_history(&fork, before, depth).await?;
            }
            Ok(())
        }
        .await;
        if let Err(error) = trimmed {
            // A failed composite must not silently leave a misleading full
            // fork behind. Archive only the thread just created by this call.
            let cleanup = self
                .request_typed_for_server::<upstream::ThreadArchiveResponse>(
                    &key.server_id,
                    upstream::ClientRequest::ThreadArchive {
                        request_id: upstream::RequestId::Integer(crate::next_request_id()),
                        params: upstream::ThreadArchiveParams {
                            thread_id: next_key.thread_id.clone(),
                        },
                    },
                )
                .await;
            if cleanup.is_ok() {
                self.app_store.remove_thread(&next_key);
            } else {
                return Err(RpcError::Deserialization(format!(
                    "{error}; incomplete fork {} could not be archived",
                    next_key.thread_id
                )));
            }
            return Err(error);
        }
        self.set_active_thread(Some(next_key.clone()));
        Ok(next_key)
    }

    async fn trim_message_history(
        &self,
        current: &ThreadSnapshot,
        before_turn_id: Option<String>,
        legacy_depth: u32,
    ) -> Result<(), RpcError> {
        let key = &current.key;
        if let Some(before_turn_id) = before_turn_id {
            let result = self
                .request_value_for_server(
                    &key.server_id,
                    upstream::ClientRequest::ThreadRevert {
                        request_id: upstream::RequestId::Integer(crate::next_request_id()),
                        params: upstream::ThreadRevertParams {
                            thread_id: key.thread_id.clone(),
                            before_turn_id,
                        },
                    },
                )
                .await;
            match result {
                Ok(value) => {
                    let response: upstream::ThreadRevertResponse = serde_json::from_value(value)
                        .map_err(|error| RpcError::Deserialization(error.to_string()))?;
                    let cursor = response.turns_backwards_cursor;
                    self.install_trimmed_history(current, response.thread, cursor.clone())?;
                    if cursor.is_some() {
                        // Mutation already succeeded. A hydration failure must
                        // not hide the restored draft or invite another revert.
                        // Retain the server cursor so loading older messages can retry.
                        if let Err(error) = self
                            .load_thread_turns_page(
                                &key.server_id,
                                &key.thread_id,
                                cursor,
                                Some(20),
                            )
                            .await
                        {
                            warn!("reverted history hydration failed: {error}");
                        }
                    }
                    return Ok(());
                }
                Err(RpcError::Server { code, message })
                    if code == -32601
                        || (code == -32600
                            && message.contains("unknown variant `thread/revert`")) => {}
                Err(error) => return Err(error),
            }
        }
        // Only a definitive unsupported-method response permits a legacy
        // retry. Timeouts/transport failures may already have changed history.
        let response = self
            .server_thread_rollback(
                &key.server_id,
                upstream::ThreadRollbackParams {
                    thread_id: key.thread_id.clone(),
                    num_turns: legacy_depth,
                },
            )
            .await
            .map_err(|error| RpcError::Deserialization(error.to_string()))?;
        self.install_trimmed_history(current, response.thread, None)
    }

    fn install_trimmed_history(
        &self,
        current: &ThreadSnapshot,
        thread: upstream::Thread,
        cursor: Option<String>,
    ) -> Result<(), RpcError> {
        let mut snapshot = thread_snapshot_from_upstream_thread_with_overrides(
            &current.key.server_id,
            thread,
            current.model.clone(),
            current.reasoning_effort.clone(),
            current.effective_approval_policy.clone(),
            current.effective_sandbox_policy.clone(),
        )
        .map_err(RpcError::Deserialization)?;
        copy_thread_runtime_fields(current, &mut snapshot);
        snapshot.agent_runtime_kind = current.agent_runtime_kind.clone();
        snapshot.initial_turns_loaded = true;
        snapshot.older_turns_cursor = cursor;
        snapshot.active_plan_progress = None;
        snapshot.pending_plan_implementation_turn_id = None;
        // Generic upsert deliberately preserves old items for metadata-only
        // reads. A successful history mutation must replace even with an empty
        // transcript, or removed messages would reappear on the next page.
        if self
            .app_store
            .mutate_thread_with_result(&current.key, |thread| {
                *thread = snapshot;
            })
            .is_some()
        {
            self.app_store.emit_thread_upsert(&current.key);
        }
        Ok(())
    }
}
