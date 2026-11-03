//! HotSpot attach-protocol injection for Linux and macOS (what `jcmd <pid> JVMTI.agent_load` does).
//!
//! 1. Create `.attach_pid<pid>` where the target JVM looks for it and send SIGQUIT.
//! 2. Wait for the UNIX socket `.java_pid<pid>`.
//! 3. Send `1\0load\0<abs lib>\0true\0\0` and read the status reply.
//!
//! The JVM then dlopens the library and calls `Agent_OnAttach`.

#![allow(dead_code)]

use std::fs;
use std::io::{Read, Write};
use std::os::unix::fs::MetadataExt;
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, Instant};

extern "C" {
    fn kill(pid: i32, sig: i32) -> i32;
    fn geteuid() -> u32;
}

const SIGQUIT: i32 = 3;
const SOCKET_WAIT: Duration = Duration::from_secs(10);
const POLL_INTERVAL: Duration = Duration::from_millis(50);
const REPLY_TIMEOUT: Duration = Duration::from_secs(30);

const IS_MACOS: bool = cfg!(target_os = "macos");

pub fn inject(pid: u32, lib: &Path) -> Result<(), String> {
    let lib = fs::canonicalize(lib)
        .map_err(|e| format!("library '{}' not found: {e}", lib.display()))?;
    let lib_str = lib
        .to_str()
        .ok_or_else(|| "library path is not valid UTF-8".to_string())?;

    preflight_owner(pid)?;
    if let Some(msg) = arch_mismatch(pid) {
        return Err(msg);
    }

    let sockets = socket_candidates(pid);
    let existing = sockets.iter().find(|p| is_socket(p)).cloned();

    let sock_path = match existing {
        Some(p) => p,
        None => trigger_attach(pid, &sockets)?,
    };

    send_load(&sock_path, lib_str)
}

// ---------------------------------------------------------------- preflight

fn current_uid() -> u32 {
    unsafe { geteuid() }
}

fn preflight_owner(pid: u32) -> Result<(), String> {
    let uid = if IS_MACOS {
        let out = Command::new("ps")
            .args(["-o", "uid=", "-p", &pid.to_string()])
            .output()
            .map_err(|e| format!("failed to run ps: {e}"))?;
        let text = String::from_utf8_lossy(&out.stdout);
        let text = text.trim();
        if text.is_empty() {
            return Err(format!("process {pid} does not exist"));
        }
        text.parse::<u32>()
            .map_err(|_| format!("could not parse owner of process {pid}: '{text}'"))?
    } else {
        match fs::metadata(format!("/proc/{pid}")) {
            Ok(m) => m.uid(),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                return Err(format!("process {pid} does not exist"))
            }
            Err(e) => return Err(format!("cannot inspect process {pid}: {e}")),
        }
    };
    let me = current_uid();
    if uid != me {
        return Err(format!(
            "process {pid} belongs to uid {uid} but the injector runs as uid {me}; \
             the attach protocol only works for the same user (run the injector as the user that started the game)"
        ));
    }
    Ok(())
}

fn host_arch() -> &'static str {
    match std::env::consts::ARCH {
        "aarch64" | "arm64" => "arm64",
        other => other,
    }
}

/// Some(message) only on a definite mismatch; None when matching or undeterminable.
fn arch_mismatch(pid: u32) -> Option<String> {
    let target = target_arch(pid)?;
    if target == host_arch() {
        return None;
    }
    let hint = if IS_MACOS && target == "x86_64" && host_arch() == "arm64" {
        " (Rosetta?)"
    } else {
        ""
    };
    Some(format!(
        "target JVM is {target}{hint} but this injector is {}; use a matching JVM or a matching injector build",
        host_arch()
    ))
}

fn target_arch(pid: u32) -> Option<String> {
    if IS_MACOS {
        let out = Command::new("ps")
            .args(["-o", "comm=", "-p", &pid.to_string()])
            .output()
            .ok()?;
        let comm = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if comm.is_empty() {
            return None;
        }
        let head = read_head(Path::new(&comm), 64)?;
        macho_arch(&head).map(str::to_string)
    } else {
        // Prefer the libjvm mapped into the process, fall back to the executable.
        let mut candidates: Vec<PathBuf> = Vec::new();
        if let Ok(maps) = fs::read_to_string(format!("/proc/{pid}/maps")) {
            for line in maps.lines() {
                if line.contains("libjvm.so") {
                    if let Some(path) = line.split_whitespace().nth(5) {
                        candidates.push(PathBuf::from(path));
                        break;
                    }
                }
            }
        }
        candidates.push(PathBuf::from(format!("/proc/{pid}/exe")));
        for c in candidates {
            if let Some(head) = read_head(&c, 64) {
                if let Some(a) = elf_arch(&head) {
                    return Some(a.to_string());
                }
            }
        }
        None
    }
}

fn read_head(path: &Path, n: usize) -> Option<Vec<u8>> {
    let mut f = fs::File::open(path).ok()?;
    let mut buf = vec![0u8; n];
    let mut got = 0;
    while got < n {
        match f.read(&mut buf[got..]) {
            Ok(0) => break,
            Ok(k) => got += k,
            Err(_) => return None,
        }
    }
    buf.truncate(got);
    Some(buf)
}

/// Architecture name ("x86_64", "arm64", ...) from an ELF header, None if not ELF/unknown.
fn elf_arch(h: &[u8]) -> Option<&'static str> {
    if h.len() < 20 || &h[0..4] != b"\x7fELF" {
        return None;
    }
    let machine = match h[5] {
        1 => u16::from_le_bytes([h[18], h[19]]),
        2 => u16::from_be_bytes([h[18], h[19]]),
        _ => return None,
    };
    Some(match machine {
        3 => "x86",
        40 => "arm",
        62 => "x86_64",
        183 => "arm64",
        243 => "riscv",
        21 => "ppc64",
        _ => return None,
    })
}

/// Architecture from a thin 64-bit Mach-O header. Fat/universal binaries return None
/// (the running slice cannot be determined from the file alone).
fn macho_arch(h: &[u8]) -> Option<&'static str> {
    if h.len() < 8 {
        return None;
    }
    let magic = u32::from_le_bytes([h[0], h[1], h[2], h[3]]);
    let cputype = match magic {
        0xfeed_facf => u32::from_le_bytes([h[4], h[5], h[6], h[7]]),
        0xcffa_edfe => u32::from_be_bytes([h[4], h[5], h[6], h[7]]),
        _ => return None,
    };
    match cputype {
        0x0100_0007 => Some("x86_64"),
        0x0100_000c => Some("arm64"),
        _ => None,
    }
}

// ------------------------------------------------------------ attach trigger

fn socket_candidates(pid: u32) -> Vec<PathBuf> {
    let name = format!(".java_pid{pid}");
    let mut v = Vec::new();
    if IS_MACOS {
        if let Some(t) = std::env::var_os("TMPDIR") {
            v.push(Path::new(&t).join(&name));
        }
    }
    let tmp = Path::new("/tmp").join(&name);
    if !v.contains(&tmp) {
        v.push(tmp);
    }
    v
}

fn is_socket(p: &Path) -> bool {
    use std::os::unix::fs::FileTypeExt;
    fs::symlink_metadata(p)
        .map(|m| m.file_type().is_socket())
        .unwrap_or(false)
}

/// Removes the trigger files when dropped, on every path.
struct TriggerFiles(Vec<PathBuf>);

impl Drop for TriggerFiles {
    fn drop(&mut self) {
        for p in &self.0 {
            let _ = fs::remove_file(p);
        }
    }
}

fn trigger_candidates(pid: u32) -> Vec<PathBuf> {
    let name = format!(".attach_pid{pid}");
    let mut v = Vec::new();
    if !IS_MACOS {
        if let Ok(cwd) = fs::read_link(format!("/proc/{pid}/cwd")) {
            v.push(cwd.join(&name));
        }
    }
    v.push(Path::new("/tmp").join(&name));
    if IS_MACOS {
        if let Some(t) = std::env::var_os("TMPDIR") {
            let p = Path::new(&t).join(&name);
            if !v.contains(&p) {
                v.push(p);
            }
        }
    }
    v
}

fn trigger_attach(pid: u32, sockets: &[PathBuf]) -> Result<PathBuf, String> {
    let mut created = Vec::new();
    for p in trigger_candidates(pid) {
        if fs::File::create(&p).is_ok() {
            created.push(p);
        }
    }
    if created.is_empty() {
        return Err(format!(
            "could not create .attach_pid{pid} in the target's working directory or /tmp"
        ));
    }
    let _guard = TriggerFiles(created);

    if unsafe { kill(pid as i32, SIGQUIT) } != 0 {
        return Err(format!(
            "failed to send SIGQUIT to {pid}: {}",
            std::io::Error::last_os_error()
        ));
    }

    let start = Instant::now();
    while start.elapsed() < SOCKET_WAIT {
        if let Some(p) = sockets.iter().find(|p| is_socket(p)) {
            return Ok(p.clone());
        }
        std::thread::sleep(POLL_INTERVAL);
    }

    if let Some(msg) = arch_mismatch(pid) {
        return Err(format!("attach socket never appeared; {msg}"));
    }
    Err(format!(
        "the JVM did not create its attach socket within {}s. Possible causes: the JVM was started with \
         -XX:+DisableAttachMechanism or -Xrs, the game runs in a sandbox (Flatpak/Snap) with a private /tmp, \
         or it belongs to a different user",
        SOCKET_WAIT.as_secs()
    ))
}

// ------------------------------------------------------------------ protocol

/// `1\0load\0<lib>\0true\0\0`: version, command, then three NUL-terminated args
/// (library path, "true" = absolute path, empty options).
fn build_request(lib: &str) -> Vec<u8> {
    let mut v = Vec::with_capacity(lib.len() + 16);
    v.extend_from_slice(b"1\0load\0");
    v.extend_from_slice(lib.as_bytes());
    v.extend_from_slice(b"\0true\0\0");
    v
}

/// First line is the integer status (0 = success); the rest is message text.
fn parse_response(data: &[u8]) -> Result<(), String> {
    let text = String::from_utf8_lossy(data);
    let (first, rest) = match text.split_once('\n') {
        Some((a, b)) => (a.trim(), b.trim()),
        None => (text.trim(), ""),
    };
    if first.is_empty() {
        return Err("JVM closed the attach connection without a reply".into());
    }
    match first.parse::<i32>() {
        Ok(0) => Ok(()),
        Ok(code) => {
            if rest.is_empty() {
                Err(format!("JVM rejected the load request (status {code})"))
            } else {
                Err(format!("JVM rejected the load request (status {code}): {rest}"))
            }
        }
        Err(_) => Err(format!("unexpected reply from JVM: {}", text.trim())),
    }
}

fn send_load(sock: &Path, lib: &str) -> Result<(), String> {
    let mut s = UnixStream::connect(sock).map_err(|e| {
        format!(
            "could not connect to attach socket {}: {e} (stale socket, or the JVM runs as another user)",
            sock.display()
        )
    })?;
    let _ = s.set_read_timeout(Some(REPLY_TIMEOUT));
    let _ = s.set_write_timeout(Some(REPLY_TIMEOUT));
    s.write_all(&build_request(lib))
        .map_err(|e| format!("failed to send attach request: {e}"))?;

    let mut buf = Vec::new();
    match s.read_to_end(&mut buf) {
        Ok(_) => {}
        // A partial reply is still parseable; a bare timeout with no data is not.
        Err(e) if buf.is_empty() => return Err(format!("no reply from JVM: {e}")),
        Err(_) => {}
    }
    parse_response(&buf)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn request_format() {
        assert_eq!(
            build_request("/opt/libprestige_native.so"),
            b"1\0load\0/opt/libprestige_native.so\0true\0\0".to_vec()
        );
    }

    #[test]
    fn response_ok_and_errors() {
        assert!(parse_response(b"0\n").is_ok());
        assert!(parse_response(b"0").is_ok());
        let e = parse_response(b"-1\nreturn code: 1\n").unwrap_err();
        assert!(e.contains("-1") && e.contains("return code: 1"));
        assert!(parse_response(b"").is_err());
        assert!(parse_response(b"garbage").is_err());
    }

    #[test]
    fn elf_headers() {
        let mut h = vec![0u8; 64];
        h[..4].copy_from_slice(b"\x7fELF");
        h[5] = 1;
        h[18..20].copy_from_slice(&62u16.to_le_bytes());
        assert_eq!(elf_arch(&h), Some("x86_64"));
        h[18..20].copy_from_slice(&183u16.to_le_bytes());
        assert_eq!(elf_arch(&h), Some("arm64"));
        assert_eq!(elf_arch(b"nope"), None);
    }

    #[test]
    fn macho_headers() {
        let mut h = vec![0xcf, 0xfa, 0xed, 0xfe];
        h.extend_from_slice(&0x0100_000cu32.to_le_bytes());
        assert_eq!(macho_arch(&h), Some("arm64"));
        let mut h = vec![0xcf, 0xfa, 0xed, 0xfe];
        h.extend_from_slice(&0x0100_0007u32.to_le_bytes());
        assert_eq!(macho_arch(&h), Some("x86_64"));
        // fat binary -> undeterminable
        assert_eq!(macho_arch(&[0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 2]), None);
    }
}
