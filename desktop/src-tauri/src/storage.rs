use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::PathBuf;
use log::{info, warn, error};
use serde::{Deserialize, Serialize};
use crate::state::PairedDevice;
use crate::types::AppSettings;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PersistentConfig {
    pub device_id: String,
    pub device_name: String,
    pub pairing_pin: String,
    pub settings: AppSettings,
    #[serde(default)]
    pub paired_devices: HashMap<String, PairedDevice>,
}

impl PersistentConfig {
    pub fn new_default() -> Self {
        let hostname = hostname::get()
            .map(|h| h.to_string_lossy().to_string())
            .unwrap_or_else(|_| "Desktop PC".to_string());

        let pin = format!("{:06}", rand::random::<u32>() % 1_000_000);

        Self {
            device_id: uuid::Uuid::new_v4().to_string(),
            device_name: hostname,
            pairing_pin: pin,
            settings: AppSettings::default(),
            paired_devices: HashMap::new(),
        }
    }
}

pub fn get_storage_dir() -> PathBuf {
    #[cfg(target_os = "macos")]
    {
        if let Ok(home) = std::env::var("HOME") {
            let path = PathBuf::from(home).join("Library/Application Support/com.dndsync.desktop");
            let _ = fs::create_dir_all(&path);
            return path;
        }
    }

    #[cfg(not(target_os = "macos"))]
    {
        if let Ok(home) = std::env::var("HOME") {
            let path = PathBuf::from(home).join(".config/dnd-syncer");
            let _ = fs::create_dir_all(&path);
            return path;
        }
    }

    let tmp = std::env::temp_dir().join("dnd-syncer");
    let _ = fs::create_dir_all(&tmp);
    tmp
}

pub fn get_config_file_path() -> PathBuf {
    get_storage_dir().join("config.json")
}

pub fn load_config() -> PersistentConfig {
    let path = get_config_file_path();
    if path.exists() {
        match File::open(&path) {
            Ok(mut file) => {
                let mut contents = String::new();
                if file.read_to_string(&mut contents).is_ok() {
                    match serde_json::from_str::<PersistentConfig>(&contents) {
                        Ok(config) => {
                            info!("Loaded persistent configuration from {:?}", path);
                            return config;
                        }
                        Err(e) => {
                            warn!("Failed to parse config file {:?}: {}, creating new default", path, e);
                        }
                    }
                }
            }
            Err(e) => {
                warn!("Failed to open config file {:?}: {}", path, e);
            }
        }
    }

    let default_cfg = PersistentConfig::new_default();
    save_config(&default_cfg);
    default_cfg
}

pub fn save_config(config: &PersistentConfig) {
    let path = get_config_file_path();
    match serde_json::to_string_pretty(config) {
        Ok(json_str) => {
            match File::create(&path) {
                Ok(mut file) => {
                    if let Err(e) = file.write_all(json_str.as_bytes()) {
                        error!("Failed to write config file {:?}: {}", path, e);
                    } else {
                        info!("Saved persistent configuration to {:?}", path);
                    }
                }
                Err(e) => error!("Failed to create config file {:?}: {}", path, e),
            }
        }
        Err(e) => error!("Failed to serialize config: {}", e),
    }
}
