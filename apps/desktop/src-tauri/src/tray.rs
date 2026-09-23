use tauri::menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, Wry};
use tauri_plugin_dialog::{DialogExt, MessageDialogButtons, MessageDialogKind};
use tauri_plugin_positioner::{Position, WindowExt};

use crate::commands::compute_host_state;
use crate::launchd::{HostState, InstallState};
use crate::sidecar::Subcommand;
use crate::state::{install_host, run_mutating, run_mutating_seq, stop_host, AppState, Settings};

pub struct TrayHandles {
    status: MenuItem<Wry>,
    start_stop: MenuItem<Wry>,
    autostart: CheckMenuItem<Wry>,
}

/// What a tray menu click should do, decided from the current host state.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TrayAction {
    /// Run the subcommand right away.
    Run(Subcommand),
    /// Ask the user first; the subcommand disconnects paired phones.
    Confirm(Subcommand),
}

pub fn status_label(s: &HostState) -> &'static str {
    match (&s.install, s.running) {
        (InstallState::NotInstalled, _) => "状态：未安装",
        (InstallState::PathMismatch { .. }, _) => "状态：需要修复",
        (InstallState::Installed, true) => "状态：运行中",
        (InstallState::Installed, false) => "状态：已停止",
    }
}

pub fn start_stop_label(s: &HostState) -> &'static str {
    if s.running {
        "停止主机服务"
    } else {
        "启动主机服务"
    }
}

pub fn autostart_checked(s: &HostState) -> bool {
    !matches!(s.install, InstallState::NotInstalled)
}

pub fn start_stop_action(s: &HostState) -> TrayAction {
    if s.running {
        TrayAction::Confirm(Subcommand::Stop)
    } else if matches!(s.install, InstallState::NotInstalled) {
        TrayAction::Run(Subcommand::Install)
    } else {
        TrayAction::Run(Subcommand::Restart)
    }
}

pub fn autostart_action(s: &HostState) -> TrayAction {
    match s.install {
        InstallState::NotInstalled => TrayAction::Run(Subcommand::Install),
        _ => TrayAction::Confirm(Subcommand::Uninstall),
    }
}

/// Open the console on launch until the service is installed, so a first
/// run is not just an icon lost in a crowded menu bar.
pub fn should_show_on_launch(install: &InstallState) -> bool {
    !matches!(install, InstallState::Installed)
}

/// After the app bundle was replaced the LaunchAgent still runs the old
/// binary; hand it over with `agentbuddy upgrade` when the version changed.
pub fn needs_upgrade(last_seen: Option<&str>, current: &str, install: &InstallState) -> bool {
    matches!(install, InstallState::Installed) && matches!(last_seen, Some(v) if v != current)
}

pub fn show_window(app: &AppHandle, page: Option<&str>) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.move_window(Position::TrayCenter);
        let _ = w.show();
        let _ = w.set_focus();
        if let Some(p) = page {
            let _ = app.emit("navigate", p);
        }
    }
}

/// Hide the console and stop any `logs -f` child (spec §7); the Logs page
/// hears `follow-stopped` and unchecks "跟随".
pub fn hide_window(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.hide();
    }
    let _ = crate::logs::follow_stop(app);
    let _ = app.emit("follow-stopped", ());
}

fn toggle_window(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        if w.is_visible().unwrap_or(false) {
            hide_window(app);
        } else {
            show_window(app, None);
        }
    }
}

pub fn apply(app: &AppHandle, state: &HostState) {
    if let Some(h) = app.try_state::<TrayHandles>() {
        let _ = h.status.set_text(status_label(state));
        let _ = h.start_stop.set_text(start_stop_label(state));
        let _ = h.autostart.set_checked(autostart_checked(state));
    }
}

async fn refresh(app: &AppHandle) {
    if let Ok(s) = compute_host_state(app).await {
        apply(app, &s);
    }
}

fn run_and_refresh(app: AppHandle, cmd: Subcommand) {
    tauri::async_runtime::spawn(async move {
        let state = app.state::<AppState>();
        let result = match cmd {
            Subcommand::Install => install_host(&app, &state).await,
            Subcommand::Stop => stop_host(&app, &state).await,
            other => run_mutating_seq(&app, &state, vec![other]).await,
        };
        if let Err(e) = result {
            app.dialog()
                .message(e.detail)
                .title("AgentBuddy")
                .kind(MessageDialogKind::Error)
                .show(|_| {});
        }
        refresh(&app).await;
    });
}

fn confirm_text(cmd: &Subcommand) -> (&'static str, &'static str) {
    match cmd {
        Subcommand::Uninstall => ("关闭开机自启", "这会卸载后台服务并停止它，手机将无法连接这台 Mac。"),
        _ => ("停止主机服务", "停止后手机将无法连接这台 Mac，直到你在这里再次启动，或下次登录 Mac 时服务自动恢复。"),
    }
}

/// Menu handlers run on the main thread, so dialogs use the callback form
/// (`show`), never `blocking_show`.
fn dispatch(app: &AppHandle, decide: fn(&HostState) -> TrayAction) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let Ok(current) = compute_host_state(&app).await else { return };
        match decide(&current) {
            TrayAction::Run(cmd) => run_and_refresh(app, cmd),
            TrayAction::Confirm(cmd) => {
                let (title, message) = confirm_text(&cmd);
                let handle = app.clone();
                app.dialog()
                    .message(message)
                    .title(title)
                    .kind(MessageDialogKind::Warning)
                    .buttons(MessageDialogButtons::OkCancelCustom("继续".into(), "取消".into()))
                    .show(move |ok| {
                        if ok {
                            run_and_refresh(handle, cmd);
                        } else {
                            // Undo the check-mark toggle the click already applied.
                            tauri::async_runtime::spawn(async move { refresh(&handle).await });
                        }
                    });
            }
        }
    });
}

fn quit(app: &AppHandle) {
    let mut settings = Settings::load(app);
    if settings.quit_notice_shown {
        app.exit(0);
        return;
    }
    settings.quit_notice_shown = true;
    let _ = settings.save(app);
    let handle = app.clone();
    app.dialog()
        .message("退出 AgentBuddy 不会停止主机服务，手机仍然可以连接这台 Mac。要停止服务请使用菜单里的「停止主机服务」。")
        .title("退出 AgentBuddy")
        .kind(MessageDialogKind::Info)
        .show(move |_| handle.exit(0));
}

pub fn spawn_refresher(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        loop {
            refresh(&app).await;
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });
}

pub fn spawn_upgrade_check(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let current = app.package_info().version.to_string();
        let mut settings = Settings::load(&app);
        if let Ok(s) = compute_host_state(&app).await {
            if should_show_on_launch(&s.install) {
                show_window(&app, Some("overview"));
            }
            if needs_upgrade(settings.last_seen_version.as_deref(), &current, &s.install) {
                let state = app.state::<AppState>();
                let _ = run_mutating(&app, &state, Subcommand::Upgrade).await;
            }
        }
        if settings.last_seen_version.as_deref() != Some(current.as_str()) {
            settings.last_seen_version = Some(current);
            let _ = settings.save(&app);
        }
        refresh(&app).await;
    });
}

pub fn install(app: &AppHandle) -> tauri::Result<()> {
    let status = MenuItem::with_id(app, "status", "状态：正在检测…", false, None::<&str>)?;
    let open = MenuItem::with_id(app, "open", "打开控制台", true, None::<&str>)?;
    let pair = MenuItem::with_id(app, "pair", "显示配对二维码", true, None::<&str>)?;
    let start_stop = MenuItem::with_id(app, "start_stop", "启动主机服务", true, None::<&str>)?;
    let autostart = CheckMenuItem::with_id(app, "autostart", "开机自启", true, false, None::<&str>)?;
    let quit_item = MenuItem::with_id(app, "quit", "退出 AgentBuddy", true, None::<&str>)?;
    let menu = Menu::with_items(
        app,
        &[
            &status,
            &PredefinedMenuItem::separator(app)?,
            &open,
            &pair,
            &start_stop,
            &autostart,
            &PredefinedMenuItem::separator(app)?,
            &quit_item,
        ],
    )?;

    let mut builder = TrayIconBuilder::with_id("main")
        .tooltip("AgentBuddy")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => show_window(app, Some("overview")),
            "pair" => show_window(app, Some("pairing")),
            "start_stop" => dispatch(app, start_stop_action),
            "autostart" => dispatch(app, autostart_action),
            "quit" => quit(app),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            tauri_plugin_positioner::on_tray_event(tray.app_handle(), &event);
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                toggle_window(tray.app_handle());
            }
        });
    if let Some(icon) = app.default_window_icon().cloned() {
        builder = builder.icon(icon);
    }
    builder.build(app)?;

    app.manage(TrayHandles { status, start_stop, autostart });
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::launchd::{HostState, InstallState};

    fn state(install: InstallState, running: bool) -> HostState {
        HostState {
            install,
            running,
            status: None,
            status_error: None,
            install_blocked: None,
            app_version: "0.1.0".into(),
            sidecar_path: "/x".into(),
        }
    }

    #[test]
    fn labels_follow_the_state_machine() {
        let s = state(InstallState::NotInstalled, false);
        assert_eq!(status_label(&s), "状态：未安装");
        assert_eq!(start_stop_label(&s), "启动主机服务");
        assert!(!autostart_checked(&s));

        let s = state(InstallState::Installed, true);
        assert_eq!(status_label(&s), "状态：运行中");
        assert_eq!(start_stop_label(&s), "停止主机服务");
        assert!(autostart_checked(&s));

        let s = state(InstallState::Installed, false);
        assert_eq!(status_label(&s), "状态：已停止");
        assert_eq!(start_stop_label(&s), "启动主机服务");

        let s = state(InstallState::PathMismatch { plist_exe: "/old".into() }, true);
        assert_eq!(status_label(&s), "状态：需要修复");
        assert!(autostart_checked(&s));
    }

    #[test]
    fn console_opens_on_launch_until_the_service_is_installed() {
        assert!(should_show_on_launch(&InstallState::NotInstalled));
        assert!(should_show_on_launch(&InstallState::PathMismatch { plist_exe: "/old".into() }));
        assert!(!should_show_on_launch(&InstallState::Installed));
    }

    #[test]
    fn stop_confirmation_says_the_service_comes_back_at_next_login() {
        assert!(confirm_text(&Subcommand::Stop).1.contains("下次登录"));
    }

    #[test]
    fn upgrade_is_needed_only_when_installed_and_version_changed() {
        assert!(!needs_upgrade(None, "0.2.0", &InstallState::Installed));
        assert!(needs_upgrade(Some("0.1.0"), "0.2.0", &InstallState::Installed));
        assert!(!needs_upgrade(Some("0.2.0"), "0.2.0", &InstallState::Installed));
        assert!(!needs_upgrade(Some("0.1.0"), "0.2.0", &InstallState::NotInstalled));
    }

    #[test]
    fn start_stop_action_starts_when_stopped_and_asks_before_stopping() {
        assert_eq!(start_stop_action(&state(InstallState::Installed, false)), TrayAction::Run(Subcommand::Restart));
        assert_eq!(start_stop_action(&state(InstallState::Installed, true)), TrayAction::Confirm(Subcommand::Stop));
        assert_eq!(start_stop_action(&state(InstallState::NotInstalled, false)), TrayAction::Run(Subcommand::Install));
    }

    #[test]
    fn autostart_action_installs_or_asks_before_uninstalling() {
        assert_eq!(autostart_action(&state(InstallState::NotInstalled, false)), TrayAction::Run(Subcommand::Install));
        assert_eq!(autostart_action(&state(InstallState::Installed, true)), TrayAction::Confirm(Subcommand::Uninstall));
        assert_eq!(
            autostart_action(&state(InstallState::PathMismatch { plist_exe: "/old".into() }, false)),
            TrayAction::Confirm(Subcommand::Uninstall)
        );
    }
}
