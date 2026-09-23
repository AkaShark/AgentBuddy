use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::error::HostError;
use crate::status::StatusInfo;

pub const LABEL: &str = "com.akashark.agentbuddycli";

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum InstallState {
    NotInstalled,
    Installed,
    PathMismatch { plist_exe: String },
}

#[derive(Debug, Clone, Serialize)]
pub struct HostState {
    pub install: InstallState,
    pub running: bool,
    pub status: Option<StatusInfo>,
    pub app_version: String,
    pub sidecar_path: String,
}

pub fn plist_path() -> Result<PathBuf, HostError> {
    let home = dirs::home_dir().ok_or_else(|| HostError::config_invalid("no home directory"))?;
    Ok(home.join("Library").join("LaunchAgents").join(format!("{LABEL}.plist")))
}

pub fn read_program_path(plist: &Path) -> Result<Option<PathBuf>, HostError> {
    if !plist.exists() {
        return Ok(None);
    }
    let value = plist::Value::from_file(plist)
        .map_err(|e| HostError::parse_failed(format!("{}: {e}", plist.display())))?;
    let program = value
        .as_dictionary()
        .and_then(|d| d.get("ProgramArguments"))
        .and_then(|v| v.as_array())
        .and_then(|a| a.first())
        .and_then(|v| v.as_string())
        .ok_or_else(|| {
            HostError::parse_failed(format!("{}: ProgramArguments missing", plist.display()))
        })?;
    Ok(Some(PathBuf::from(program)))
}

fn normalize(p: &Path) -> PathBuf {
    std::fs::canonicalize(p).unwrap_or_else(|_| p.to_path_buf())
}

pub fn derive_install_state(plist_exe: Option<&Path>, sidecar: &Path) -> InstallState {
    match plist_exe {
        None => InstallState::NotInstalled,
        Some(exe) if normalize(exe) == normalize(sidecar) => InstallState::Installed,
        Some(exe) => InstallState::PathMismatch { plist_exe: exe.to_string_lossy().into_owned() },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn missing_plist_means_not_installed() {
        let sidecar = std::path::Path::new("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy");
        assert_eq!(derive_install_state(None, sidecar), InstallState::NotInstalled);
    }

    #[test]
    fn same_path_means_installed() {
        let dir = tempfile::tempdir().unwrap();
        let exe = dir.path().join("agentbuddy");
        fs::write(&exe, b"").unwrap();
        assert_eq!(derive_install_state(Some(&exe), &exe), InstallState::Installed);
    }

    #[test]
    fn different_path_means_mismatch_with_plist_path_reported() {
        let dir = tempfile::tempdir().unwrap();
        let ours = dir.path().join("new/agentbuddy");
        fs::create_dir_all(ours.parent().unwrap()).unwrap();
        fs::write(&ours, b"").unwrap();
        let theirs = dir.path().join("old/agentbuddy");
        let state = derive_install_state(Some(&theirs), &ours);
        assert_eq!(state, InstallState::PathMismatch { plist_exe: theirs.to_string_lossy().into_owned() });
    }

    #[test]
    fn derive_install_state_treats_symlink_as_match() {
        let dir = tempfile::tempdir().unwrap();
        let real = dir.path().join("Applications/AgentBuddy.app/Contents/MacOS/agentbuddy");
        fs::create_dir_all(real.parent().unwrap()).unwrap();
        fs::write(&real, b"").unwrap();
        let link = dir.path().join("link-agentbuddy");
        std::os::unix::fs::symlink(&real, &link).unwrap();
        assert_eq!(derive_install_state(Some(&link), &real), InstallState::Installed);
    }

    #[test]
    fn read_program_path_extracts_first_program_argument() {
        let dir = tempfile::tempdir().unwrap();
        let plist = dir.path().join("x.plist");
        fs::write(&plist, r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.akashark.agentbuddycli</string>
  <key>ProgramArguments</key><array><string>/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy</string><string>serve</string></array>
</dict></plist>"#).unwrap();
        let p = read_program_path(&plist).unwrap().unwrap();
        assert_eq!(p, std::path::PathBuf::from("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy"));
    }

    #[test]
    fn read_program_path_returns_none_when_file_missing() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_program_path(&dir.path().join("nope.plist")).unwrap(), None);
    }

    #[test]
    fn plist_path_is_under_library_launchagents() {
        let p = plist_path().unwrap();
        assert!(p.ends_with("Library/LaunchAgents/com.akashark.agentbuddycli.plist"));
    }
}
