/**
 * Protocol definitions for DND & Notification Sync
 * Used across Desktop (Tauri / Rust / TS) and Mobile (Android / Kotlin)
 */

export const PROTOCOL_VERSION = "1.0.0";
export const DEFAULT_PORT = 47890;
export const MDNS_SERVICE_TYPE = "_dndsync._tcp";

export type DeviceType = "android" | "macos" | "windows" | "linux";

export type DndMode = 
  | "OFF"             // Normal notifications
  | "PRIORITY_ONLY"   // Priority interruptions only (Starred contacts, repeat callers)
  | "TOTAL_SILENCE"   // Total silence (No sounds, alarms, or vibrations)
  | "ALARMS_ONLY";    // Alarms only

export interface DeviceInfo {
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  appVersion: string;
  protocolVersion: string;
  ipAddress?: string;
  port?: number;
}

export interface PairedDevice {
  deviceInfo: DeviceInfo;
  sessionToken: string;
  pairedAt: number;
  lastSeenAt: number;
}

export interface PairingRequest {
  deviceInfo: DeviceInfo;
  pin: string;
  publicKey?: string;
}

export interface PairingResponse {
  success: boolean;
  deviceId: string;
  sessionToken?: string;
  errorMessage?: string;
}

export interface NotificationActionItem {
  id: string;
  title: string;
  isReply: boolean;
  replyPlaceholder?: string;
}

export interface NotificationItem {
  id: string; // Unique notification key (e.g. Android sbn key or UUID)
  packageName: string;
  appName: string;
  title: string;
  text: string;
  subText?: string;
  timestamp: number;
  isOngoing: boolean;
  isClearable: boolean;
  category?: string;
  appIconBase64?: string;
  actions: NotificationActionItem[];
}

export type MessageType =
  // Handshake & Auth
  | "PAIR_REQUEST"
  | "PAIR_RESPONSE"
  | "AUTH_REQUEST"
  | "AUTH_RESPONSE"
  | "HEARTBEAT_PING"
  | "HEARTBEAT_PONG"
  
  // DND / Focus Mode
  | "DND_STATUS_UPDATE"
  | "SET_DND_REQUEST"
  | "SET_DND_RESPONSE"
  
  // Notifications
  | "NOTIFICATION_POSTED"
  | "NOTIFICATION_REMOVED"
  | "DISMISS_NOTIFICATION"
  | "TRIGGER_NOTIFICATION_ACTION"
  | "SEND_NOTIFICATION_REPLY"
  | "SYNC_ALL_NOTIFICATIONS_REQUEST"
  | "SYNC_ALL_NOTIFICATIONS_RESPONSE";

export interface SyncMessage<T = unknown> {
  id: string; // UUID v4
  type: MessageType;
  senderId: string;
  targetId?: string;
  timestamp: number;
  payload: T;
}

export interface DndStatusPayload {
  mode: DndMode;
  modeName?: string; // e.g. "Sleep", "Bedtime", "Work", "Personal", "Driving", "Do Not Disturb"
  isEnabled: boolean;
  sourceDevice: string;
  rawFilterCode?: number; // Android INTERRUPTION_FILTER_* code
}

export interface SetDndPayload {
  mode: DndMode;
  modeName?: string;
  enabled: boolean;
}

export interface NotificationPostedPayload {
  notification: NotificationItem;
}

export interface NotificationRemovedPayload {
  notificationId: string;
  packageName: string;
  reason?: "USER_DISMISSED" | "APP_CANCELLED" | "CLICKED";
}

export interface DismissNotificationPayload {
  notificationId: string;
  packageName: string;
}

export interface TriggerActionPayload {
  notificationId: string;
  actionId: string;
  packageName: string;
}

export interface SendReplyPayload {
  notificationId: string;
  actionId: string;
  packageName: string;
  replyText: string;
}

export interface SyncAllNotificationsPayload {
  notifications: NotificationItem[];
  dndStatus: DndStatusPayload;
}

export interface AppSettings {
  autoSyncDndBidirectional: boolean;
  muteDesktopWhenPhoneDnd: boolean;
  showNotificationToasts: boolean;
  launchAtStartup: boolean;
  ignoredPackages: string[];
  priorityOnlyPackages: string[];
}
