//! PrestigeInjector: lists the running Minecraft (javaw/java) processes and loads the native library
//! (prestige_native.dll / libprestige_native.so / libprestige_native.dylib) into the selected one: a remote LoadLibraryW
//! thread on Windows, the HotSpot attach protocol on Linux and macOS. The library does everything else once it is inside
//! the JVM; the injector tails its log so every stage (transforming, injecting, initialising) is visible.
//!
//!   PrestigeInjector                      interactive process picker (Up/Down, Enter, Esc)
//!   PrestigeInjector <pid> [lib path]     non-interactive
mod platform;

use std::fs;
use std::io::{self, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::time::{Duration, Instant};

/// The one credit line, shown once at the top of the screen.
const CREDIT: &str = "Cracked by Skid Solutions - https://discord.gg/x2FJyGrDNg";
const INVITE_URL: &str = "https://discord.gg/x2FJyGrDNg";
/// How long to follow the DLL's progress after it has been loaded.
const FOLLOW_TIMEOUT: Duration = Duration::from_secs(180);

const RESET: &str = "\x1b[0m";
const DIM: &str = "\x1b[90m";
const CYAN: &str = "\x1b[96m";
const GREEN: &str = "\x1b[92m";
const YELLOW: &str = "\x1b[93m";
const RED: &str = "\x1b[91m";

fn default_dll() -> PathBuf {
    std::env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(|dir| dir.join(platform::LIB_NAME)))
        .unwrap_or_else(|| PathBuf::from(platform::LIB_NAME))
}

fn banner() -> String {
    let mut out = String::new();
    out.push_str(&format!("{CYAN}  ___ ___ ___ ___ _____ ___ ___ ___ \n"));
    out.push_str(" | _ \\ _ \\ __/ __|_   _|_ _/ __| __|\n");
    out.push_str(" |  _/   / _|\\__ \\ | |  | | (_ | _| \n");
    out.push_str(&format!(" |_| |_|_\\___|___/ |_| |___\\___|___|{RESET}  injector\n"));
    out.push_str(&format!("{YELLOW}  {CREDIT}{RESET}\n\n"));
    out
}

fn step(label: &str, message: &str) {
    println!("{DIM}[{:>7}]{RESET} {CYAN}{label:<12}{RESET} {message}", clock());
}
fn ok(label: &str, message: &str) {
    println!("{DIM}[{:>7}]{RESET} {GREEN}{label:<12}{RESET} {message}", clock());
}
fn fail(label: &str, message: &str) {
    println!("{DIM}[{:>7}]{RESET} {RED}{label:<12}{RESET} {message}", clock());
}

/// Seconds since the injector started, for the log prefix.
fn clock() -> String {
    use std::sync::OnceLock;
    static START: OnceLock<Instant> = OnceLock::new();
    format!("{:.1}s", START.get_or_init(Instant::now).elapsed().as_secs_f32())
}

fn main() -> ExitCode {
    let code = real_main();
    platform::restore_terminal();
    code
}

fn real_main() -> ExitCode {
    platform::enable_ansi();
    let _ = clock();
    let args: Vec<String> = std::env::args().skip(1).collect();
    let interactive = args.is_empty();

    let (pid, dll): (Option<u32>, PathBuf) = match args.as_slice() {
        [] => {
            open_invite_once();
            (pick_process(), default_dll())
        }
        [pid] => (parse_pid(pid), default_dll()),
        [pid, dll] => (parse_pid(pid), PathBuf::from(dll)),
        _ => {
            eprintln!("usage: PrestigeInjector [<pid> [lib path]]");
            return ExitCode::from(2);
        }
    };
    let Some(pid) = pid else {
        // interactive mode quit with Esc, or an unparsable pid was given
        return if interactive { ExitCode::SUCCESS } else { ExitCode::from(2) };
    };
    if !interactive {
        print!("{}", banner());
    }

    let code = run(pid, &dll);
    if interactive {
        print!("\nPress Enter to close...");
        let _ = io::stdout().flush();
        let mut line = String::new();
        let _ = io::stdin().read_line(&mut line);
    }
    code
}

fn parse_pid(text: &str) -> Option<u32> {
    text.parse().ok()
}

fn run(pid: u32, dll: &Path) -> ExitCode {
    println!();
    step("target", &format!("process {pid}"));
    let dll = match dll.canonicalize() {
        Ok(p) => p,
        Err(e) => {
            fail("error", &format!("cannot find {}: {e}", dll.display()));
            return ExitCode::from(1);
        }
    };
    let dll_display = dll.display().to_string();
    #[cfg(windows)]
    let dll_display = dll_display.trim_start_matches(r"\\?\").to_string();
    step("loader", &dll_display);

    // The library appends to prestige-native.log beside itself; remember where it ends so only this run's lines are shown.
    let log_path = dll.with_file_name("prestige-native.log");
    let log_start = fs::metadata(&log_path).map(|m| m.len()).unwrap_or(0);

    step("checking", "64-bit JVM, process access, library not already loaded");
    step("injecting", "loading the native library into the JVM");
    match platform::inject(pid, &dll) {
        Ok(()) => ok("injected", &format!("{} is loaded in the game process", platform::LIB_NAME)),
        Err(e) => {
            fail("failed", &e);
            return ExitCode::from(1);
        }
    }

    step("transforming", "following the loader log (attach, class loader, hook transformer)...");
    match follow_log(&log_path, log_start) {
        Outcome::Done => {
            println!();
            ok("success", "successfully injected - Prestige is running in the game");
            ExitCode::SUCCESS
        }
        Outcome::Failed(line) => {
            fail("failed", &format!("injection did not complete: {line}"));
            println!("{DIM}  details: {} and the game's latest.log{RESET}", log_path.display());
            ExitCode::from(1)
        }
        Outcome::TimedOut => {
            fail("timeout", "no completion message from the loader within 3 minutes");
            println!("{DIM}  check {} and the game's latest.log{RESET}", log_path.display());
            ExitCode::from(1)
        }
    }
}

enum Outcome {
    Done,
    Failed(String),
    TimedOut,
}

/// Prints the lines the loader/bootstrap append to the log until they report success or failure.
fn follow_log(path: &Path, start: u64) -> Outcome {
    let deadline = Instant::now() + FOLLOW_TIMEOUT;
    let mut offset = start;
    let mut pending = String::new();
    while Instant::now() < deadline {
        if let Ok(mut file) = fs::File::open(path) {
            if file.seek(SeekFrom::Start(offset)).is_ok() {
                let mut chunk = String::new();
                if let Ok(n) = file.read_to_string(&mut chunk) {
                    offset += n as u64;
                    pending.push_str(&chunk);
                }
            }
        }
        while let Some(end) = pending.find('\n') {
            let line: String = pending.drain(..=end).collect();
            let line = line.trim_end();
            // lines look like "[unix seconds] message"
            let message = line.split_once("] ").map(|(_, m)| m).unwrap_or(line);
            let lower = message.to_ascii_lowercase();
            // Per-hook failures ("[java] hook failed for ...") are not fatal: Bootstrap keeps the hooks that applied.
            let fatal = message.starts_with("FAILED:") || lower.contains("start failed") || lower.contains("client init failed") || lower.contains("could not hook");
            if fatal {
                fail("loader", message);
                return Outcome::Failed(message.to_string());
            }
            if message.contains("client initialised") {
                ok("loader", message);
                return Outcome::Done;
            }
            step("loader", message);
        }
        std::thread::sleep(Duration::from_millis(200));
    }
    Outcome::TimedOut
}

fn config_path() -> Option<PathBuf> {
    platform::config_dir().map(|dir| dir.join("PrestigeInjector").join("settings.txt"))
}

/// Opens the Discord invite on the very first launch only (no prompt); afterwards only the credit line shows it.
fn open_invite_once() {
    let Some(path) = config_path() else { return };
    if path.exists() {
        return;
    }
    let _ = platform::open_url(INVITE_URL);
    if let Some(dir) = path.parent() {
        let _ = fs::create_dir_all(dir);
    }
    let _ = fs::write(&path, "invite_opened=true\n");
}

/// Interactive list of Minecraft JVM windows, refreshed every 750 ms. Returns the chosen pid.
fn pick_process() -> Option<u32> {
    let mut selected = 0usize;
    let mut processes = platform::java_processes();
    let mut last_refresh = Instant::now();
    loop {
        if selected >= processes.len() {
            selected = processes.len().saturating_sub(1);
        }
        draw(&processes, selected);
        let deadline = Instant::now() + Duration::from_millis(50);
        while Instant::now() < deadline {
            if let Some(key) = platform::poll_key() {
                match key {
                    platform::Key::Up => selected = selected.saturating_sub(1),
                    platform::Key::Down => selected = (selected + 1).min(processes.len().saturating_sub(1)),
                    platform::Key::Enter => {
                        if let Some(p) = processes.get(selected) {
                            return Some(p.pid);
                        }
                    }
                    platform::Key::Escape => return None,
                }
                draw(&processes, selected);
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        if last_refresh.elapsed() >= Duration::from_millis(750) {
            processes = platform::java_processes();
            last_refresh = Instant::now();
        }
    }
}

fn draw(processes: &[platform::JavaProcess], selected: usize) {
    let mut out = String::from("\x1b[2J\x1b[H");
    out.push_str(&banner());
    out.push_str("Minecraft 1.21.11 (Fabric, 64-bit) - Up/Down select, Enter inject, Esc quit\n\n");
    if processes.is_empty() {
        out.push_str("  waiting for a Minecraft JVM (java/javaw)...\n");
    }
    for (i, p) in processes.iter().enumerate() {
        let marker = if i == selected { ">" } else { " " };
        out.push_str(&format!("{marker} [{:>6}] {}  ({})\n", p.pid, p.title, p.exe));
    }
    print!("{out}");
    let _ = io::stdout().flush();
}
