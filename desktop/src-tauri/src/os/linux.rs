use std::process::Command;
use log::info;
use super::{OsFocusAdapter, OsFocusState};

pub struct LinuxFocusAdapter;

impl LinuxFocusAdapter {
    pub fn new() -> Self {
        Self
    }
}

impl OsFocusAdapter for LinuxFocusAdapter {
    fn get_focus_mode(&self) -> OsFocusState {
        // 1. GNOME show-banners setting
        let output = Command::new("gsettings")
            .args(["get", "org.gnome.desktop.notifications", "show-banners"])
            .output();

        if let Ok(out) = output {
            if out.status.success() {
                let res = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if res == "false" {
                    return OsFocusState::Active {
                        mode_name: Some("Do Not Disturb".to_string()),
                    };
                } else if res == "true" {
                    return OsFocusState::Inactive;
                }
            }
        }

        // 2. Dunst notification daemon
        let dunst_out = Command::new("dunstctl")
            .arg("is-paused")
            .output();

        if let Ok(out) = dunst_out {
            if out.status.success() {
                let res = String::from_utf8_lossy(&out.stdout).trim().to_string();
                if res == "true" {
                    return OsFocusState::Active {
                        mode_name: Some("Do Not Disturb".to_string()),
                    };
                }
            }
        }

        OsFocusState::Inactive
    }

    fn set_focus_mode(&self, enable: bool, mode_name: Option<&str>) -> Result<(), String> {
        let mode = mode_name.unwrap_or("Do Not Disturb");
        info!("Setting Linux DND to: enable={}, mode={}", enable, mode);

        let gnome_val = if enable { "false" } else { "true" };
        let _ = Command::new("gsettings")
            .args(["set", "org.gnome.desktop.notifications", "show-banners", gnome_val])
            .output();

        let dunst_val = if enable { "true" } else { "false" };
        let _ = Command::new("dunstctl")
            .args(["set-paused", dunst_val])
            .output();

        Ok(())
    }
}
