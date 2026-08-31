use log::info;
use super::{OsFocusAdapter, OsFocusState};

pub struct WindowsFocusAdapter;

impl WindowsFocusAdapter {
    pub fn new() -> Self {
        Self
    }
}

impl OsFocusAdapter for WindowsFocusAdapter {
    fn get_focus_mode(&self) -> OsFocusState {
        #[cfg(target_os = "windows")]
        {
            use std::process::Command;
            let ps_script = r#"
                try {
                    $val = (Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\Cache\DefaultAccount\$$windows.data.notifications.quiethourssettings\Current' -ErrorAction Stop).Data
                    if ($val -ne $null -and $val.Length -gt 18 -and $val[18] -ne 0) {
                        Write-Output "ACTIVE:Do Not Disturb"
                    } else {
                        Write-Output "INACTIVE"
                    }
                } catch {
                    Write-Output "INACTIVE"
                }
            "#;

            let output = Command::new("powershell")
                .args(["-NoProfile", "-NonInteractive", "-Command", ps_script])
                .output();

            if let Ok(out) = output {
                if out.status.success() {
                    let res = String::from_utf8_lossy(&out.stdout).trim().to_string();
                    if res.starts_with("ACTIVE") {
                        let mode_name = res.strip_prefix("ACTIVE:")
                            .unwrap_or("Do Not Disturb")
                            .to_string();
                        return OsFocusState::Active {
                            mode_name: Some(mode_name),
                        };
                    }
                }
            }
        }
        OsFocusState::Inactive
    }

    fn set_focus_mode(&self, enable: bool, mode_name: Option<&str>) -> Result<(), String> {
        let mode = mode_name.unwrap_or("Do Not Disturb");
        info!("Setting Windows Focus Assist to: enable={}, mode={}", enable, mode);
        #[cfg(target_os = "windows")]
        {
            use std::process::Command;
            let ps_script = format!(
                r#"
                New-BurntToastNotification -Text 'Focus Mode', 'Synced Focus mode: {} ({})' -ErrorAction SilentlyContinue
                "#,
                if enable { "Enabled" } else { "Disabled" },
                mode
            );

            let _ = Command::new("powershell")
                .args(["-NoProfile", "-NonInteractive", "-Command", &ps_script])
                .output();
        }
        Ok(())
    }
}
