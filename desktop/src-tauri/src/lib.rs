pub mod types;
pub mod os;
pub mod network;
pub mod state;
pub mod commands;

use state::AppState;
use network::discovery::DiscoveryService;
use network::server::WsServer;
use types::DEFAULT_PORT;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    env_logger::init();
    let app_state = AppState::new();

    // Start UDP discovery and WebSocket server
    let discovery = DiscoveryService::new(
        app_state.device_id.clone(),
        app_state.device_name.clone(),
        DEFAULT_PORT,
    );
    let ws_server = WsServer::new(app_state.clone());

    tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_shell::init())
        .manage(app_state.clone())
        .setup(move |app| {
            let handle = app.handle().clone();
            let state_clone = app_state.clone();

            tauri::async_runtime::spawn(async move {
                state_clone.set_app_handle(handle).await;
                discovery.start().await;
                ws_server.start().await;
                let watcher = os::watcher::OsFocusWatcher::new(state_clone.clone());
                watcher.start().await;
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_state,
            commands::regenerate_pin,
            commands::toggle_dnd,
            commands::dismiss_notification,
            commands::reply_notification,
            commands::trigger_notification_action,
            commands::unpair_device,
            commands::update_settings,
            commands::send_test_notification,
            commands::start_drag,
            commands::setup_macos_shortcuts,
            commands::open_macos_full_disk_access,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
