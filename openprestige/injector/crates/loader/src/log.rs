use std::ffi::c_void;
#[cfg(unix)]
use std::os::unix::ffi::OsStrExt;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::sync::OnceLock;
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(windows)]
extern "system" {
    fn GetModuleFileNameW(module: *mut c_void, buf: *mut u16, size: u32) -> u32;
}

#[cfg(unix)]
#[repr(C)]
struct DlInfo {
    dli_fname: *const std::ffi::c_char,
    dli_fbase: *mut c_void,
    dli_sname: *const std::ffi::c_char,
    dli_saddr: *mut c_void,
}

#[cfg(unix)]
extern "C" {
    fn dladdr(addr: *const c_void, info: *mut DlInfo) -> i32;
}

#[cfg(unix)]
fn module_path() -> PathBuf {
    fn anchor() {}
    let mut info = DlInfo { dli_fname: std::ptr::null(), dli_fbase: std::ptr::null_mut(), dli_sname: std::ptr::null(), dli_saddr: std::ptr::null_mut() };
    let ok = unsafe { dladdr(anchor as fn() as usize as *const c_void, &mut info) };
    if ok != 0 && !info.dli_fname.is_null() {
        let name = unsafe { std::ffi::CStr::from_ptr(info.dli_fname) };
        PathBuf::from(std::ffi::OsStr::from_bytes(name.to_bytes()))
    } else {
        std::env::temp_dir().join("libprestige_native")
    }
}

#[cfg(windows)]
fn module_path() -> PathBuf {
    let mut buf = [0u16; 1024];
    let len = unsafe { GetModuleFileNameW(crate::MODULE, buf.as_mut_ptr(), buf.len() as u32) } as usize;
    PathBuf::from(String::from_utf16_lossy(&buf[..len]))
}

fn log_path() -> &'static PathBuf {
    static PATH: OnceLock<PathBuf> = OnceLock::new();
    PATH.get_or_init(|| {
        module_path().with_file_name("prestige-native.log")
    })
}

/// Appends a line to prestige-native.log beside the DLL.
pub fn log(message: &str) {
    let secs = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(log_path()) {
        let _ = writeln!(file, "[{secs}] {message}");
    }
}

#[macro_export]
macro_rules! log {
    ($($arg:tt)*) => { $crate::log::log(&format!($($arg)*)) };
}
