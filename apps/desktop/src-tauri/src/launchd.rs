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
    /// Set when `status --json` itself failed (e.g. invalid host.toml), so
    /// the console shows the problem instead of a healthy-looking "stopped".
    pub status_error: Option<HostError>,
    /// Why installing from this bundle location is refused, if it is.
    pub install_blocked: Option<String>,
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


/// How "stop the host service" is carried out.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StopPlan {
    /// Unload the LaunchAgent (`launchctl bootout`); the plist has
    /// KeepAlive=true, so a plain `agentbuddy stop` would be respawned.
    /// The service comes back on `restart`/`install` or at the next login.
    Bootout,
    /// No LaunchAgent: stop the stray daemon through its control socket.
    SidecarStop,
}

pub fn stop_plan(install: &InstallState) -> StopPlan {
    match install {
        InstallState::NotInstalled => StopPlan::SidecarStop,
        InstallState::Installed | InstallState::PathMismatch { .. } => StopPlan::Bootout,
    }
}

pub fn bootout_target(uid: u32) -> String {
    format!("gui/{uid}/{LABEL}")
}

/// `launchctl bootout` on a service that is not loaded is not an error here.
pub fn classify_bootout(code: Option<i32>, stderr: &str) -> Result<(), HostError> {
    let not_loaded = matches!(code, Some(3) | Some(113))
        || stderr.contains("No such process")
        || stderr.contains("Could not find");
    if code == Some(0) || not_loaded {
        return Ok(());
    }
    Err(HostError::command_failed("launchctl bootout", code, stderr.to_owned()))
}

pub fn bootout() -> Result<(), HostError> {
    let uid = unsafe { libc::getuid() };
    let out = std::process::Command::new("/bin/launchctl")
        .args(["bootout", &bootout_target(uid)])
        .output()?;
    classify_bootout(out.status.code(), &String::from_utf8_lossy(&out.stderr))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BundleLocation {
    Normal,
    /// Gatekeeper App Translocation: a random read-only mount that vanishes.
    Translocated,
    /// Launched straight from the mounted dmg.
    MountedVolume,
}

pub fn bundle_location(sidecar: &Path) -> BundleLocation {
    let s = sidecar.to_string_lossy();
    if s.contains("/AppTranslocation/") {
        BundleLocation::Translocated
    } else if s.starts_with("/Volumes/") {
        BundleLocation::MountedVolume
    } else {
        BundleLocation::Normal
    }
}

/// Refuse to write a LaunchAgent that points into a dmg or translocated copy:
/// after eject or reboot launchd would crash-loop on a missing binary.
pub fn install_guard(sidecar: &Path) -> Result<(), HostError> {
    match bundle_location(sidecar) {
        BundleLocation::Normal => Ok(()),
        BundleLocation::Translocated | BundleLocation::MountedVolume => Err(HostError::install_location(format!(
            "AgentBuddy 正在从磁盘映像或临时隔离位置运行（{}）。请先把 AgentBuddy 拖进「应用程序」文件夹，从那里打开后再安装后台服务。",
            sidecar.display()
        ))),
    }
}

/// `pair` starts a detached daemon when none runs; only pair against the
/// LaunchAgent-managed one so the phone keeps working after a reboot.
pub fn pair_guard(install: &InstallState) -> Result<(), HostError> {
    match install {
        InstallState::Installed => Ok(()),
        _ => Err(HostError::not_installed("请先安装（或修复）后台服务，再显示配对二维码。")),
    }
}

/// Install state of this bundle's sidecar right now.
pub fn current_install_state() -> Result<InstallState, HostError> {
    let sidecar = crate::sidecar::sidecar_path()?;
    let plist_exe = read_program_path(&plist_path()?)?;
    Ok(derive_install_state(plist_exe.as_deref(), &sidecar))
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
    fn stop_plan_boots_out_installed_services_and_stops_stray_daemons() {
        // KeepAlive=true in the plist: `agentbuddy stop` alone is respawned by launchd.
        assert_eq!(stop_plan(&InstallState::Installed), StopPlan::Bootout);
        assert_eq!(stop_plan(&InstallState::PathMismatch { plist_exe: "/old".into() }), StopPlan::Bootout);
        assert_eq!(stop_plan(&InstallState::NotInstalled), StopPlan::SidecarStop);
    }

    #[test]
    fn bootout_target_names_the_gui_domain_label() {
        assert_eq!(bootout_target(501), "gui/501/com.akashark.agentbuddycli");
    }

    #[test]
    fn bootout_treats_not_loaded_as_success() {
        assert!(classify_bootout(Some(0), "").is_ok());
        assert!(classify_bootout(Some(3), "Boot-out failed: 3: No such process").is_ok());
        assert!(classify_bootout(Some(113), "Could not find specified service").is_ok());
        assert!(classify_bootout(Some(5), "Boot-out failed: 5: Input/output error").is_err());
    }

    #[test]
    fn bundle_location_flags_translocated_and_dmg_launches() {
        use std::path::Path;
        assert_eq!(bundle_location(Path::new("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy")), BundleLocation::Normal);
        assert_eq!(
            bundle_location(Path::new("/private/var/folders/ab/xyz/T/AppTranslocation/0F1E-22/d/AgentBuddy.app/Contents/MacOS/agentbuddy")),
            BundleLocation::Translocated
        );
        assert_eq!(bundle_location(Path::new("/Volumes/AgentBuddy/AgentBuddy.app/Contents/MacOS/agentbuddy")), BundleLocation::MountedVolume);
        assert_eq!(bundle_location(Path::new("/Users/me/src/apps/desktop/src-tauri/target/debug/agentbuddy")), BundleLocation::Normal);
    }

    #[test]
    fn install_guard_refuses_translocated_and_mounted_bundles() {
        use std::path::Path;
        assert!(install_guard(Path::new("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy")).is_ok());
        let e = install_guard(Path::new("/Volumes/AgentBuddy/AgentBuddy.app/Contents/MacOS/agentbuddy")).unwrap_err();
        assert!(matches!(e.kind, crate::error::HostErrorKind::InstallLocation));
        assert!(e.detail.contains("应用程序"));
        assert!(install_guard(Path::new("/private/var/folders/x/T/AppTranslocation/U/d/AgentBuddy.app/Contents/MacOS/agentbuddy")).is_err());
    }

    #[test]
    fn pairing_requires_an_installed_service() {
        // `pair` spawns a detached daemon when none runs; pairing to that
        // orphan would stop working at the next reboot.
        assert!(pair_guard(&InstallState::Installed).is_ok());
        let e = pair_guard(&InstallState::NotInstalled).unwrap_err();
        assert!(matches!(e.kind, crate::error::HostErrorKind::NotInstalled));
        assert!(pair_guard(&InstallState::PathMismatch { plist_exe: "/old".into() }).is_err());
    }

    #[test]
    fn plist_path_is_under_library_launchagents() {
        let p = plist_path().unwrap();
        assert!(p.ends_with("Library/LaunchAgents/com.akashark.agentbuddycli.plist"));
    }
}
