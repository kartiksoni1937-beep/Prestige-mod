//! Win32 plumbing of the injector (raw bindings, no external crates).
use std::ffi::c_void;
use std::os::windows::ffi::OsStrExt;
use std::path::Path;
use std::time::Duration;

use super::{JavaProcess, Key};

type Handle = *mut c_void;

const PROCESS_CREATE_THREAD: u32 = 0x0002;
const PROCESS_VM_OPERATION: u32 = 0x0008;
const PROCESS_VM_WRITE: u32 = 0x0020;
const PROCESS_VM_READ: u32 = 0x0010;
const PROCESS_QUERY_INFORMATION: u32 = 0x0400;
const PROCESS_QUERY_LIMITED_INFORMATION: u32 = 0x1000;
const MEM_COMMIT: u32 = 0x1000;
const MEM_RESERVE: u32 = 0x2000;
const MEM_RELEASE: u32 = 0x8000;
const PAGE_READWRITE: u32 = 0x04;
const TH32CS_SNAPMODULE: u32 = 0x8;
const TH32CS_SNAPMODULE32: u32 = 0x10;
const WAIT_OBJECT_0: u32 = 0;

#[repr(C)]
struct ModuleEntry32W {
    size: u32,
    module_id: u32,
    process_id: u32,
    glblcnt_usage: u32,
    proccnt_usage: u32,
    base_addr: *mut u8,
    base_size: u32,
    module: Handle,
    sz_module: [u16; 256],
    sz_exe_path: [u16; 260],
}

extern "system" {
    fn OpenProcess(access: u32, inherit: i32, pid: u32) -> Handle;
    fn CloseHandle(h: Handle) -> i32;
    fn VirtualAllocEx(process: Handle, address: *mut c_void, size: usize, alloc_type: u32, protect: u32) -> *mut c_void;
    fn VirtualFreeEx(process: Handle, address: *mut c_void, size: usize, free_type: u32) -> i32;
    fn WriteProcessMemory(process: Handle, address: *mut c_void, buf: *const c_void, size: usize, written: *mut usize) -> i32;
    fn CreateRemoteThread(process: Handle, attrs: *mut c_void, stack: usize, start: *mut c_void, param: *mut c_void, flags: u32, id: *mut u32) -> Handle;
    fn WaitForSingleObject(h: Handle, ms: u32) -> u32;
    fn GetExitCodeThread(h: Handle, code: *mut u32) -> i32;
    fn GetModuleHandleW(name: *const u16) -> Handle;
    fn GetProcAddress(module: Handle, name: *const u8) -> *mut c_void;
    fn IsWow64Process(process: Handle, wow64: *mut i32) -> i32;
    fn QueryFullProcessImageNameW(process: Handle, flags: u32, buf: *mut u16, size: *mut u32) -> i32;
    fn GetLastError() -> u32;
    fn CreateToolhelp32Snapshot(flags: u32, pid: u32) -> Handle;
    fn Module32FirstW(snapshot: Handle, entry: *mut ModuleEntry32W) -> i32;
    fn Module32NextW(snapshot: Handle, entry: *mut ModuleEntry32W) -> i32;
    fn GetStdHandle(id: u32) -> Handle;
    fn GetConsoleMode(h: Handle, mode: *mut u32) -> i32;
    fn SetConsoleMode(h: Handle, mode: u32) -> i32;
}

#[link(name = "user32")]
extern "system" {
    fn EnumWindows(callback: unsafe extern "system" fn(Handle, isize) -> i32, param: isize) -> i32;
    fn IsWindowVisible(hwnd: Handle) -> i32;
    fn GetWindowTextW(hwnd: Handle, buf: *mut u16, max: i32) -> i32;
    fn GetWindowThreadProcessId(hwnd: Handle, pid: *mut u32) -> u32;
}

#[link(name = "shell32")]
extern "system" {
    fn ShellExecuteW(hwnd: Handle, verb: *const u16, file: *const u16, params: *const u16, dir: *const u16, show: i32) -> isize;
}

extern "C" {
    fn _kbhit() -> i32;
    fn _getch() -> i32;
}

/// Nothing to restore on Windows (keys are read with _getch).
pub fn restore_terminal() {}

pub fn enable_ansi() {
    unsafe {
        let out = GetStdHandle(-11i32 as u32);
        let mut mode = 0;
        if GetConsoleMode(out, &mut mode) != 0 {
            SetConsoleMode(out, mode | 0x0004);
        }
    }
}

/// Non-blocking key read: arrows arrive as 0xE0/0x00 followed by 72 (up) / 80 (down).
pub fn poll_key() -> Option<Key> {
    unsafe {
        if _kbhit() == 0 {
            return None;
        }
        match _getch() {
            13 => Some(Key::Enter),
            27 => Some(Key::Escape),
            0 | 0xE0 => match _getch() {
                72 => Some(Key::Up),
                80 => Some(Key::Down),
                _ => None,
            },
            _ => None,
        }
    }
}

fn wide(path: &Path) -> Vec<u16> {
    path.as_os_str().encode_wide().chain(std::iter::once(0)).collect()
}

fn image_name(pid: u32) -> Option<String> {
    unsafe {
        let process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid);
        if process.is_null() {
            return None;
        }
        let mut buf = [0u16; 1024];
        let mut len = buf.len() as u32;
        let ok = QueryFullProcessImageNameW(process, 0, buf.as_mut_ptr(), &mut len);
        CloseHandle(process);
        if ok == 0 {
            return None;
        }
        Some(String::from_utf16_lossy(&buf[..len as usize]))
    }
}

unsafe extern "system" fn collect(hwnd: Handle, param: isize) -> i32 {
    let out = &mut *(param as *mut Vec<(u32, String)>);
    if IsWindowVisible(hwnd) == 0 {
        return 1;
    }
    let mut buf = [0u16; 256];
    let len = GetWindowTextW(hwnd, buf.as_mut_ptr(), buf.len() as i32);
    if len <= 0 {
        return 1;
    }
    let mut pid = 0;
    GetWindowThreadProcessId(hwnd, &mut pid);
    out.push((pid, String::from_utf16_lossy(&buf[..len as usize])));
    1
}

/// Visible top-level windows that belong to java.exe / javaw.exe, one entry per process.
pub fn java_processes() -> Vec<JavaProcess> {
    let mut windows: Vec<(u32, String)> = Vec::new();
    unsafe {
        EnumWindows(collect, &mut windows as *mut _ as isize);
    }
    let mut out: Vec<JavaProcess> = Vec::new();
    for (pid, title) in windows {
        if out.iter().any(|w| w.pid == pid) {
            continue;
        }
        let Some(path) = image_name(pid) else { continue };
        let exe = path.rsplit(['\\', '/']).next().unwrap_or("").to_string();
        if exe.eq_ignore_ascii_case("javaw.exe") || exe.eq_ignore_ascii_case("java.exe") {
            out.push(JavaProcess { pid, title, exe });
        }
    }
    out
}

fn last_error(what: &str) -> String {
    format!("{what} failed (error {})", unsafe { GetLastError() })
}

/// True when the module file name is loaded in the process.
fn module_loaded(pid: u32, file_name: &str) -> bool {
    unsafe {
        let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if snapshot.is_null() || snapshot as isize == -1 {
            return false;
        }
        let mut entry: ModuleEntry32W = std::mem::zeroed();
        entry.size = std::mem::size_of::<ModuleEntry32W>() as u32;
        let mut found = false;
        let mut ok = Module32FirstW(snapshot, &mut entry);
        while ok != 0 {
            let len = entry.sz_module.iter().position(|&c| c == 0).unwrap_or(entry.sz_module.len());
            if String::from_utf16_lossy(&entry.sz_module[..len]).eq_ignore_ascii_case(file_name) {
                found = true;
                break;
            }
            ok = Module32NextW(snapshot, &mut entry);
        }
        CloseHandle(snapshot);
        found
    }
}

/// LoadLibraryW(dll) in the target process through CreateRemoteThread.
pub fn inject(pid: u32, dll: &Path) -> Result<(), String> {
    unsafe {
        let access = PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ;
        let process = OpenProcess(access, 0, pid);
        if process.is_null() {
            return Err(last_error("OpenProcess (run as the same user, or as administrator)"));
        }
        let result = inject_into(process, pid, dll);
        CloseHandle(process);
        result
    }
}

unsafe fn inject_into(process: Handle, pid: u32, dll: &Path) -> Result<(), String> {
    let mut wow64 = 0;
    if IsWow64Process(process, &mut wow64) != 0 && wow64 != 0 {
        return Err("the target is a 32-bit JVM; only 64-bit JVMs are supported".into());
    }
    let file_name = dll.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    if module_loaded(pid, &file_name) {
        return Err(format!("{file_name} is already loaded in process {pid}"));
    }

    let path = wide(dll);
    let bytes = path.len() * 2;
    let remote = VirtualAllocEx(process, std::ptr::null_mut(), bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if remote.is_null() {
        return Err(last_error("VirtualAllocEx"));
    }
    let cleanup = |err: String| {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        Err(err)
    };
    if WriteProcessMemory(process, remote, path.as_ptr() as *const c_void, bytes, std::ptr::null_mut()) == 0 {
        return cleanup(last_error("WriteProcessMemory"));
    }

    // kernel32 is mapped at the same base in every process of a boot session, so the local address is valid remotely.
    let kernel32 = GetModuleHandleW(wide(Path::new("kernel32.dll")).as_ptr());
    let load_library = GetProcAddress(kernel32, c"LoadLibraryW".as_ptr() as *const u8);
    if load_library.is_null() {
        return cleanup(last_error("resolve LoadLibraryW"));
    }
    let thread = CreateRemoteThread(process, std::ptr::null_mut(), 0, load_library, remote, 0, std::ptr::null_mut());
    if thread.is_null() {
        return cleanup(last_error("CreateRemoteThread"));
    }
    let wait = WaitForSingleObject(thread, 30_000);
    let mut exit = 0;
    GetExitCodeThread(thread, &mut exit);
    CloseHandle(thread);
    if wait != WAIT_OBJECT_0 {
        // The thread may still be running inside the target; leave the buffer alone.
        return Err("remote LoadLibraryW did not finish within 30 seconds".into());
    }
    VirtualFreeEx(process, remote, 0, MEM_RELEASE);

    // LoadLibraryW's return value is truncated to 32 bits by GetExitCodeThread, so confirm through the module list.
    for _ in 0..20 {
        if module_loaded(pid, &file_name) {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    Err(format!("LoadLibraryW returned {exit:#x} but {file_name} is not mapped (missing dependency or blocked by security software?)"))
}

/// Opens a URL in the default web browser.
pub fn open_url(url: &str) -> bool {
    let verb: Vec<u16> = "open ".encode_utf16().collect();
    let target: Vec<u16> = url.encode_utf16().chain(std::iter::once(0)).collect();
    // ShellExecute returns a value greater than 32 on success
    unsafe { ShellExecuteW(std::ptr::null_mut(), verb.as_ptr(), target.as_ptr(), std::ptr::null(), std::ptr::null(), 1) > 32 }
}
