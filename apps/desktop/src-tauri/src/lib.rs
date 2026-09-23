mod commands;
mod config;
pub mod error;
mod launchd;
mod logs;
mod shellenv;
mod sidecar;
mod state;
pub mod status;
mod tray;

use tauri::Manager;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_positioner::init())
        .manage(state::AppState::default())
        .setup(|app| {
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);
            tray::install(app.handle())?;
            tray::spawn_refresher(app.handle().clone());
            tray::spawn_upgrade_check(app.handle().clone());
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                tray::hide_window(window.app_handle());
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::host_state,
            commands::host_install,
            commands::host_uninstall,
            commands::host_start,
            commands::host_stop,
            commands::host_restart,
            commands::host_reload,
            commands::host_upgrade,
            commands::pair_payload,
            commands::rotate_token,
            commands::agent_settings,
            commands::agent_set_enabled,
            commands::agent_set_bin,
            commands::logs_tail,
            commands::logs_follow_start,
            commands::logs_follow_stop,
            commands::reveal_path,
        ])
        .run(tauri::generate_context!())
        .expect("error while running AgentBuddy");
}
