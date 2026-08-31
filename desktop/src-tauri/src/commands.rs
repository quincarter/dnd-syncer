use tauri::State;
use serde::Serialize;
use serde_json::json;
use crate::state::{AppState, PairedDevice};
use crate::types::{
    AppSettings, DndMode, DndStatusPayload, MessageType,
    NotificationActionItem, NotificationItem, SetDndPayload, SyncMessage,
};
use crate::os::get_os_adapter;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppStateResponse {
    pub device_id: String,
    pub device_name: String,
    pub pairing_pin: String,
    pub local_ip: Option<String>,
    pub paired_devices: Vec<PairedDevice>,
    pub active_device_ids: Vec<String>,
    pub notifications: Vec<NotificationItem>,
    pub phone_dnd_status: Option<DndStatusPayload>,
    pub desktop_dnd_status: bool,
    pub has_full_disk_access: bool,
    pub settings: AppSettings,
}

fn check_full_disk_access() -> bool {
    #[cfg(target_os = "macos")]
    {
        if let Ok(home) = std::env::var("HOME") {
            let path = format!("{}/Library/DoNotDisturb/DB/Assertions.json", home);
            return std::fs::File::open(&path).is_ok();
        }
        false
    }
    #[cfg(not(target_os = "macos"))]
    true
}

#[tauri::command]
pub async fn get_state(state: State<'_, AppState>) -> Result<AppStateResponse, String> {
    let paired = state.paired_devices.read().await.values().cloned().collect();
    let active_ids = state.active_connections.read().await.keys().cloned().collect();
    let notifs = state.active_notifications.read().await.clone();
    let phone_dnd = state.phone_dnd_status.read().await.clone();
    
    // Check transition guard after user click (2000ms)
    let is_in_transition = {
        let last_cmd = *state.last_toggled_at.read().await;
        last_cmd.map(|t| t.elapsed() < std::time::Duration::from_millis(2000)).unwrap_or(false)
    };

    let live_dnd = if is_in_transition {
        *state.desktop_dnd_status.read().await
    } else {
        let adapter = get_os_adapter();
        let query_res = match adapter.get_focus_mode() {
            crate::os::OsFocusState::Active { .. } => true,
            crate::os::OsFocusState::Inactive => false,
            crate::os::OsFocusState::Unknown => *state.desktop_dnd_status.read().await,
        };
        {
            let mut desk_guard = state.desktop_dnd_status.write().await;
            *desk_guard = query_res;
        }
        query_res
    };

    let pin = state.pairing_pin.read().await.clone();
    let settings = state.settings.read().await.clone();
    let full_disk = check_full_disk_access();
    let local_ip = local_ip_address::local_ip().ok().map(|ip| ip.to_string());

    Ok(AppStateResponse {
        device_id: state.device_id.clone(),
        device_name: state.device_name.clone(),
        pairing_pin: pin,
        local_ip,
        paired_devices: paired,
        active_device_ids: active_ids,
        notifications: notifs,
        phone_dnd_status: phone_dnd,
        desktop_dnd_status: live_dnd,
        has_full_disk_access: full_disk,
        settings,
    })
}

#[tauri::command]
pub async fn regenerate_pin(state: State<'_, AppState>) -> Result<String, String> {
    let new_pin = format!("{:06}", rand::random::<u32>() % 1_000_000);
    {
        let mut guard = state.pairing_pin.write().await;
        *guard = new_pin.clone();
    }
    state.save_persistent_state().await;
    state.emit_frontend_event("pin_changed", &new_pin).await;
    Ok(new_pin)
}

#[tauri::command]
pub async fn toggle_dnd(enabled: bool, state: State<'_, AppState>) -> Result<(), String> {
    // 1. Broadcast DND change request to connected Android devices
    let dnd_mode = if enabled { DndMode::PRIORITY_ONLY } else { DndMode::OFF };
    let payload = SetDndPayload {
        mode: dnd_mode.clone(),
        mode_name: None,
        enabled,
    };

    let msg = SyncMessage {
        id: uuid::Uuid::new_v4().to_string(),
        r#type: MessageType::SetDndRequest,
        sender_id: state.device_id.clone(),
        target_id: None,
        timestamp: chrono::Utc::now().timestamp_millis(),
        payload: serde_json::to_value(payload).unwrap(),
    };

    state.broadcast_message(&msg).await;

    // 2. Immediately update state & transition timestamp
    {
        let mut time_guard = state.last_toggled_at.write().await;
        *time_guard = Some(std::time::Instant::now());
    }
    {
        let mut desk_guard = state.desktop_dnd_status.write().await;
        *desk_guard = enabled;
    }
    state.emit_frontend_event("desktop_dnd_changed", enabled).await;

    // 3. Run OS Focus change asynchronously in background worker to prevent UI & mouse lag
    tokio::task::spawn_blocking(move || {
        let adapter = get_os_adapter();
        let _ = adapter.set_focus_mode(enabled, None);
    });

    Ok(())
}

#[tauri::command]
pub async fn dismiss_notification(
    notification_id: String,
    package_name: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    // 1. Send dismiss message to Android
    let msg = SyncMessage {
        id: uuid::Uuid::new_v4().to_string(),
        r#type: MessageType::DismissNotification,
        sender_id: state.device_id.clone(),
        target_id: None,
        timestamp: chrono::Utc::now().timestamp_millis(),
        payload: json!({
            "notificationId": notification_id,
            "packageName": package_name
        }),
    };

    state.broadcast_message(&msg).await;

    // 2. Remove locally
    state.remove_notification(&notification_id).await;

    Ok(())
}

#[tauri::command]
pub async fn reply_notification(
    notification_id: String,
    action_id: String,
    package_name: String,
    reply_text: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let msg = SyncMessage {
        id: uuid::Uuid::new_v4().to_string(),
        r#type: MessageType::SendNotificationReply,
        sender_id: state.device_id.clone(),
        target_id: None,
        timestamp: chrono::Utc::now().timestamp_millis(),
        payload: json!({
            "notificationId": notification_id,
            "actionId": action_id,
            "packageName": package_name,
            "replyText": reply_text
        }),
    };

    state.broadcast_message(&msg).await;

    // Automatically remove or keep notification
    state.remove_notification(&notification_id).await;

    Ok(())
}

#[tauri::command]
pub async fn trigger_notification_action(
    notification_id: String,
    action_id: String,
    package_name: String,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let msg = SyncMessage {
        id: uuid::Uuid::new_v4().to_string(),
        r#type: MessageType::TriggerNotificationAction,
        sender_id: state.device_id.clone(),
        target_id: None,
        timestamp: chrono::Utc::now().timestamp_millis(),
        payload: json!({
            "notificationId": notification_id,
            "actionId": action_id,
            "packageName": package_name
        }),
    };

    state.broadcast_message(&msg).await;

    Ok(())
}

#[tauri::command]
pub async fn unpair_device(device_id: String, state: State<'_, AppState>) -> Result<(), String> {
    {
        let mut paired = state.paired_devices.write().await;
        paired.remove(&device_id);
    }
    {
        let mut conns = state.active_connections.write().await;
        conns.remove(&device_id);
    }

    state.save_persistent_state().await;
    state.emit_frontend_event("device_unpaired", device_id).await;
    Ok(())
}

#[tauri::command]
pub async fn update_settings(settings: AppSettings, state: State<'_, AppState>) -> Result<(), String> {
    {
        let mut s = state.settings.write().await;
        *s = settings.clone();
    }
    state.save_persistent_state().await;
    state.emit_frontend_event("settings_changed", settings).await;
    Ok(())
}

#[tauri::command]
pub fn start_drag(window: tauri::Window) -> Result<(), String> {
    window.start_dragging().map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn send_test_notification(state: State<'_, AppState>) -> Result<(), String> {
    let test_notif = NotificationItem {
        id: format!("test_{}", uuid::Uuid::new_v4()),
        package_name: "com.google.android.talk".to_string(),
        app_name: "Google Messages".to_string(),
        title: "Alex Morgan".to_string(),
        text: "Hey, are you free for lunch today?".to_string(),
        sub_text: Some("Mobile".to_string()),
        timestamp: chrono::Utc::now().timestamp_millis(),
        is_ongoing: false,
        is_clearable: true,
        category: Some("msg".to_string()),
        app_icon_base64: None,
        actions: vec![
            NotificationActionItem {
                id: "reply_0".to_string(),
                title: "Reply".to_string(),
                is_reply: true,
                reply_placeholder: Some("Type a reply...".to_string()),
            },
            NotificationActionItem {
                id: "read_1".to_string(),
                title: "Mark as read".to_string(),
                is_reply: false,
                reply_placeholder: None,
            },
        ],
    };

    state.add_notification(test_notif).await;
    Ok(())
}

#[tauri::command]
pub async fn open_macos_full_disk_access() -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        let _ = std::process::Command::new("open")
            .arg("x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles")
            .output();
    }
    Ok(())
}

#[tauri::command]
pub async fn setup_macos_shortcuts() -> Result<String, String> {
    #[cfg(target_os = "macos")]
    {
        use std::process::Command;

        let tmp_dir = std::env::temp_dir();
        let on_path = tmp_dir.join("Turn_On_Do_Not_Disturb.shortcut");

        let script = r#"tell application "Shortcuts" to activate"#;
        let _ = Command::new("osascript").arg("-e").arg(script).output();
        let _ = Command::new("open").arg(&on_path).output();

        return Ok("Opened Shortcuts setup".to_string());
    }
    #[allow(unreachable_code)]
    Ok("Not macOS".to_string())
}
