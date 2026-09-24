use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};
use tauri_plugin_shell::process::CommandChild;

use crate::error::HostError;
use crate::sidecar::{self, Subcommand};

#[derive(Default)]
pub struct AppState {
    /// Serializes install/uninstall/stop/restart/reload/rotate/upgrade.
    pub mutation: tokio::sync::Mutex<()>,
    /// Running `logs -f` child, if any.
    pub follow: Mutex<Option<CommandChild>>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub last_seen_version: Option<String>,
    pub quit_notice_shown: bool,
}

impl Settings {
    fn path(app: &AppHandle) -> Result<std::path::PathBuf, HostError> {
        let dir = app
            .path()
            .app_data_dir()
            .map_err(|e| HostError::config_invalid(e.to_string()))?;
        std::fs::create_dir_all(&dir)?;
        Ok(dir.join("settings.json"))
    }

    pub fn load(app: &AppHandle) -> Settings {
        Self::path(app)
            .and_then(|p| std::fs::read_to_string(p).map_err(HostError::from))
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self, app: &AppHandle) -> Result<(), HostError> {
        let p = Self::path(app)?;
        let text = serde_json::to_string_pretty(self)
            .map_err(|e| HostError::config_invalid(e.to_string()))?;
        std::fs::write(p, text)?;
        Ok(())
    }
}

pub async fn run_mutating(
    app: &AppHandle,
    state: &AppState,
    cmd: Subcommand,
) -> Result<(), HostError> {
    run_mutating_seq(app, state, vec![cmd]).await
}

/// Run several mutating subcommands back to back under one lock hold.
pub async fn run_mutating_seq(
    app: &AppHandle,
    state: &AppState,
    cmds: Vec<Subcommand>,
) -> Result<(), HostError> {
    run_mutating_seq_with(state, cmds, |cmd| async move {
        sidecar::run(app, cmd).await.map(|_| ())
    }).await
}

async fn run_mutating_seq_with<F, Fut>(state: &AppState, cmds: Vec<Subcommand>, mut runner: F) -> Result<(), HostError>
where
    F: FnMut(Subcommand) -> Fut,
    Fut: std::future::Future<Output = Result<(), HostError>>,
{
    let _guard = state.mutation.lock().await;
    for cmd in cmds {
        debug_assert!(cmd.is_mutating());
        runner(cmd).await?;
    }
    Ok(())
}

/// Install (or repair) the LaunchAgent, refusing dmg/translocated bundles.
pub async fn install_host(app: &AppHandle, state: &AppState) -> Result<(), HostError> {
    crate::launchd::install_guard(&sidecar::sidecar_path()?)?;
    run_mutating_seq(app, state, install_sequence()).await
}

/// Stop the host service so it stays stopped: bootout the LaunchAgent when
/// one is installed (KeepAlive would respawn a plain stop), otherwise stop
/// the stray daemon through the sidecar.
pub async fn stop_host(app: &AppHandle, state: &AppState) -> Result<(), HostError> {
    let _guard = state.mutation.lock().await;
    match crate::launchd::stop_plan(&crate::launchd::current_install_state()?) {
        crate::launchd::StopPlan::Bootout => tauri::async_runtime::spawn_blocking(crate::launchd::bootout)
            .await
            .map_err(|e| HostError::config_invalid(format!("bootout task: {e}")))?,
        crate::launchd::StopPlan::SidecarStop => sidecar::run(app, Subcommand::Stop).await.map(|_| ()),
    }
}

/// What "install the host service" runs: `install` writes and bootstraps the
/// LaunchAgent; `restart` then stops any daemon that was running outside
/// launchd (e.g. one `pair` spawned) so the LaunchAgent owns the only one.
pub fn install_sequence() -> Vec<Subcommand> {
    vec![Subcommand::Install, Subcommand::Restart]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn mutating_commands_are_serialized() {
        let state = AppState::default();
        let order = Mutex::new(Vec::new());
        let (entered_tx, entered_rx) = tokio::sync::oneshot::channel();
        let (release_tx, release_rx) = tokio::sync::oneshot::channel();
        let mut entered_tx = Some(entered_tx);
        let mut release_rx = Some(release_rx);
        let first = run_mutating_seq_with(&state, install_sequence(), |cmd| {
            let signal = entered_tx.take();
            let release = release_rx.take();
            let order = &order;
            async move {
                order.lock().unwrap().push(cmd);
                if let Some(signal) = signal { signal.send(()).unwrap(); }
                if let Some(release) = release { release.await.unwrap(); }
                Ok(())
            }
        });
        let second = async {
            entered_rx.await.unwrap();
            let mut competing = Box::pin(run_mutating_seq_with(&state, vec![Subcommand::Rotate], |cmd| {
                order.lock().unwrap().push(cmd);
                async { Ok(()) }
            }));
            // Poll the real runner path while install is paused; it must wait
            // for the complete install + restart sequence, not just install.
            std::future::poll_fn(|cx| {
                assert!(std::future::Future::poll(competing.as_mut(), cx).is_pending());
                std::task::Poll::Ready(())
            }).await;
            release_tx.send(()).unwrap();
            competing.await.unwrap();
        };
        let (result, ()) = tokio::join!(first, second);
        result.unwrap();
        assert_eq!(*order.lock().unwrap(), vec![Subcommand::Install, Subcommand::Restart, Subcommand::Rotate]);
    }

    #[test]
    fn install_adopts_any_running_daemon_under_launchd() {
        // `install` alone leaves a daemon started by `pair` running outside
        // launchd; `restart` hands it over to the LaunchAgent.
        assert_eq!(install_sequence(), vec![Subcommand::Install, Subcommand::Restart]);
    }

    #[test]
    fn settings_round_trip_through_json() {
        let s = Settings { last_seen_version: Some("0.1.0".into()), quit_notice_shown: true };
        let json = serde_json::to_string(&s).unwrap();
        let back: Settings = serde_json::from_str(&json).unwrap();
        assert_eq!(back, s);
        let empty: Settings = serde_json::from_str("{}").unwrap();
        assert_eq!(empty, Settings::default());
    }
}
