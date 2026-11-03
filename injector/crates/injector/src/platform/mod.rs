//! Platform backends of the injector. Each backend exposes the same functions:
//! enable_ansi, poll_key, java_processes, inject, open_url, restore_terminal.
use std::path::PathBuf;

/// A running Minecraft JVM shown in the picker.
pub struct JavaProcess {
    pub pid: u32,
    pub title: String,
    pub exe: String,
}

pub enum Key {
    Up,
    Down,
    Enter,
    Escape,
}

#[cfg(windows)]
mod win;
#[cfg(windows)]
pub use win::*;

#[cfg(unix)]
mod unix_attach;
#[cfg(unix)]
mod unix_procs;
#[cfg(unix)]
mod unix_term;
#[cfg(unix)]
pub use unix_attach::inject;
#[cfg(unix)]
pub use unix_procs::java_processes;
#[cfg(unix)]
pub use unix_term::{enable_ansi, open_url, poll_key, restore_terminal};

#[cfg(windows)]
pub const LIB_NAME: &str = "prestige_native.dll";
#[cfg(target_os = "macos")]
pub const LIB_NAME: &str = "libprestige_native.dylib";
#[cfg(all(unix, not(target_os = "macos")))]
pub const LIB_NAME: &str = "libprestige_native.so";

/// Per-user configuration directory: APPDATA, $XDG_CONFIG_HOME or ~/.config, ~/Library/Application Support.
pub fn config_dir() -> Option<PathBuf> {
    #[cfg(windows)]
    {
        std::env::var_os("APPDATA").map(PathBuf::from)
    }
    #[cfg(target_os = "macos")]
    {
        std::env::var_os("HOME").map(|h| PathBuf::from(h).join("Library").join("Application Support"))
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        match std::env::var_os("XDG_CONFIG_HOME") {
            Some(dir) if !dir.is_empty() => Some(PathBuf::from(dir)),
            _ => std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")),
        }
    }
}
