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
    let _guard = state.mutation.lock().await;
    for cmd in cmds {
        debug_assert!(cmd.is_mutating());
        sidecar::run(app, cmd).await?;
    }
    Ok(())
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
        // Two tasks race for the mutation lock; the second must observe the first finished.
        let state = std::sync::Arc::new(AppState::default());
        let order = std::sync::Arc::new(std::sync::Mutex::new(Vec::<&'static str>::new()));
        let (s1, o1) = (state.clone(), order.clone());
        let t1 = tokio::spawn(async move {
            let _g = s1.mutation.lock().await;
            o1.lock().unwrap().push("a-start");
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            o1.lock().unwrap().push("a-end");
        });
        tokio::time::sleep(std::time::Duration::from_millis(5)).await;
        let (s2, o2) = (state.clone(), order.clone());
        let t2 = tokio::spawn(async move {
            let _g = s2.mutation.lock().await;
            o2.lock().unwrap().push("b-start");
        });
        let _ = tokio::join!(t1, t2);
        assert_eq!(*order.lock().unwrap(), vec!["a-start", "a-end", "b-start"]);
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
