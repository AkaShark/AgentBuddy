use std::path::PathBuf;

use tauri::AppHandle;
use tauri_plugin_shell::ShellExt;

use crate::error::HostError;

pub const SIDECAR_NAME: &str = "agentbuddy";

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Subcommand {
    StatusJson,
    Pair,
    Install,
    Uninstall,
    Stop,
    Restart,
    Reload,
    Rotate,
    Upgrade,
    LogsTail(usize),
    LogsFollow(usize),
    Version,
}

impl Subcommand {
    pub fn args(&self) -> Vec<String> {
        let v: Vec<&str> = match self {
            Subcommand::StatusJson => vec!["status", "--json"],
            Subcommand::Pair => vec!["pair"],
            Subcommand::Install => vec!["install"],
            Subcommand::Uninstall => vec!["uninstall"],
            Subcommand::Stop => vec!["stop"],
            Subcommand::Restart => vec!["restart"],
            Subcommand::Reload => vec!["reload"],
            Subcommand::Rotate => vec!["rotate"],
            Subcommand::Upgrade => vec!["upgrade"],
            Subcommand::LogsTail(n) => return vec!["logs".into(), "-n".into(), n.to_string()],
            Subcommand::LogsFollow(n) => {
                return vec!["logs".into(), "-f".into(), "-n".into(), n.to_string()]
            }
            Subcommand::Version => vec!["--version"],
        };
        v.into_iter().map(String::from).collect()
    }

    pub fn is_mutating(&self) -> bool {
        matches!(
            self,
            Subcommand::Install
                | Subcommand::Uninstall
                | Subcommand::Stop
                | Subcommand::Restart
                | Subcommand::Reload
                | Subcommand::Rotate
                | Subcommand::Upgrade
        )
    }

    pub fn label(&self) -> &'static str {
        match self {
            Subcommand::StatusJson => "status --json",
            Subcommand::Pair => "pair",
            Subcommand::Install => "install",
            Subcommand::Uninstall => "uninstall",
            Subcommand::Stop => "stop",
            Subcommand::Restart => "restart",
            Subcommand::Reload => "reload",
            Subcommand::Rotate => "rotate",
            Subcommand::Upgrade => "upgrade",
            Subcommand::LogsTail(_) => "logs -n",
            Subcommand::LogsFollow(_) => "logs -f",
            Subcommand::Version => "--version",
        }
    }
}

pub struct RawOutput {
    pub code: Option<i32>,
    pub stdout: String,
    pub stderr: String,
}

pub fn classify(cmd: &Subcommand, out: RawOutput) -> Result<String, HostError> {
    if out.code == Some(0) {
        return Ok(out.stdout);
    }
    if out.stderr.contains("Permission denied") || out.stderr.contains("Operation not permitted") {
        return Err(HostError::permission_denied(format!(
            "`agentbuddy {}`: {}",
            cmd.label(),
            out.stderr.trim()
        )));
    }
    Err(HostError::command_failed(cmd.label(), out.code, out.stderr))
}

/// Absolute path of the bundled sidecar: Tauri places external binaries next
/// to the main executable (Contents/MacOS in the .app, target/<profile> in dev).
pub fn sidecar_path() -> Result<PathBuf, HostError> {
    let exe = std::env::current_exe().map_err(|e| HostError::sidecar_missing(e.to_string()))?;
    let dir = exe
        .parent()
        .ok_or_else(|| HostError::sidecar_missing("executable has no parent dir"))?;
    let path = dir.join(SIDECAR_NAME);
    if !path.exists() {
        return Err(HostError::sidecar_missing(format!("{} not found", path.display())));
    }
    Ok(path)
}

pub async fn run(app: &AppHandle, cmd: Subcommand) -> Result<String, HostError> {
    let command = app
        .shell()
        .sidecar(SIDECAR_NAME)
        .map_err(|e| HostError::sidecar_missing(e.to_string()))?
        .args(cmd.args())
        .env("PATH", crate::shellenv::user_path())
        .env("SHELL", crate::shellenv::user_shell());
    let output = command.output().await.map_err(|e| {
        HostError::sidecar_missing(format!("spawn `agentbuddy {}`: {e}", cmd.label()))
    })?;
    classify(
        &cmd,
        RawOutput {
            code: output.status.code(),
            stdout: String::from_utf8_lossy(&output.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
        },
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::error::HostErrorKind;

    #[test]
    fn args_match_the_daemon_cli() {
        assert_eq!(Subcommand::StatusJson.args(), vec!["status", "--json"]);
        assert_eq!(Subcommand::Pair.args(), vec!["pair"]);
        assert_eq!(Subcommand::LogsTail(500).args(), vec!["logs", "-n", "500"]);
        assert_eq!(Subcommand::LogsFollow(200).args(), vec!["logs", "-f", "-n", "200"]);
        assert_eq!(Subcommand::Version.args(), vec!["--version"]);
    }

    #[test]
    fn mutating_set_is_exactly_the_service_changing_commands() {
        let mutating = [
            Subcommand::Install, Subcommand::Uninstall, Subcommand::Stop,
            Subcommand::Restart, Subcommand::Reload, Subcommand::Rotate, Subcommand::Upgrade,
        ];
        for c in mutating { assert!(c.is_mutating(), "{c:?}"); }
        for c in [Subcommand::StatusJson, Subcommand::Pair, Subcommand::LogsTail(1), Subcommand::LogsFollow(1), Subcommand::Version] {
            assert!(!c.is_mutating(), "{c:?}");
        }
    }

    #[test]
    fn classify_returns_stdout_on_success() {
        let out = RawOutput { code: Some(0), stdout: "{\"pid\":1}\n".into(), stderr: String::new() };
        assert_eq!(classify(&Subcommand::StatusJson, out).unwrap(), "{\"pid\":1}\n");
    }

    #[test]
    fn classify_maps_nonzero_exit_to_command_failed_with_stderr() {
        let out = RawOutput { code: Some(1), stdout: String::new(), stderr: "error: daemon not running\n".into() };
        let err = classify(&Subcommand::Stop, out).unwrap_err();
        match err.kind {
            HostErrorKind::CommandFailed { code, stderr } => {
                assert_eq!(code, Some(1));
                assert!(stderr.contains("daemon not running"));
            }
            other => panic!("unexpected {other:?}"),
        }
        assert!(err.detail.contains("stop"));
    }

    #[test]
    fn classify_maps_permission_denied_stderr() {
        let out = RawOutput { code: Some(1), stdout: String::new(), stderr: "Permission denied (os error 13)".into() };
        let err = classify(&Subcommand::Install, out).unwrap_err();
        assert!(matches!(err.kind, HostErrorKind::PermissionDenied));
    }
}
