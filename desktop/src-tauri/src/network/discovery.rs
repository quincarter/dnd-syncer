use std::net::SocketAddr;
use std::time::Duration;
use tokio::net::UdpSocket;
use log::{info, warn, error};
use serde::{Deserialize, Serialize};

pub const DISCOVERY_PORT: u16 = 47891;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DiscoveryBeacon {
    pub magic: String,
    pub device_id: String,
    pub device_name: String,
    pub device_type: String,
    pub ws_port: u16,
    pub protocol_version: String,
}

pub struct DiscoveryService {
    device_id: String,
    device_name: String,
    ws_port: u16,
}

impl DiscoveryService {
    pub fn new(device_id: String, device_name: String, ws_port: u16) -> Self {
        Self {
            device_id,
            device_name,
            ws_port,
        }
    }

    /// Run the discovery broadcaster and listener in the background
    pub async fn start(self) {
        let broadcast_beacon = DiscoveryBeacon {
            magic: "DND_SYNC_BEACON".to_string(),
            device_id: self.device_id.clone(),
            device_name: self.device_name.clone(),
            device_type: if cfg!(target_os = "macos") {
                "macos"
            } else if cfg!(target_os = "windows") {
                "windows"
            } else {
                "linux"
            }.to_string(),
            ws_port: self.ws_port,
            protocol_version: "1.0.0".to_string(),
        };

        // Spawn broadcast task
        tokio::spawn(async move {
            let socket = match UdpSocket::bind("0.0.0.0:0").await {
                Ok(s) => s,
                Err(e) => {
                    error!("Failed to bind UDP broadcast socket: {}", e);
                    return;
                }
            };

            if let Err(e) = socket.set_broadcast(true) {
                warn!("Failed to set UDP broadcast flag: {}", e);
            }

            let broadcast_target: SocketAddr = "255.255.255.255:47891".parse().unwrap();
            let payload = serde_json::to_vec(&broadcast_beacon).unwrap_or_default();

            info!("Starting LAN Discovery beacon broadcast on UDP port 47891");

            loop {
                let _ = socket.send_to(&payload, broadcast_target).await;
                tokio::time::sleep(Duration::from_secs(3)).await;
            }
        });
    }
}
