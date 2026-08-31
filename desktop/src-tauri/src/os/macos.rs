use std::process::Command;
use std::path::{Path, PathBuf};
use std::fs;
use log::{info, warn};
use super::{OsFocusAdapter, OsFocusState};

pub struct MacosFocusAdapter;

impl MacosFocusAdapter {
    pub fn new() -> Self {
        Self
    }

    /// Locates the getfocus CLI binary
    fn get_focus_cli_path() -> PathBuf {
        if let Ok(mut exe_dir) = std::env::current_exe() {
            exe_dir.pop();
            let bin_path = exe_dir.join("getfocus");
            if bin_path.exists() {
                return bin_path;
            }
            let bundled_path = exe_dir.join("../Resources/bin/getfocus");
            if bundled_path.exists() {
                return bundled_path;
            }
        }

        let local_bin = Path::new("src-tauri/bin/getfocus");
        if local_bin.exists() {
            return local_bin.to_path_buf();
        }

        let direct_bin = Path::new("bin/getfocus");
        if direct_bin.exists() {
            return direct_bin.to_path_buf();
        }

        PathBuf::from("/Users/quincarter/Documents/Dev/Anti-gravity-projects/dnd-syncer/desktop/src-tauri/bin/getfocus")
    }

    /// Runs getfocus CLI
    fn run_getfocus() -> Option<OsFocusState> {
        let cli_path = Self::get_focus_cli_path();
        if !cli_path.exists() {
            return None;
        }

        let out_file = "/tmp/dnd_syncer_current_focus.txt";
        let output = Command::new(&cli_path)
            .args(["-output", out_file])
            .output();

        if let Ok(out) = output {
            if out.status.success() {
                if let Ok(content) = fs::read_to_string(out_file) {
                    let trimmed = content.trim();
                    if trimmed.is_empty() || trimmed == "None" || trimmed == "off" {
                        return Some(OsFocusState::Inactive);
                    } else {
                        return Some(OsFocusState::Active {
                            mode_name: Some(trimmed.to_string()),
                        });
                    }
                }
            } else {
                let err_msg = String::from_utf8_lossy(&out.stderr);
                warn!("getfocus execution notice: {}", err_msg.trim());
            }
        }

        None
    }

    fn run_osascript(script: &str) -> Result<String, String> {
        let output = Command::new("osascript")
            .arg("-e")
            .arg(script)
            .output()
            .map_err(|e| format!("Failed to execute osascript: {}", e))?;

        if output.status.success() {
            Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
        } else {
            Err(String::from_utf8_lossy(&output.stderr).trim().to_string())
        }
    }
}

impl OsFocusAdapter for MacosFocusAdapter {
    fn get_focus_mode(&self) -> OsFocusState {
        // 1. Try getfocus CLI
        if let Some(state) = Self::run_getfocus() {
            return state;
        }

        // 2. Try Shortcuts "Get Current Focus"
        let sc_query = r#"
            tell application "Shortcuts Events"
                try
                    set res to (run shortcut "Get Current Focus") as text
                    if res is not "" and res is not "missing value" and res is not "None" and res is not "off" then
                        return "ACTIVE:" & res
                    else
                        return "INACTIVE"
                    end if
                on error err
                    return "ERR:" & err
                end try
            end tell
        "#;

        if let Ok(res) = Self::run_osascript(sc_query) {
            if res.starts_with("ACTIVE:") {
                let raw_title = res.trim_start_matches("ACTIVE:").trim();
                let title = if raw_title.is_empty() { "Do Not Disturb" } else { raw_title };
                return OsFocusState::Active {
                    mode_name: Some(title.to_string()),
                };
            } else if res == "INACTIVE" {
                return OsFocusState::Inactive;
            }
        }

        // 3. Fallback: Menu bar indicator check
        let mb_query = r#"
            tell application "System Events"
                try
                    tell process "ControlCenter"
                        set focusItems to (every menu bar item of menu bar 1 whose description is "Focus" or description contains "Focus" or description contains "Do Not Disturb")
                        if (count of focusItems) > 0 then
                            return "ACTIVE"
                        else
                            return "INACTIVE"
                        end if
                    end tell
                on error err
                    return "UNKNOWN:" & err
                end try
            end tell
        "#;

        if let Ok(res) = Self::run_osascript(mb_query) {
            if res == "ACTIVE" {
                return OsFocusState::Active {
                    mode_name: Some("Do Not Disturb".to_string()),
                };
            } else if res == "INACTIVE" {
                return OsFocusState::Inactive;
            }
        }

        OsFocusState::Unknown
    }

    fn set_focus_mode(&self, enable: bool, _mode_name: Option<&str>) -> Result<(), String> {
        let shortcut_name = if enable {
            "Turn On Do Not Disturb"
        } else {
            "Turn Off Do Not Disturb"
        };

        let script = format!(
            r#"
            tell application "Shortcuts Events"
                try
                    run shortcut "{}"
                    return "OK"
                on error
                    return "FAIL"
                end try
            end tell
            "#,
            shortcut_name
        );

        if let Ok(res) = Self::run_osascript(&script) {
            if res == "OK" {
                info!("Successfully executed '{}'", shortcut_name);
                return Ok(());
            }
        }

        Ok(())
    }
}
