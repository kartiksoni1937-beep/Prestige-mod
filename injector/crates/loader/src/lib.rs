//! prestige_native.dll: loaded into a running Minecraft 1.21.11 (Fabric) JVM by PrestigeInjector.exe.
//!
//! The DLL worker waits for the JVM, appends the embedded Prestige payload jar to Fabric's Knot class loader, registers the
//! JVMTI-backed natives of `dev.zprestige.prestige.inject.NativeBridge` and starts `Bootstrap`, which transforms the already
//! loaded game classes and initialises the client.
mod bridge;
mod jni;
mod log;
mod worker;

use std::ffi::c_void;

#[cfg(windows)]
const DLL_PROCESS_ATTACH: u32 = 1;

#[cfg(windows)]
extern "system" {
    fn DisableThreadLibraryCalls(module: *mut c_void) -> i32;
}

#[cfg(windows)]
pub(crate) static mut MODULE: *mut c_void = std::ptr::null_mut();

#[cfg(windows)]
#[no_mangle]
pub extern "system" fn DllMain(module: *mut c_void, reason: u32, _reserved: *mut c_void) -> i32 {
    if reason == DLL_PROCESS_ATTACH {
        unsafe {
            MODULE = module;
            DisableThreadLibraryCalls(module);
        }
        // Nothing but thread creation may happen under the loader lock.
        let _ = std::thread::Builder::new().name("prestige-loader".into()).spawn(worker::run);
    }
    1
}

/// The JavaVM handed to Agent_OnAttach / Agent_OnLoad by the JVM (unix); read by the worker.
#[cfg(unix)]
pub(crate) static VM: std::sync::atomic::AtomicPtr<c_void> = std::sync::atomic::AtomicPtr::new(std::ptr::null_mut());

#[cfg(unix)]
fn start_worker(vm: *mut c_void) -> i32 {
    VM.store(vm, std::sync::atomic::Ordering::SeqCst);
    match std::thread::Builder::new().name("prestige-loader".into()).spawn(worker::run) {
        Ok(_) => 0,
        Err(_) => -1,
    }
}

/// Called by HotSpot when the library is loaded through the attach protocol (JVMTI.agent_load).
#[cfg(unix)]
#[no_mangle]
pub extern "C" fn Agent_OnAttach(vm: *mut c_void, _options: *mut std::ffi::c_char, _reserved: *mut c_void) -> i32 {
    start_worker(vm)
}

/// Called by the JVM when the library is given as -agentpath at start-up.
#[cfg(unix)]
#[no_mangle]
pub extern "C" fn Agent_OnLoad(vm: *mut c_void, _options: *mut std::ffi::c_char, _reserved: *mut c_void) -> i32 {
    start_worker(vm)
}
