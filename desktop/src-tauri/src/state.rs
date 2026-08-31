use std::collections::HashMap;
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::{mpsc, RwLock};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Wry};
use tauri::menu::MenuItem;
use tauri::tray::TrayIcon;
use crate::types::{AppSettings, DeviceInfo, DndMode, DndStatusPayload, MessageType, NotificationItem, SetDndPayload, SyncMessage};
use crate::os::{get_os_adapter, OsFocusState};
use log::info;

#[derive(Clone)]
pub struct TrayHandles {
    pub tray: TrayIcon<Wry>,
    pub status_item: MenuItem<Wry>,
    pub toggle_item: MenuItem<Wry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairedDevice {
    pub device_info: DeviceInfo,
    pub session_token: String,
    pub paired_at: i64,
    pub last_seen_at: i64,
}

#[derive(Clone)]
pub struct AppState {
    pub device_id: String,
    pub device_name: String,
    pub pairing_pin: Arc<RwLock<String>>,
    pub paired_devices: Arc<RwLock<HashMap<String, PairedDevice>>>,
    pub active_connections: Arc<RwLock<HashMap<String, mpsc::UnboundedSender<tokio_tungstenite::tungstenite::Message>>>>,
    pub active_notifications: Arc<RwLock<Vec<NotificationItem>>>,
    pub phone_dnd_status: Arc<RwLock<Option<DndStatusPayload>>>,
    pub desktop_dnd_status: Arc<RwLock<bool>>,
    pub last_toggled_at: Arc<RwLock<Option<Instant>>>,
    pub settings: Arc<RwLock<AppSettings>>,
    pub app_handle: Arc<RwLock<Option<AppHandle>>>,
    pub tray: Arc<RwLock<Option<TrayHandles>>>,
}

impl AppState {
    pub fn new() -> Self {
        let config = crate::storage::load_config();

        Self {
            device_id: config.device_id,
            device_name: config.device_name,
            pairing_pin: Arc::new(RwLock::new(config.pairing_pin)),
            paired_devices: Arc::new(RwLock::new(config.paired_devices)),
            active_connections: Arc::new(RwLock::new(HashMap::new())),
            active_notifications: Arc::new(RwLock::new(Vec::new())),
            phone_dnd_status: Arc::new(RwLock::new(None)),
            desktop_dnd_status: Arc::new(RwLock::new(false)),
            last_toggled_at: Arc::new(RwLock::new(None)),
            settings: Arc::new(RwLock::new(config.settings)),
            app_handle: Arc::new(RwLock::new(None)),
            tray: Arc::new(RwLock::new(None)),
        }
    }

    pub async fn save_persistent_state(&self) {
        let pin = self.pairing_pin.read().await.clone();
        let settings = self.settings.read().await.clone();
        let paired = self.paired_devices.read().await.clone();

        let cfg = crate::storage::PersistentConfig {
            device_id: self.device_id.clone(),
            device_name: self.device_name.clone(),
            pairing_pin: pin,
            settings,
            paired_devices: paired,
        };

        crate::storage::save_config(&cfg);
    }

    pub async fn set_app_handle(&self, handle: AppHandle) {
        let mut guard = self.app_handle.write().await;
        *guard = Some(handle);
    }

    pub async fn set_tray_handles(&self, handles: TrayHandles) {
        let mut guard = self.tray.write().await;
        *guard = Some(handles);
    }

    /// Updates the tray's status line, toggle-item label, and (on Windows)
    /// hover tooltip to reflect the given Focus state. Called any time
    /// desktop_dnd_status changes, from whichever source triggered it (this
    /// app's own button, the host OS's own UI, or a phone-initiated change).
    pub async fn refresh_tray(&self, enabled: bool) {
        let status_text = if enabled { "Focus Mode is On" } else { "Focus Mode is Off" };
        let toggle_text = if enabled { "Turn Focus Off" } else { "Turn Focus On" };

        if let Some(handles) = self.tray.read().await.as_ref() {
            let _ = handles.status_item.set_text(status_text);
            let _ = handles.toggle_item.set_text(toggle_text);
            let _ = handles.tray.set_tooltip(Some(status_text));
        }
    }

    pub async fn emit_frontend_event<T: Serialize + Clone>(&self, event: &str, payload: T) {
        let guard = self.app_handle.read().await;
        if let Some(ref handle) = *guard {
            let _ = handle.emit(event, payload);
        }
    }

    /// Builds a SetDndRequest reflecting the desktop's current live OS focus
    /// state (not just whatever we last recorded), so a device that just
    /// connected converges to the truth regardless of whether that state was
    /// set via this app's own button, the host OS's own UI, or was already
    /// in place before the app (or that device) even started.
    pub fn current_dnd_sync_message(&self) -> SyncMessage {
        let (enabled, mode_name) = match get_os_adapter().get_focus_mode() {
            OsFocusState::Active { mode_name } => (true, mode_name),
            _ => (false, None),
        };
        let payload = SetDndPayload {
            mode: if enabled { DndMode::PRIORITY_ONLY } else { DndMode::OFF },
            mode_name,
            enabled,
        };
        SyncMessage {
            id: uuid::Uuid::new_v4().to_string(),
            r#type: MessageType::SetDndRequest,
            sender_id: self.device_id.clone(),
            target_id: None,
            timestamp: chrono::Utc::now().timestamp_millis(),
            payload: serde_json::to_value(payload).unwrap(),
        }
    }

    pub async fn send_to_device(&self, device_id: &str, message: &SyncMessage) -> Result<(), String> {
        let json_str = serde_json::to_string(message).map_err(|e| e.to_string())?;
        let connections = self.active_connections.read().await;

        if let Some(tx) = connections.get(device_id) {
            tx.send(tokio_tungstenite::tungstenite::Message::Text(json_str))
                .map_err(|e| format!("Failed to send message: {}", e))?;
            Ok(())
        } else {
            Err("Device not connected".to_string())
        }
    }

    pub async fn broadcast_message(&self, message: &SyncMessage) {
        self.broadcast_message_except(message, None).await;
    }

    /// Broadcasts to every connected device except `exclude_device_id` (the
    /// device that originated the change), so relaying a state update back
    /// to its source doesn't create an echo.
    pub async fn broadcast_message_except(&self, message: &SyncMessage, exclude_device_id: Option<&str>) {
        let json_str = match serde_json::to_string(message) {
            Ok(s) => s,
            Err(_) => return,
        };

        let connections = self.active_connections.read().await;
        for (id, tx) in connections.iter() {
            if Some(id.as_str()) == exclude_device_id {
                continue;
            }
            let _ = tx.send(tokio_tungstenite::tungstenite::Message::Text(json_str.clone()));
        }
    }

    /// Handles incoming DND update from Android phone
    pub async fn handle_phone_dnd_update(&self, payload: DndStatusPayload, source_device_id: &str) {
        info!("Phone DND updated: mode={:?}, is_enabled={}", payload.mode, payload.is_enabled);

        {
            let mut guard = self.phone_dnd_status.write().await;
            *guard = Some(payload.clone());
        }

        self.emit_frontend_event("phone_dnd_changed", &payload).await;

        let settings = self.settings.read().await.clone();
        if settings.auto_sync_dnd_bidirectional || settings.mute_desktop_when_phone_dnd {
            {
                let mut time_guard = self.last_toggled_at.write().await;
                *time_guard = Some(Instant::now());
            }
            let adapter = get_os_adapter();
            let _ = adapter.set_focus_mode(payload.is_enabled, payload.mode_name.as_deref());
            let mut desk_guard = self.desktop_dnd_status.write().await;
            *desk_guard = payload.is_enabled;
            self.emit_frontend_event("desktop_dnd_changed", payload.is_enabled).await;
            self.refresh_tray(payload.is_enabled).await;
        }

        // Relay to any other connected devices (e.g. a second paired phone)
        // so every device converges to the same state, not just this desktop.
        // Gated on the bidirectional-sync setting specifically, since
        // mute_desktop_when_phone_dnd is a narrower phone->this-desktop-only
        // preference that shouldn't fan a change out to other devices.
        if settings.auto_sync_dnd_bidirectional {
            let relay_payload = SetDndPayload {
                mode: payload.mode,
                mode_name: payload.mode_name,
                enabled: payload.is_enabled,
            };
            let relay_msg = SyncMessage {
                id: uuid::Uuid::new_v4().to_string(),
                r#type: MessageType::SetDndRequest,
                sender_id: self.device_id.clone(),
                target_id: None,
                timestamp: chrono::Utc::now().timestamp_millis(),
                payload: serde_json::to_value(relay_payload).unwrap(),
            };
            self.broadcast_message_except(&relay_msg, Some(source_device_id)).await;
        }
    }

    /// Adds or updates notification
    pub async fn add_notification(&self, item: NotificationItem) {
        let settings = self.settings.read().await;

        // Check if package is in ignored list
        if settings.ignored_packages.contains(&item.package_name) {
            return;
        }

        // Add to active notifications
        let mut notifs = self.active_notifications.write().await;
        // Remove existing notification with same ID if any
        notifs.retain(|n| n.id != item.id);
        notifs.insert(0, item.clone());

        // Keep maximum 50 active notifications
        if notifs.len() > 50 {
            notifs.truncate(50);
        }

        self.emit_frontend_event("notification_posted", &item).await;
    }

    /// Removes notification
    pub async fn remove_notification(&self, notif_id: &str) {
        let mut notifs = self.active_notifications.write().await;
        notifs.retain(|n| n.id != notif_id);
        self.emit_frontend_event("notification_removed", notif_id).await;
    }
}
