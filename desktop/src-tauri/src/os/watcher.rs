use std::time::Duration;
use tokio::time::sleep;
use log::info;
use serde_json::to_value;

use crate::state::AppState;
use crate::types::{DndMode, MessageType, SetDndPayload, SyncMessage};
use super::{get_os_adapter, OsFocusState};

pub struct OsFocusWatcher {
    state: AppState,
}

impl OsFocusWatcher {
    pub fn new(state: AppState) -> Self {
        Self { state }
    }

    /// Background polling loop to watch OS Focus mode changes from host OS Control Center / Settings
    pub async fn start(self) {
        let adapter = get_os_adapter();
        let (initial_state, initial_mode_name) = match adapter.get_focus_mode() {
            OsFocusState::Active { mode_name } => (true, mode_name),
            _ => (false, None),
        };

        {
            let mut desk_guard = self.state.desktop_dnd_status.write().await;
            *desk_guard = initial_state;
        }

        info!(
            "Starting host OS Focus Mode background watcher (initial state: active={}, mode={:?})",
            initial_state, initial_mode_name
        );

        tokio::spawn(async move {
            let mut last_mode_name = initial_mode_name;

            loop {
                // Responsive 500ms polling
                sleep(Duration::from_millis(500)).await;

                // 2000ms transition guard after explicit user in-app toggle
                {
                    let last_toggle = *self.state.last_toggled_at.read().await;
                    if let Some(t) = last_toggle {
                        if t.elapsed() < Duration::from_millis(2000) {
                            continue;
                        }
                    }
                }

                let focus_state = adapter.get_focus_mode();

                match focus_state {
                    OsFocusState::Active { mode_name } => {
                        let recorded_state = *self.state.desktop_dnd_status.read().await;
                        if !recorded_state || mode_name != last_mode_name {
                            info!("Host OS Focus changed externally -> ACTIVE ({:?})", mode_name);
                            last_mode_name = mode_name.clone();

                            {
                                let mut desk_guard = self.state.desktop_dnd_status.write().await;
                                *desk_guard = true;
                            }
                            self.state.emit_frontend_event("desktop_dnd_changed", true).await;
                            self.state.refresh_tray(true).await;

                            let settings = self.state.settings.read().await.clone();
                            if settings.auto_sync_dnd_bidirectional {
                                let payload = SetDndPayload {
                                    mode: DndMode::PRIORITY_ONLY,
                                    mode_name: mode_name.clone(),
                                    enabled: true,
                                };
                                let msg = SyncMessage {
                                    id: uuid::Uuid::new_v4().to_string(),
                                    r#type: MessageType::SetDndRequest,
                                    sender_id: self.state.device_id.clone(),
                                    target_id: None,
                                    timestamp: chrono::Utc::now().timestamp_millis(),
                                    payload: to_value(payload).unwrap(),
                                };
                                self.state.broadcast_message(&msg).await;
                            }
                        }
                    }
                    OsFocusState::Inactive => {
                        let recorded_state = *self.state.desktop_dnd_status.read().await;
                        if recorded_state {
                            info!("Host OS Focus changed externally -> INACTIVE");
                            last_mode_name = None;

                            {
                                let mut desk_guard = self.state.desktop_dnd_status.write().await;
                                *desk_guard = false;
                            }
                            self.state.emit_frontend_event("desktop_dnd_changed", false).await;
                            self.state.refresh_tray(false).await;

                            let settings = self.state.settings.read().await.clone();
                            if settings.auto_sync_dnd_bidirectional {
                                let payload = SetDndPayload {
                                    mode: DndMode::OFF,
                                    mode_name: None,
                                    enabled: false,
                                };
                                let msg = SyncMessage {
                                    id: uuid::Uuid::new_v4().to_string(),
                                    r#type: MessageType::SetDndRequest,
                                    sender_id: self.state.device_id.clone(),
                                    target_id: None,
                                    timestamp: chrono::Utc::now().timestamp_millis(),
                                    payload: to_value(payload).unwrap(),
                                };
                                self.state.broadcast_message(&msg).await;
                            }
                        }
                    }
                    OsFocusState::Unknown => {}
                }
            }
        });
    }
}
