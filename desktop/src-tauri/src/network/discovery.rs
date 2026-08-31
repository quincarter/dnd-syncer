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
    pub host: Option<String>,
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
        let local_ip = local_ip_address::local_ip().ok();
        let local_ip_str = local_ip.as_ref().map(|ip| ip.to_string());

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
            host: local_ip_str,
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

            let mut targets: Vec<SocketAddr> = vec!["255.255.255.255:47891".parse().unwrap()];
            if let Some(std::net::IpAddr::V4(v4)) = local_ip {
                let octets = v4.octets();
                if let Ok(subnet_bcast) = format!("{}.{}.{}.255:47891", octets[0], octets[1], octets[2]).parse() {
                    targets.push(subnet_bcast);
                }
            }

            let payload = serde_json::to_vec(&broadcast_beacon).unwrap_or_default();
            info!("Starting LAN Discovery beacon broadcast on UDP port 47891 to {:?}", targets);

            loop {
                for target in &targets {
                    let _ = socket.send_to(&payload, target).await;
                }
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        });
    }
}
