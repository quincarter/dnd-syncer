use std::collections::HashMap;
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::{mpsc, RwLock};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};
use crate::types::{AppSettings, DeviceInfo, DndStatusPayload, NotificationItem, SyncMessage};
use crate::os::get_os_adapter;
use log::info;

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
}

impl AppState {
    pub fn new() -> Self {
        let hostname = hostname::get()
            .map(|h| h.to_string_lossy().to_string())
            .unwrap_or_else(|_| "Desktop PC".to_string());

        let pin = format!("{:06}", rand::random::<u32>() % 1_000_000);

        Self {
            device_id: uuid::Uuid::new_v4().to_string(),
            device_name: hostname,
            pairing_pin: Arc::new(RwLock::new(pin)),
            paired_devices: Arc::new(RwLock::new(HashMap::new())),
            active_connections: Arc::new(RwLock::new(HashMap::new())),
            active_notifications: Arc::new(RwLock::new(Vec::new())),
            phone_dnd_status: Arc::new(RwLock::new(None)),
            desktop_dnd_status: Arc::new(RwLock::new(false)),
            last_toggled_at: Arc::new(RwLock::new(None)),
            settings: Arc::new(RwLock::new(AppSettings::default())),
            app_handle: Arc::new(RwLock::new(None)),
        }
    }

    pub async fn set_app_handle(&self, handle: AppHandle) {
        let mut guard = self.app_handle.write().await;
        *guard = Some(handle);
    }

    pub async fn emit_frontend_event<T: Serialize + Clone>(&self, event: &str, payload: T) {
        let guard = self.app_handle.read().await;
        if let Some(ref handle) = *guard {
            let _ = handle.emit(event, payload);
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
        let json_str = match serde_json::to_string(message) {
            Ok(s) => s,
            Err(_) => return,
        };

        let connections = self.active_connections.read().await;
        for (_id, tx) in connections.iter() {
            let _ = tx.send(tokio_tungstenite::tungstenite::Message::Text(json_str.clone()));
        }
    }

    /// Handles incoming DND update from Android phone
    pub async fn handle_phone_dnd_update(&self, payload: DndStatusPayload) {
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
