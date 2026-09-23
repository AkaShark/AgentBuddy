use std::collections::BTreeMap;
use std::path::PathBuf;

use tauri::{AppHandle, State};
use tauri_plugin_opener::OpenerExt;

use crate::config::{self, AgentSettings};
use crate::error::{HostError, HostErrorKind};
use crate::launchd::{self, HostState};
use crate::logs;
use crate::sidecar::{self, Subcommand};
use crate::state::{run_mutating, AppState};
use crate::status::{self, PairPayload};

pub async fn compute_host_state(app: &AppHandle) -> Result<HostState, HostError> {
    let sidecar = sidecar::sidecar_path()?;
    let plist = launchd::plist_path()?;
    let plist_exe = launchd::read_program_path(&plist)?;
    let install = launchd::derive_install_state(plist_exe.as_deref(), &sidecar);
    let status = match sidecar::run(app, Subcommand::StatusJson).await {
        Ok(out) => Some(status::parse_status(&out)?),
        Err(e) if matches!(e.kind, HostErrorKind::CommandFailed { .. }) => None,
        Err(e) => return Err(e),
    };
    Ok(HostState {
        install,
        running: status.as_ref().map(|s| s.is_running()).unwrap_or(false),
        status,
        app_version: app.package_info().version.to_string(),
        sidecar_path: sidecar.to_string_lossy().into_owned(),
    })
}

#[tauri::command]
pub async fn host_state(app: AppHandle) -> Result<HostState, HostError> {
    compute_host_state(&app).await
}

#[tauri::command]
pub async fn host_install(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Install).await
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
    run_mutating(&app, &state, Subcommand::Stop).await
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
pub async fn pair_payload(app: AppHandle) -> Result<PairPayload, HostError> {
    let out = sidecar::run(&app, Subcommand::Pair).await?;
    status::parse_pair(&out)
}

#[tauri::command]
pub async fn rotate_token(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Rotate).await
}

async fn config_path(app: &AppHandle) -> Result<PathBuf, HostError> {
    let out = sidecar::run(app, Subcommand::StatusJson).await?;
    Ok(PathBuf::from(status::parse_status(&out)?.config_path))
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
    let path = config_path(&app).await?;
    let text = config::read_or_empty(&path)?;
    config::write_atomic(&path, &config::set_agent_enabled(&text, &name, enabled)?)?;
    run_mutating(&app, &state, Subcommand::Reload).await
}

#[tauri::command]
pub async fn agent_set_bin(
    app: AppHandle,
    state: State<'_, AppState>,
    name: String,
    path: String,
) -> Result<(), HostError> {
    let cfg = config_path(&app).await?;
    let text = config::read_or_empty(&cfg)?;
    config::write_atomic(&cfg, &config::set_agent_bin(&text, &name, &path)?)?;
    run_mutating(&app, &state, Subcommand::Reload).await
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
