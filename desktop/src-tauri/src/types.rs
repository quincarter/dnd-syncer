use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION: &str = "1.0.0";
pub const DEFAULT_PORT: u16 = 47890;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum DeviceType {
    Android,
    Macos,
    Windows,
    Linux,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[allow(non_camel_case_types)]
pub enum DndMode {
    OFF,
    PRIORITY_ONLY,
    TOTAL_SILENCE,
    ALARMS_ONLY,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceInfo {
    pub device_id: String,
    pub device_name: String,
    pub device_type: DeviceType,
    pub app_version: String,
    pub protocol_version: String,
    pub ip_address: Option<String>,
    pub port: Option<u16>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingRequest {
    pub device_info: DeviceInfo,
    pub pin: String,
    pub public_key: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PairingResponse {
    pub success: boolean_or_bool::Bool,
    pub device_id: String,
    pub session_token: Option<String>,
    pub error_message: Option<String>,
}

mod boolean_or_bool {
    pub type Bool = bool;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationActionItem {
    pub id: String,
    pub title: String,
    pub is_reply: bool,
    pub reply_placeholder: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationItem {
    pub id: String,
    pub package_name: String,
    pub app_name: String,
    pub title: String,
    pub text: String,
    pub sub_text: Option<String>,
    pub timestamp: i64,
    pub is_ongoing: bool,
    pub is_clearable: bool,
    pub category: Option<String>,
    pub app_icon_base64: Option<String>,
    #[serde(default)]
    pub actions: Vec<NotificationActionItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum MessageType {
    PairRequest,
    PairResponse,
    AuthRequest,
    AuthResponse,
    HeartbeatPing,
    HeartbeatPong,
    DndStatusUpdate,
    SetDndRequest,
    SetDndResponse,
    NotificationPosted,
    NotificationRemoved,
    DismissNotification,
    TriggerNotificationAction,
    SendNotificationReply,
    SyncAllNotificationsRequest,
    SyncAllNotificationsResponse,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncMessage {
    pub id: String,
    pub r#type: MessageType,
    pub sender_id: String,
    pub target_id: Option<String>,
    pub timestamp: i64,
    pub payload: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DndStatusPayload {
    pub mode: DndMode,
    pub mode_name: Option<String>,
    pub is_enabled: bool,
    pub source_device: String,
    pub raw_filter_code: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetDndPayload {
    pub mode: DndMode,
    pub mode_name: Option<String>,
    pub enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationPostedPayload {
    pub notification: NotificationItem,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationRemovedPayload {
    pub notification_id: String,
    pub package_name: String,
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DismissNotificationPayload {
    pub notification_id: String,
    pub package_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TriggerActionPayload {
    pub notification_id: String,
    pub action_id: String,
    pub package_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SendReplyPayload {
    pub notification_id: String,
    pub action_id: String,
    pub package_name: String,
    pub reply_text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncAllNotificationsPayload {
    pub notifications: Vec<NotificationItem>,
    pub dnd_status: DndStatusPayload,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    pub auto_sync_dnd_bidirectional: bool,
    pub mute_desktop_when_phone_dnd: bool,
    pub show_notification_toasts: bool,
    pub launch_at_startup: bool,
    pub ignored_packages: Vec<String>,
    pub priority_only_packages: Vec<String>,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            auto_sync_dnd_bidirectional: true,
            mute_desktop_when_phone_dnd: true,
            show_notification_toasts: true,
            launch_at_startup: false,
            ignored_packages: vec![
                "com.android.systemui".to_string(),
                "android".to_string(),
                "com.google.android.googlequicksearchbox".to_string(),
            ],
            priority_only_packages: Vec::new(),
        }
    }
}
