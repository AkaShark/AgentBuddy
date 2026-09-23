use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_shell::process::CommandEvent;
use tauri_plugin_shell::ShellExt;

use crate::error::HostError;
use crate::sidecar::{self, Subcommand, SIDECAR_NAME};
use crate::state::AppState;

pub const LOG_LINE_EVENT: &str = "log-line";

#[derive(Clone, Serialize)]
struct LogLine {
    line: String,
}

pub async fn tail(app: &AppHandle, lines: u32) -> Result<Vec<String>, HostError> {
    let out = sidecar::run(app, Subcommand::LogsTail(lines as usize)).await?;
    Ok(out.lines().map(str::to_owned).collect())
}

pub fn follow_start(app: &AppHandle) -> Result<(), HostError> {
    let state = app.state::<AppState>();
    let mut slot = state
        .follow
        .lock()
        .map_err(|_| HostError::config_invalid("follow lock poisoned"))?;
    if slot.is_some() {
        return Ok(());
    }
    let (mut rx, child) = app
        .shell()
        .sidecar(SIDECAR_NAME)
        .map_err(|e| HostError::sidecar_missing(e.to_string()))?
        .args(Subcommand::LogsFollow(200).args())
        .spawn()
        .map_err(|e| HostError::sidecar_missing(format!("spawn logs -f: {e}")))?;
    *slot = Some(child);
    let handle = app.clone();
    tauri::async_runtime::spawn(async move {
        while let Some(event) = rx.recv().await {
            match event {
                CommandEvent::Stdout(bytes) | CommandEvent::Stderr(bytes) => {
                    let line = String::from_utf8_lossy(&bytes).trim_end().to_string();
                    let _ = handle.emit(LOG_LINE_EVENT, LogLine { line });
                }
                CommandEvent::Terminated(_) => break,
                _ => {}
            }
        }
        if let Ok(mut slot) = handle.state::<AppState>().follow.lock() {
            *slot = None;
        }
    });
    Ok(())
}

pub fn follow_stop(app: &AppHandle) -> Result<(), HostError> {
    let state = app.state::<AppState>();
    let mut slot = state
        .follow
        .lock()
        .map_err(|_| HostError::config_invalid("follow lock poisoned"))?;
    if let Some(child) = slot.take() {
        child
            .kill()
            .map_err(|e| HostError::config_invalid(format!("kill logs -f: {e}")))?;
    }
    Ok(())
}
