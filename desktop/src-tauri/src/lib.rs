pub mod types;
pub mod os;
pub mod network;
pub mod state;
pub mod commands;
pub mod storage;

use state::{AppState, TrayHandles};
use network::discovery::DiscoveryService;
use network::server::WsServer;
use types::DEFAULT_PORT;
use tauri::{Manager, WindowEvent};
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};

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

            // Keep syncing in the background when the window is closed:
            // build a tray icon (menu bar item on macOS) with a live status
            // line, a toggle for the opposite of the current Focus state,
            // Show, and Quit. Intercepting the window close (below) to hide
            // rather than exit is what makes the background sync meaningful.
            let status_i = MenuItem::with_id(app, "status", "Focus Mode is Off", false, None::<&str>)?;
            let toggle_i = MenuItem::with_id(app, "toggle", "Turn Focus On", true, None::<&str>)?;
            let separator = PredefinedMenuItem::separator(app)?;
            let show_i = MenuItem::with_id(app, "show", "Show DND Syncer", true, None::<&str>)?;
            let quit_i = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&status_i, &toggle_i, &separator, &show_i, &quit_i])?;

            let menu_event_state = app_state.clone();
            let tray = TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .tooltip("Focus Mode is Off")
                .menu(&menu)
                .on_menu_event(move |app, event| match event.id.as_ref() {
                    "quit" => app.exit(0),
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.unminimize();
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                    "toggle" => {
                        let state = menu_event_state.clone();
                        tauri::async_runtime::spawn(async move {
                            let currently_enabled = *state.desktop_dnd_status.read().await;
                            commands::apply_toggle_dnd(&state, !currently_enabled).await;
                        });
                    }
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.unminimize();
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                })
                .build(app)?;

            tauri::async_runtime::spawn(async move {
                state_clone.set_app_handle(handle).await;
                state_clone
                    .set_tray_handles(TrayHandles { tray, status_item: status_i, toggle_item: toggle_i })
                    .await;
                let initial_enabled = matches!(
                    os::get_os_adapter().get_focus_mode(),
                    os::OsFocusState::Active { .. }
                );
                state_clone.refresh_tray(initial_enabled).await;

                discovery.start().await;
                ws_server.start().await;
                let watcher = os::watcher::OsFocusWatcher::new(state_clone.clone());
                watcher.start().await;
            });

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                // Hide instead of quitting so the sync server / OS-focus
                // watcher keep running; the tray menu's "Quit" item is the
                // only thing that actually exits the app.
                api.prevent_close();
                let _ = window.hide();
            }
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
