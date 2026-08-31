use std::net::SocketAddr;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio_tungstenite::accept_async;
use tokio_tungstenite::tungstenite::Message;
use futures_util::{SinkExt, StreamExt};
use log::{error, info, warn};
use serde_json::json;

use crate::state::{AppState, PairedDevice};
use crate::types::{
    DndStatusPayload, MessageType,
    NotificationPostedPayload, NotificationRemovedPayload,
    PairingRequest, PairingResponse, SyncAllNotificationsPayload, SyncMessage,
    DEFAULT_PORT,
};

pub struct WsServer {
    state: AppState,
}

impl WsServer {
    pub fn new(state: AppState) -> Self {
        Self { state }
    }

    pub async fn start(self) {
        let addr = format!("0.0.0.0:{}", DEFAULT_PORT);
        let listener = match TcpListener::bind(&addr).await {
            Ok(l) => {
                info!("WebSocket Server listening on ws://{}", addr);
                l
            }
            Err(e) => {
                error!("Failed to bind WebSocket server on {}: {}", addr, e);
                return;
            }
        };

        let state = self.state;

        tokio::spawn(async move {
            while let Ok((stream, peer_addr)) = listener.accept().await {
                let state_clone = state.clone();
                tokio::spawn(async move {
                    handle_connection(state_clone, stream, peer_addr).await;
                });
            }
        });
    }
}

async fn handle_connection(state: AppState, stream: TcpStream, peer_addr: SocketAddr) {
    info!("Incoming TCP connection from {}", peer_addr);

    let ws_stream = match accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => {
            warn!("WebSocket handshake failed with {}: {}", peer_addr, e);
            return;
        }
    };

    info!("WebSocket connection established with {}", peer_addr);
    let (mut ws_sender, mut ws_receiver) = ws_stream.split();

    let (tx, mut rx) = mpsc::unbounded_channel::<Message>();
    let mut authenticated_device_id: Option<String> = None;

    // Outgoing message forwarding loop
    let send_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if ws_sender.send(msg).await.is_err() {
                break;
            }
        }
    });

    // Incoming message receiving loop
    while let Some(msg_res) = ws_receiver.next().await {
        let msg = match msg_res {
            Ok(m) => m,
            Err(e) => {
                warn!("WebSocket read error from {}: {}", peer_addr, e);
                break;
            }
        };

        match msg {
            Message::Text(text) => {
                if let Ok(sync_msg) = serde_json::from_str::<SyncMessage>(&text) {
                    handle_sync_message(&state, &sync_msg, &tx, &mut authenticated_device_id).await;
                }
            }
            Message::Ping(data) => {
                let _ = tx.send(Message::Pong(data));
            }
            Message::Close(_) => {
                info!("WebSocket connection closed by {}", peer_addr);
                break;
            }
            _ => {}
        }
    }

    send_task.abort();

    // Clean up active connection
    if let Some(dev_id) = authenticated_device_id {
        let mut conns = state.active_connections.write().await;
        conns.remove(&dev_id);
        state.emit_frontend_event("device_disconnected", dev_id.clone()).await;
        info!("Device {} disconnected", dev_id);
    }
}

async fn handle_sync_message(
    state: &AppState,
    msg: &SyncMessage,
    tx: &mpsc::UnboundedSender<Message>,
    authenticated_device_id: &mut Option<String>,
) {
    match msg.r#type {
        MessageType::PairRequest => {
            if let Ok(req) = serde_json::from_value::<PairingRequest>(msg.payload.clone()) {
                let current_pin = state.pairing_pin.read().await.clone();
                let is_valid = req.pin.trim() == current_pin.trim();

                let (success, session_token, err_msg) = if is_valid {
                    let token = uuid::Uuid::new_v4().to_string();
                    let paired = PairedDevice {
                        device_info: req.device_info.clone(),
                        session_token: token.clone(),
                        paired_at: chrono::Utc::now().timestamp_millis(),
                        last_seen_at: chrono::Utc::now().timestamp_millis(),
                    };

                    {
                        let mut paired_guard = state.paired_devices.write().await;
                        paired_guard.insert(req.device_info.device_id.clone(), paired.clone());
                    }
                    state.save_persistent_state().await;

                    let mut conns = state.active_connections.write().await;
                    conns.insert(req.device_info.device_id.clone(), tx.clone());
                    *authenticated_device_id = Some(req.device_info.device_id.clone());

                    state.emit_frontend_event("device_paired", &paired).await;
                    info!("Device paired successfully: {}", req.device_info.device_name);
                    (true, Some(token), None)
                } else {
                    (false, None, Some("Invalid pairing PIN code".to_string()))
                };

                let resp_payload = PairingResponse {
                    success,
                    device_id: state.device_id.clone(),
                    session_token,
                    error_message: err_msg,
                };

                let resp_msg = SyncMessage {
                    id: uuid::Uuid::new_v4().to_string(),
                    r#type: MessageType::PairResponse,
                    sender_id: state.device_id.clone(),
                    target_id: Some(msg.sender_id.clone()),
                    timestamp: chrono::Utc::now().timestamp_millis(),
                    payload: serde_json::to_value(resp_payload).unwrap(),
                };

                let _ = tx.send(Message::Text(serde_json::to_string(&resp_msg).unwrap()));

                // Push the desktop's current live DND state so the newly
                // paired device converges immediately, regardless of how
                // that state was set (this app's button, the host OS's own
                // UI, or already in place before pairing happened).
                if success {
                    let dnd_msg = state.current_dnd_sync_message();
                    let _ = tx.send(Message::Text(serde_json::to_string(&dnd_msg).unwrap()));
                }
            }
        }

        MessageType::AuthRequest => {
            // Verify session token
            let device_id = &msg.sender_id;
            let paired_guard = state.paired_devices.read().await;

            if let Some(paired) = paired_guard.get(device_id) {
                let mut conns = state.active_connections.write().await;
                conns.insert(device_id.clone(), tx.clone());
                *authenticated_device_id = Some(device_id.clone());

                state.emit_frontend_event("device_connected", paired.clone()).await;
                info!("Device authenticated: {}", paired.device_info.device_name);

                // Send Auth Response Success
                let auth_resp = SyncMessage {
                    id: uuid::Uuid::new_v4().to_string(),
                    r#type: MessageType::AuthResponse,
                    sender_id: state.device_id.clone(),
                    target_id: Some(device_id.clone()),
                    timestamp: chrono::Utc::now().timestamp_millis(),
                    payload: json!({ "success": true }),
                };
                let _ = tx.send(Message::Text(serde_json::to_string(&auth_resp).unwrap()));

                // Request full initial sync
                let sync_req = SyncMessage {
                    id: uuid::Uuid::new_v4().to_string(),
                    r#type: MessageType::SyncAllNotificationsRequest,
                    sender_id: state.device_id.clone(),
                    target_id: Some(device_id.clone()),
                    timestamp: chrono::Utc::now().timestamp_millis(),
                    payload: json!({}),
                };
                let _ = tx.send(Message::Text(serde_json::to_string(&sync_req).unwrap()));

                // Same as PairRequest: push current live desktop DND state
                // immediately on (re)connect.
                let dnd_msg = state.current_dnd_sync_message();
                let _ = tx.send(Message::Text(serde_json::to_string(&dnd_msg).unwrap()));
            } else {
                let auth_resp = SyncMessage {
                    id: uuid::Uuid::new_v4().to_string(),
                    r#type: MessageType::AuthResponse,
                    sender_id: state.device_id.clone(),
                    target_id: Some(device_id.clone()),
                    timestamp: chrono::Utc::now().timestamp_millis(),
                    payload: json!({ "success": false, "error": "Not paired" }),
                };
                let _ = tx.send(Message::Text(serde_json::to_string(&auth_resp).unwrap()));
            }
        }

        MessageType::HeartbeatPing => {
            let pong = SyncMessage {
                id: uuid::Uuid::new_v4().to_string(),
                r#type: MessageType::HeartbeatPong,
                sender_id: state.device_id.clone(),
                target_id: Some(msg.sender_id.clone()),
                timestamp: chrono::Utc::now().timestamp_millis(),
                payload: json!({}),
            };
            let _ = tx.send(Message::Text(serde_json::to_string(&pong).unwrap()));
        }

        MessageType::DndStatusUpdate => {
            if let Ok(dnd_payload) = serde_json::from_value::<DndStatusPayload>(msg.payload.clone()) {
                state.handle_phone_dnd_update(dnd_payload, &msg.sender_id).await;
            }
        }

        MessageType::NotificationPosted => {
            if let Ok(payload) = serde_json::from_value::<NotificationPostedPayload>(msg.payload.clone()) {
                state.add_notification(payload.notification).await;
            }
        }

        MessageType::NotificationRemoved => {
            if let Ok(payload) = serde_json::from_value::<NotificationRemovedPayload>(msg.payload.clone()) {
                state.remove_notification(&payload.notification_id).await;
            }
        }

        MessageType::SyncAllNotificationsResponse => {
            if let Ok(payload) = serde_json::from_value::<SyncAllNotificationsPayload>(msg.payload.clone()) {
                state.handle_phone_dnd_update(payload.dnd_status, &msg.sender_id).await;
                for notif in payload.notifications {
                    state.add_notification(notif).await;
                }
            }
        }

        _ => {}
    }
}
