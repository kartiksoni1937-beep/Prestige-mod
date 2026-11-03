//! The loader worker: JVM discovery, payload installation into Fabric's Knot class loader, native registration, bootstrap.
use crate::bridge::{init_jvmti, native_class_bytes, native_class_bytes_batch, native_log, native_redefine, native_redefine_batch, native_release};
use crate::jni::*;
use crate::log;
#[cfg(windows)]
use std::ffi::c_char;
use std::ffi::c_void;
use std::path::PathBuf;
#[cfg(windows)]
use std::time::Duration;

#[cfg(windows)]
extern "system" {
    fn GetModuleHandleA(name: *const c_char) -> *mut c_void;
    fn GetProcAddress(module: *mut c_void, name: *const c_char) -> *mut c_void;
}

static PAYLOAD: &[u8] = include_bytes!(env!("PRESTIGE_PAYLOAD_JAR"));

const NATIVE_BRIDGE: &str = "dev.zprestige.prestige.inject.NativeBridge";
const BOOTSTRAP: &str = "dev.zprestige.prestige.inject.Bootstrap";

pub fn run() {
    log!("worker started, pid {}", std::process::id());
    match unsafe { install() } {
        Ok(()) => log!("bootstrap returned"),
        Err(e) => log!("FAILED: {e}"),
    }
}

unsafe fn install() -> Result<(), String> {
    let vm = find_vm()?;
    let env = attach_current_thread(vm)?;
    struct Detach(VmPtr);
    impl Drop for Detach {
        fn drop(&mut self) {
            unsafe { detach_current_thread(self.0) }
        }
    }
    let _detach = Detach(vm);
    let jni = Jni::new(env);
    log!("attached to the JVM");

    init_jvmti(vm)?;
    log!("JVMTI ready");

    let jar = write_payload()?;
    log!("payload written to {}", jar.display());

    let loader = add_to_knot(&jni, &jar)?;
    log!("payload appended to the Fabric Knot class loader");

    register_natives(&jni, loader)?;
    log!("natives registered");

    let bootstrap = class_for_name(&jni, BOOTSTRAP, loader, true)?;
    let start = jni.get_static_method_id(bootstrap, "start", "()V");
    jni.check("resolve Bootstrap.start")?;
    // Bootstrap.start waits for the client, installs the hooks and schedules the client initialisation.
    jni.call_static_void(bootstrap, start, &[]);
    jni.check("Bootstrap.start")?;
    Ok(())
}

/// The JavaVM passed to Agent_OnAttach / Agent_OnLoad.
#[cfg(unix)]
unsafe fn find_vm() -> Result<VmPtr, String> {
    let vm = crate::VM.load(std::sync::atomic::Ordering::SeqCst) as VmPtr;
    if vm.is_null() {
        return Err("no JavaVM was passed to the agent".into());
    }
    Ok(vm)
}

/// Waits for jvm.dll and a created JavaVM (the injector may run before Minecraft has started its JVM).
#[cfg(windows)]
unsafe fn find_vm() -> Result<VmPtr, String> {
    let mut module = std::ptr::null_mut();
    for _ in 0..600 {
        module = GetModuleHandleA(c"jvm.dll".as_ptr());
        if !module.is_null() {
            break;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    if module.is_null() {
        return Err("jvm.dll is not loaded in this process".into());
    }
    let get_vms = GetProcAddress(module, c"JNI_GetCreatedJavaVMs".as_ptr());
    if get_vms.is_null() {
        return Err("JNI_GetCreatedJavaVMs export not found".into());
    }
    let get_vms: unsafe extern "system" fn(*mut VmPtr, JInt, *mut JInt) -> JInt = std::mem::transmute(get_vms);
    for _ in 0..600 {
        let mut vm: VmPtr = std::ptr::null_mut();
        let mut count: JInt = 0;
        if get_vms(&mut vm, 1, &mut count) == JNI_OK && count > 0 && !vm.is_null() {
            return Ok(vm);
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    Err("no JavaVM was created".into())
}

fn write_payload() -> Result<PathBuf, String> {
    let dir = std::env::temp_dir().join("PrestigeLoader");
    std::fs::create_dir_all(&dir).map_err(|e| format!("create {}: {e}", dir.display()))?;
    let path = dir.join(format!("prestige-payload-{}.jar", std::process::id()));
    std::fs::write(&path, PAYLOAD).map_err(|e| format!("write {}: {e}", path.display()))?;
    Ok(path)
}

/// FabricLauncherBase.getLauncher().addToClassPath(path) so the payload and the game classes share the Knot class loader.
/// Returns a global reference to the Knot target class loader.
unsafe fn add_to_knot(jni: &Jni, jar: &std::path::Path) -> Result<JObject, String> {
    jni.ensure_local_capacity(4096);
    let (base, fabric_loader) = locate_fabric(jni)?;
    let get_launcher = jni.get_static_method_id(base, "getLauncher", "()Lnet/fabricmc/loader/impl/launch/FabricLauncher;");
    jni.check("FabricLauncherBase.getLauncher")?;
    let launcher = jni.call_static_object(base, get_launcher, &[]);
    jni.check("getLauncher()")?;
    if launcher.is_null() {
        return Err("Fabric launcher is not available yet".into());
    }

    let launcher_class = load_fabric_class(jni, "net.fabricmc.loader.impl.launch.FabricLauncher", fabric_loader)?;
    let add = jni.get_method_id(launcher_class, "addToClassPath", "(Ljava/nio/file/Path;[Ljava/lang/String;)V");
    jni.check("FabricLauncher.addToClassPath")?;
    let target = jni.get_method_id(launcher_class, "getTargetClassLoader", "()Ljava/lang/ClassLoader;");
    jni.check("FabricLauncher.getTargetClassLoader")?;

    // Path path = new java.io.File(jar).toPath()
    let file_class = jni.find_class("java/io/File");
    let file_ctor = jni.get_method_id(file_class, "<init>", "(Ljava/lang/String;)V");
    let to_path = jni.get_method_id(file_class, "toPath", "()Ljava/nio/file/Path;");
    jni.check("java.io.File")?;
    let jar_string = jni.new_string(&jar.to_string_lossy());
    let file = jni.new_object(file_class, file_ctor, &[obj(jar_string)]);
    let path = jni.call_object(file, to_path, &[]);
    jni.check("File.toPath")?;

    let string_class = jni.find_class("java/lang/String");
    let prefixes = jni.new_object_array(0, string_class, std::ptr::null_mut());
    jni.call_void(launcher, add, &[obj(path), obj(prefixes)]);
    jni.check("addToClassPath")?;

    let loader = jni.call_object(launcher, target, &[]);
    jni.check("getTargetClassLoader()")?;
    if loader.is_null() {
        return Err("Knot target class loader is null".into());
    }
    Ok(jni.new_global_ref(loader))
}

const FABRIC_BASE: &str = "net.fabricmc.loader.impl.launch.FabricLauncherBase";

/// Finds the class loader that can see fabric-loader. The system class loader is tried first (official launcher, most
/// others); launchers that run the game from their own URLClassLoader (Prism/MultiMC, many modpack launchers) keep
/// fabric-loader out of it, so every thread's context class loader is probed too. Returns the FabricLauncherBase class
/// and the loader it was found through (null = FindClass / system loader).
unsafe fn locate_fabric(jni: &Jni) -> Result<(JClass, JObject), String> {
    let base = jni.find_class(&FABRIC_BASE.replace('.', "/"));
    if jni.take_exception().is_none() && !base.is_null() {
        return Ok((base, std::ptr::null_mut()));
    }
    log!("FabricLauncherBase is not visible to the system class loader, probing thread context class loaders");
    let loaders = thread_loaders(jni)?;
    for &loader in &loaders {
        if let Ok(class) = class_for_name(jni, FABRIC_BASE, loader, false) {
            log!("fabric-loader found through {}", describe(jni, loader));
            return Ok((class, loader));
        }
    }
    let seen: Vec<String> = loaders.iter().map(|&l| describe(jni, l)).collect();
    Err(format!(
        "net.fabricmc.loader is not visible to any class loader ({} probed: {}); is this a Fabric instance?",
        seen.len(),
        seen.join(" | ")
    ))
}

/// Distinct context class loaders of all live threads (the game threads inherit the launcher's loader).
unsafe fn thread_loaders(jni: &Jni) -> Result<Vec<JObject>, String> {
    let thread_class = jni.find_class("java/lang/Thread");
    let get_all = jni.get_static_method_id(thread_class, "getAllStackTraces", "()Ljava/util/Map;");
    let get_context = jni.get_method_id(thread_class, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    let map_class = jni.find_class("java/util/Map");
    let key_set = jni.get_method_id(map_class, "keySet", "()Ljava/util/Set;");
    let set_class = jni.find_class("java/util/Set");
    let to_array = jni.get_method_id(set_class, "toArray", "()[Ljava/lang/Object;");
    jni.check("Thread/Map/Set lookup")?;
    let map = jni.call_static_object(thread_class, get_all, &[]);
    jni.check("Thread.getAllStackTraces")?;
    let set = jni.call_object(jni_non_null(map)?, key_set, &[]);
    let threads = jni.call_object(jni_non_null(set)?, to_array, &[]);
    jni.check("thread list")?;
    let mut loaders: Vec<JObject> = Vec::new();
    for i in 0..jni.get_array_length(jni_non_null(threads)?) {
        let thread = jni.get_object_array_element(threads, i);
        if thread.is_null() {
            continue;
        }
        let loader = jni.call_object(thread, get_context, &[]);
        if jni.take_exception().is_some() || loader.is_null() {
            continue;
        }
        if !loaders.iter().any(|&known| jni.is_same_object(known, loader)) {
            loaders.push(loader);
        }
    }
    Ok(loaders)
}

unsafe fn jni_non_null(o: JObject) -> Result<JObject, String> {
    if o.is_null() { Err("unexpected null from the JVM".into()) } else { Ok(o) }
}

/// Object.toString() of a class loader for the log.
unsafe fn describe(jni: &Jni, o: JObject) -> String {
    let class = jni.get_object_class(o);
    let to_string = jni.get_method_id(class, "toString", "()Ljava/lang/String;");
    if to_string.is_null() {
        jni.exception_clear();
        return "<loader>".into();
    }
    let s = jni.call_object(o, to_string, &[]);
    if jni.take_exception().is_some() || s.is_null() { "<loader>".into() } else { jni.get_string_utf(s) }
}

/// Loads a fabric-loader class through the loader found by `locate_fabric` (null = system class loader).
unsafe fn load_fabric_class(jni: &Jni, name: &str, loader: JObject) -> Result<JClass, String> {
    if loader.is_null() {
        let class = jni.find_class(&name.replace('.', "/"));
        jni.check(name)?;
        return Ok(class);
    }
    class_for_name(jni, name, loader, true)
}

/// Class.forName(name, initialize, loader)
unsafe fn class_for_name(jni: &Jni, name: &str, loader: JObject, initialize: bool) -> Result<JClass, String> {
    let class_class = jni.find_class("java/lang/Class");
    let for_name = jni.get_static_method_id(class_class, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
    jni.check("Class.forName")?;
    let name_string = jni.new_string(name);
    let class = jni.call_static_object(class_class, for_name, &[obj(name_string), boolean(initialize), obj(loader)]);
    jni.check(&format!("load {name}"))?;
    if class.is_null() {
        return Err(format!("{name} not found"));
    }
    Ok(class)
}

unsafe fn register_natives(jni: &Jni, loader: JObject) -> Result<(), String> {
    let class = class_for_name(jni, NATIVE_BRIDGE, loader, true)?;
    let methods = [
        NativeMethod {
            name: c"classBytes".as_ptr(),
            signature: c"(Ljava/lang/Class;)[B".as_ptr(),
            fn_ptr: native_class_bytes as *const () as usize as *mut c_void,
        },
        NativeMethod {
            name: c"redefine".as_ptr(),
            signature: c"(Ljava/lang/Class;[B)I".as_ptr(),
            fn_ptr: native_redefine as *const () as usize as *mut c_void,
        },
        NativeMethod {
            name: c"classBytesBatch".as_ptr(),
            signature: c"([Ljava/lang/Class;)[[B".as_ptr(),
            fn_ptr: native_class_bytes_batch as *const () as usize as *mut c_void,
        },
        NativeMethod {
            name: c"redefineBatch".as_ptr(),
            signature: c"([Ljava/lang/Class;[[B)I".as_ptr(),
            fn_ptr: native_redefine_batch as *const () as usize as *mut c_void,
        },
        NativeMethod {
            name: c"release".as_ptr(),
            signature: c"()V".as_ptr(),
            fn_ptr: native_release as *const () as usize as *mut c_void,
        },
        NativeMethod {
            name: c"log".as_ptr(),
            signature: c"(Ljava/lang/String;)V".as_ptr(),
            fn_ptr: native_log as *const () as usize as *mut c_void,
        },
    ];
    let rc = jni.register_natives(class, &methods);
    if rc != JNI_OK {
        let _ = jni.take_exception();
        return Err(format!("RegisterNatives failed ({rc})"));
    }
    Ok(())
}
