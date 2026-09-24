use std::collections::BTreeMap;
use std::path::PathBuf;

use tauri::{AppHandle, State};
use tauri_plugin_opener::OpenerExt;

use crate::config::{self, AgentSettings};
use crate::error::{HostError, HostErrorKind};
use crate::launchd::{self, HostState};
use crate::logs;
use crate::sidecar::{self, Subcommand};
use crate::state::{install_host, run_mutating, stop_host, AppState};
use crate::status::{self, PairPayload, StatusInfo};

/// Split a `status --json` result into (status, status_error). A daemon CLI
/// failure (bad host.toml, wedged socket) is shown to the user, not hidden as
/// "stopped"; a failure to spawn the sidecar at all stays a hard error.
pub fn status_outcome(
    res: Result<String, HostError>,
) -> Result<(Option<StatusInfo>, Option<HostError>), HostError> {
    match res {
        Ok(out) => Ok((Some(status::parse_status(&out)?), None)),
        Err(e) if matches!(e.kind, HostErrorKind::CommandFailed { .. }) => Ok((None, Some(e))),
        Err(e) => Err(e),
    }
}

/// After editing host.toml: a running daemon must reload it; a stopped one
/// reads it at start, and `reload` would fail with "daemon not running".
pub fn reload_after_config_write(running: bool) -> Option<Subcommand> {
    running.then_some(Subcommand::Reload)
}

pub async fn compute_host_state(app: &AppHandle) -> Result<HostState, HostError> {
    let sidecar = sidecar::sidecar_path()?;
    let install = launchd::current_install_state()?;
    let (status, status_error) = status_outcome(sidecar::run(app, Subcommand::StatusJson).await)?;
    Ok(HostState {
        install,
        running: status.as_ref().map(|s| s.is_running()).unwrap_or(false),
        status,
        status_error,
        install_blocked: launchd::install_guard(&sidecar).err().map(|e| e.detail),
        app_version: app.package_info().version.to_string(),
        sidecar_path: sidecar.to_string_lossy().into_owned(),
    })
}

#[tauri::command]
pub async fn host_state(app: AppHandle) -> Result<HostState, HostError> {
    let s = compute_host_state(&app).await?;
    crate::tray::apply(&app, &s);
    Ok(s)
}

#[tauri::command]
pub async fn host_install(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    install_host(&app, &state).await
}

#[tauri::command]
pub async fn host_uninstall(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Uninstall).await
}

#[tauri::command]
pub async fn host_start(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Restart).await
}

#[tauri::command]
pub async fn host_stop(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    stop_host(&app, &state).await
}

#[tauri::command]
pub async fn host_restart(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Restart).await
}

#[tauri::command]
pub async fn host_reload(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Reload).await
}

#[tauri::command]
pub async fn host_upgrade(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Upgrade).await
}

#[tauri::command]
pub async fn pair_payload(app: AppHandle, state: State<'_, AppState>) -> Result<PairPayload, HostError> {
    launchd::pair_guard(&launchd::current_install_state()?)?;
    // `pair` may restart the daemon onto this binary (ensure_current_daemon),
    // so it runs under the same lock as the other service-changing commands.
    let _guard = state.mutation.lock().await;
    let out = sidecar::run(&app, Subcommand::Pair).await?;
    status::parse_pair(&out)
}

#[tauri::command]
pub async fn rotate_token(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Rotate).await
}

async fn current_status(app: &AppHandle) -> Result<StatusInfo, HostError> {
    let out = sidecar::run(app, Subcommand::StatusJson).await?;
    status::parse_status(&out)
}

async fn config_path(app: &AppHandle) -> Result<PathBuf, HostError> {
    Ok(PathBuf::from(current_status(app).await?.config_path))
}

#[tauri::command]
pub async fn agent_settings(app: AppHandle) -> Result<BTreeMap<String, AgentSettings>, HostError> {
    let path = config_path(&app).await?;
    config::read_agent_settings(&config::read_or_empty(&path)?)
}

#[tauri::command]
pub async fn agent_set_enabled(
    app: AppHandle,
    state: State<'_, AppState>,
    name: String,
    enabled: bool,
) -> Result<(), HostError> {
    let current = current_status(&app).await?;
    let path = PathBuf::from(&current.config_path);
    let text = config::read_or_empty(&path)?;
    config::write_atomic(&path, &config::set_agent_enabled(&text, &name, enabled)?)?;
    match reload_after_config_write(current.is_running()) {
        Some(cmd) => run_mutating(&app, &state, cmd).await,
        None => Ok(()),
    }
}

#[tauri::command]
pub async fn agent_set_bin(
    app: AppHandle,
    state: State<'_, AppState>,
    name: String,
    path: String,
) -> Result<(), HostError> {
    let current = current_status(&app).await?;
    let cfg = PathBuf::from(&current.config_path);
    let text = config::read_or_empty(&cfg)?;
    config::write_atomic(&cfg, &config::set_agent_bin(&text, &name, &path)?)?;
    match reload_after_config_write(current.is_running()) {
        Some(cmd) => run_mutating(&app, &state, cmd).await,
        None => Ok(()),
    }
}

#[tauri::command]
pub async fn codex_set_endpoint(
    app: AppHandle,
    state: State<'_, AppState>,
    host: Option<String>,
    port: Option<u16>,
) -> Result<(), HostError> {
    let current = current_status(&app).await?;
    let path = PathBuf::from(&current.config_path);
    let text = config::read_or_empty(&path)?;
    config::write_atomic(&path, &config::set_codex_endpoint(&text, host.as_deref(), port)?)?;
    match reload_after_config_write(current.is_running()) {
        Some(cmd) => run_mutating(&app, &state, cmd).await,
        None => Ok(()),
    }
}

#[tauri::command]
pub async fn logs_tail(app: AppHandle, lines: u32) -> Result<Vec<String>, HostError> {
    logs::tail(&app, lines).await
}

#[tauri::command]
pub fn logs_follow_start(app: AppHandle) -> Result<(), HostError> {
    logs::follow_start(&app)
}

#[tauri::command]
pub fn logs_follow_stop(app: AppHandle) -> Result<(), HostError> {
    logs::follow_stop(&app)
}

#[tauri::command]
pub async fn reveal_path(app: AppHandle, kind: String) -> Result<(), HostError> {
    let target = match kind.as_str() {
        "config" => config_path(&app).await?,
        "logs" => {
            let home =
                dirs::home_dir().ok_or_else(|| HostError::config_invalid("no home directory"))?;
            home.join("Library/Logs").join(launchd::LABEL)
        }
        other => return Err(HostError::config_invalid(format!("unknown path kind `{other}`"))),
    };
    app.opener()
        .reveal_item_in_dir(&target)
        .map_err(|e| HostError::config_invalid(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    const STATUS: &str = include_str!("../tests/fixtures/status.json");

    #[test]
    fn status_command_failures_are_reported_not_hidden() {
        let failed = HostError::command_failed("status --json", Some(1), "error: invalid host.toml");
        let (status, err) = status_outcome(Err(failed)).unwrap();
        assert!(status.is_none());
        assert!(err.unwrap().detail.contains("invalid host.toml"));
    }

    #[test]
    fn status_success_carries_no_error() {
        let (status, err) = status_outcome(Ok(STATUS.to_owned())).unwrap();
        assert!(status.is_some());
        assert!(err.is_none());
    }

    #[test]
    fn status_spawn_failures_stay_hard_errors() {
        assert!(status_outcome(Err(HostError::sidecar_missing("agentbuddy not found"))).is_err());
    }

    #[test]
    fn config_changes_reload_only_a_running_daemon() {
        assert_eq!(reload_after_config_write(true), Some(Subcommand::Reload));
        assert_eq!(reload_after_config_write(false), None);
    }
}
