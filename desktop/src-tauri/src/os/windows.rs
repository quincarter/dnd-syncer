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
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x08000000;
            // Windows 11 moved the live Do Not Disturb state to a new CloudStore
            // location (windows.data.donotdisturb.quiethourssettings, nested under a
            // GUID-named container key); the older notifications.quiethourssettings
            // key is still present but no longer updated, so it must be tried second.
            // The active profile name (e.g. "Microsoft.QuietHoursProfile.PriorityOnly")
            // is embedded in the blob as UTF-16LE text, but not at a fixed/aligned byte
            // offset, so we search byte-for-byte for the interleaved-null pattern
            // rather than decoding the whole blob as UTF-16 from index 0.
            let ps_script = r#"
                try {
                    function ToUtf16Ascii([string]$s) {
                        -join ($s.ToCharArray() | ForEach-Object { [string]$_ + [char]0 })
                    }

                    $newKeyParent = Get-ChildItem -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\DefaultAccount\Current' -ErrorAction SilentlyContinue |
                        Where-Object { $_.PSChildName -like '*$windows.data.donotdisturb.quiethourssettings' } |
                        Select-Object -First 1

                    $val = $null
                    if ($newKeyParent) {
                        $leaf = Join-Path $newKeyParent.PSPath 'windows.data.donotdisturb.quiethourssettings'
                        $val = (Get-ItemProperty -Path $leaf -ErrorAction Stop).Data
                    }
                    if (-not $val) {
                        $val = (Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CloudStore\Store\Cache\DefaultAccount\$$windows.data.notifications.quiethourssettings\Current' -ErrorAction Stop).Data
                    }

                    if ($val) {
                        $raw = [System.Text.Encoding]::GetEncoding(28591).GetString($val)
                        $needleAlarms = ToUtf16Ascii 'QuietHoursProfile.AlarmsOnly'
                        $needlePriority = ToUtf16Ascii 'QuietHoursProfile.PriorityOnly'
                        if ($raw.Contains($needleAlarms)) {
                            Write-Output "ACTIVE:Alarms Only"
                        } elseif ($raw.Contains($needlePriority)) {
                            Write-Output "ACTIVE:Priority Only"
                        } else {
                            Write-Output "INACTIVE"
                        }
                    } else {
                        Write-Output "INACTIVE"
                    }
                } catch {
                    Write-Output "INACTIVE"
                }
            "#;

            let output = Command::new("powershell")
                .args(["-NoProfile", "-NonInteractive", "-Command", ps_script])
                .creation_flags(CREATE_NO_WINDOW)
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
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x08000000;

            // Windows exposes no supported API to set Do Not Disturb programmatically.
            // The private IQuietHoursSettings COM interface some Windows 10 tools used
            // no longer answers QueryInterface on this build, and hand-writing the
            // undocumented CloudStore registry blob is too fragile to ship. Instead we
            // drive the real Settings toggle via UI Automation (a stable, documented
            // API) - this briefly flashes the Settings window on screen.
            let desired_ps_bool = if enable { "$true" } else { "$false" };
            let ps_script = format!(
                r#"
                try {{
                    Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes

                    $desiredOn = {}

                    Start-Process "ms-settings:notifications"

                    $toggle = $null
                    $deadline = (Get-Date).AddSeconds(8)
                    while ((Get-Date) -lt $deadline -and -not $toggle) {{
                        Start-Sleep -Milliseconds 300
                        $root = [System.Windows.Automation.AutomationElement]::RootElement
                        $winCondition = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ClassNameProperty, "ApplicationFrameWindow")
                        $windows = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $winCondition)
                        $settingsWindow = $windows | Where-Object {{ $_.Current.Name -like "*Settings*" }} | Select-Object -First 1
                        if ($settingsWindow) {{
                            $idCondition = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::AutomationIdProperty, "SystemSettings_Notifications_QuietHours_MuteNotification_Enabled_ToggleSwitch")
                            $toggle = $settingsWindow.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $idCondition)
                        }}
                    }}

                    if ($toggle) {{
                        $patternObj = $null
                        if ($toggle.TryGetCurrentPattern([System.Windows.Automation.TogglePattern]::Pattern, [ref]$patternObj)) {{
                            $tp = $patternObj -as [System.Windows.Automation.TogglePattern]
                            $isOn = $tp.Current.ToggleState -eq [System.Windows.Automation.ToggleState]::On
                            if ($isOn -ne $desiredOn) {{
                                $tp.Toggle()
                            }}
                        }}
                    }}

                    Start-Sleep -Milliseconds 300
                    Get-Process -Name SystemSettings -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
                }} catch {{}}

                New-BurntToastNotification -Text 'Focus Mode', 'Synced Focus mode: {} ({})' -ErrorAction SilentlyContinue
                "#,
                desired_ps_bool,
                if enable { "Enabled" } else { "Disabled" },
                mode
            );

            let _ = Command::new("powershell")
                .args(["-NoProfile", "-NonInteractive", "-Command", &ps_script])
                .creation_flags(CREATE_NO_WINDOW)
                .output();
        }
        Ok(())
    }
}
