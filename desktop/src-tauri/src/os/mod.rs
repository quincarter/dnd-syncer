pub mod macos;
pub mod windows;
pub mod linux;
pub mod watcher;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OsFocusState {
    Active { mode_name: Option<String> },
    Inactive,
    Unknown,
}

pub trait OsFocusAdapter: Send + Sync {
    /// Get the current focus / DND mode and mode name of the host OS
    fn get_focus_mode(&self) -> OsFocusState;
    /// Enable or disable host OS focus / DND mode with specific mode name target
    fn set_focus_mode(&self, enable: bool, mode_name: Option<&str>) -> Result<(), String>;
}

/// Factory function to return the adapter for the current host OS
pub fn get_os_adapter() -> Box<dyn OsFocusAdapter> {
    #[cfg(target_os = "macos")]
    {
        Box::new(macos::MacosFocusAdapter::new())
    }
    #[cfg(target_os = "windows")]
    {
        Box::new(windows::WindowsFocusAdapter::new())
    }
    #[cfg(target_os = "linux")]
    {
        Box::new(linux::LinuxFocusAdapter::new())
    }
    #[cfg(not(any(target_os = "macos", target_os = "windows", target_os = "linux")))]
    {
        Box::new(DummyAdapter)
    }
}

#[allow(dead_code)]
struct DummyAdapter;
impl OsFocusAdapter for DummyAdapter {
    fn get_focus_mode(&self) -> OsFocusState {
        OsFocusState::Inactive
    }
    fn set_focus_mode(&self, _enable: bool, _mode_name: Option<&str>) -> Result<(), String> {
        Ok(())
    }
}
