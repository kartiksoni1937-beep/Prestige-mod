//! Lists running Minecraft (Fabric) JVMs on Linux and macOS. std only.
use super::JavaProcess;

/// Last path component, accepting both separators.
fn file_name(path: &str) -> &str {
    path.trim_end_matches(['/', '\\']).rsplit(['/', '\\']).next().unwrap_or(path)
}

/// True when `argv0` names a java launcher and the arguments look like a Minecraft/Fabric JVM.
pub(crate) fn is_minecraft_jvm(args: &[String]) -> bool {
    let Some(argv0) = args.first() else { return false };
    if !file_name(argv0).to_ascii_lowercase().contains("java") {
        return false;
    }
    args.iter().any(|a| {
        let a = a.to_ascii_lowercase();
        a.contains("net.fabricmc.loader") || a.contains("knotclient") || a.contains("minecraft")
    })
}

/// Friendly instance label: value of --version, else the last component of --gameDir, else "Minecraft".
pub(crate) fn instance_label(args: &[String]) -> String {
    fn value<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
        let prefix = format!("{flag}=");
        let mut it = args.iter();
        while let Some(a) = it.next() {
            if a == flag {
                return it.next().map(String::as_str).filter(|v| !v.is_empty());
            }
            if let Some(v) = a.strip_prefix(&prefix) {
                if !v.is_empty() {
                    return Some(v);
                }
            }
        }
        None
    }
    if let Some(v) = value(args, "--version") {
        return v.to_string();
    }
    if let Some(dir) = value(args, "--gameDir") {
        let name = file_name(dir);
        if !name.is_empty() {
            return name.to_string();
        }
    }
    "Minecraft".to_string()
}

fn build(pid: u32, args: &[String]) -> Option<JavaProcess> {
    if !is_minecraft_jvm(args) {
        return None;
    }
    Some(JavaProcess { pid, title: instance_label(args), exe: file_name(&args[0]).to_string() })
}

/// Parses one `ps -axo pid=,uid=,command=` line into (pid, uid, argv).
/// ps joins argv with spaces, so argv[0] is taken as everything before the first " -" option.
#[cfg_attr(target_os = "linux", allow(dead_code))]
pub(crate) fn parse_ps_line(line: &str) -> Option<(u32, u32, Vec<String>)> {
    let line = line.trim_start();
    let (pid, rest) = line.split_once(char::is_whitespace)?;
    let rest = rest.trim_start();
    let (uid, cmd) = rest.split_once(char::is_whitespace)?;
    let cmd = cmd.trim();
    let (argv0, tail) = match cmd.find(" -") {
        Some(i) => (&cmd[..i], &cmd[i..]),
        None => (cmd, ""),
    };
    let mut args = vec![argv0.to_string()];
    args.extend(tail.split_whitespace().map(str::to_string));
    Some((pid.parse().ok()?, uid.parse().ok()?, args))
}

#[cfg(target_os = "linux")]
pub fn java_processes() -> Vec<JavaProcess> {
    use std::os::unix::fs::MetadataExt;
    let own_pid = std::process::id();
    let own_uid = std::fs::metadata("/proc/self").map(|m| m.uid()).ok();
    let mut out = Vec::new();
    let Ok(dir) = std::fs::read_dir("/proc") else { return out };
    for entry in dir.flatten() {
        let Some(pid) = entry.file_name().to_str().and_then(|n| n.parse::<u32>().ok()) else { continue };
        if pid == own_pid {
            continue;
        }
        if let (Some(me), Ok(meta)) = (own_uid, entry.metadata()) {
            if meta.uid() != me {
                continue;
            }
        }
        let Ok(raw) = std::fs::read(entry.path().join("cmdline")) else { continue };
        let args: Vec<String> = raw
            .split(|&b| b == 0)
            .filter(|s| !s.is_empty())
            .map(|s| String::from_utf8_lossy(s).into_owned())
            .collect();
        if let Some(p) = build(pid, &args) {
            out.push(p);
        }
    }
    out.sort_by_key(|p| p.pid);
    out
}

#[cfg(not(target_os = "linux"))]
pub fn java_processes() -> Vec<JavaProcess> {
    let own_pid = std::process::id();
    let Ok(output) = std::process::Command::new("ps").args(["-axo", "pid=,uid=,command="]).output() else {
        return Vec::new();
    };
    let text = String::from_utf8_lossy(&output.stdout);
    let rows: Vec<_> = text.lines().filter_map(parse_ps_line).collect();
    let own_uid = rows.iter().find(|r| r.0 == own_pid).map(|r| r.1);
    let mut out: Vec<JavaProcess> = rows
        .iter()
        .filter(|(pid, uid, _)| *pid != own_pid && own_uid.map_or(true, |u| u == *uid))
        .filter_map(|(pid, _, args)| build(*pid, args))
        .collect();
    out.sort_by_key(|p| p.pid);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(s: &[&str]) -> Vec<String> {
        s.iter().map(|x| x.to_string()).collect()
    }

    #[test]
    fn filters_minecraft_jvms() {
        assert!(is_minecraft_jvm(&v(&["/usr/lib/jvm/java-21/bin/java", "-cp", "x", "net.fabricmc.loader.impl.launch.knot.KnotClient"])));
        assert!(is_minecraft_jvm(&v(&["java", "-Dminecraft.launcher.brand=x", "Main"])));
        assert!(!is_minecraft_jvm(&v(&["/usr/bin/java", "-jar", "gradle.jar"])));
        assert!(!is_minecraft_jvm(&v(&["/usr/bin/bash", "-c", "minecraft"])));
        assert!(!is_minecraft_jvm(&[]));
    }

    #[test]
    fn labels() {
        assert_eq!(instance_label(&v(&["java", "--version", "fabric-1.21.11"])), "fabric-1.21.11");
        assert_eq!(instance_label(&v(&["java", "--version=1.21"])), "1.21");
        assert_eq!(instance_label(&v(&["java", "--gameDir", "/home/u/.minecraft/instances/Pack/"])), "Pack");
        assert_eq!(instance_label(&v(&["java", "KnotClient"])), "Minecraft");
    }

    #[test]
    fn ps_lines() {
        let (pid, uid, args) = parse_ps_line("  123   501 /Users/a/Library/Application Support/x/bin/java -Xmx2G --version 1.21 net.fabricmc.loader").unwrap();
        assert_eq!((pid, uid), (123, 501));
        assert_eq!(args[0], "/Users/a/Library/Application Support/x/bin/java");
        assert!(is_minecraft_jvm(&args));
        assert_eq!(instance_label(&args), "1.21");
        assert!(parse_ps_line("garbage").is_none());
    }
}
