//! Locates the remapped Prestige payload jar (built by `gradlew remapJar`) so lib.rs can embed it with include_bytes!.
use std::env;
use std::fs;
use std::path::PathBuf;

fn main() {
    println!("cargo:rerun-if-env-changed=PRESTIGE_PAYLOAD");
    let jar = match env::var("PRESTIGE_PAYLOAD") {
        Ok(p) => PathBuf::from(p),
        Err(_) => newest_remapped_jar(),
    };
    if !jar.is_file() {
        panic!("payload jar not found: {} (run `gradlew remapJar` or set PRESTIGE_PAYLOAD)", jar.display());
    }
    println!("cargo:rerun-if-changed={}", jar.display());
    println!("cargo:rustc-env=PRESTIGE_PAYLOAD_JAR={}", jar.display());
}

fn newest_remapped_jar() -> PathBuf {
    let libs = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap()).join("../../../build/libs");
    let mut best: Option<(std::time::SystemTime, PathBuf)> = None;
    if let Ok(entries) = fs::read_dir(&libs) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if !name.starts_with("prestige-") || !name.ends_with(".jar") || name.ends_with("-dev.jar") || name.ends_with("-sources.jar") {
                continue;
            }
            let time = entry.metadata().and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH);
            if best.as_ref().map_or(true, |(t, _)| time > *t) {
                best = Some((time, entry.path()));
            }
        }
    }
    best.map(|(_, p)| p).unwrap_or_else(|| libs.join("prestige-0.1.0.jar"))
}
