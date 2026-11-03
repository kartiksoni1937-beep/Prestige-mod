//! Minimal raw JNI / JVMTI bindings. Function-table indices were checked against the JDK 21 jni.h / jvmti.h.
use std::ffi::{c_char, c_void, CStr, CString};

pub type JObject = *mut c_void;
pub type JClass = JObject;
pub type JMethodId = *mut c_void;
pub type JValue = u64;
pub type JInt = i32;
pub type JBool = u8;

/// JNIEnv*: pointer to a pointer to the function table.
pub type EnvPtr = *mut *const *const c_void;

pub const JNI_OK: JInt = 0;
pub const JVMTI_VERSION_1_2: JInt = 0x3001_0200;

#[repr(C)]
pub struct NativeMethod {
    pub name: *const c_char,
    pub signature: *const c_char,
    pub fn_ptr: *mut c_void,
}

/// Packs a JNI argument into the 8-byte jvalue slot.
pub fn obj(o: JObject) -> JValue {
    o as usize as u64
}
pub fn boolean(b: bool) -> JValue {
    b as u64
}

pub struct Jni {
    pub env: EnvPtr,
}

macro_rules! f {
    ($self:expr, $idx:expr, $ty:ty) => {
        std::mem::transmute::<*const c_void, $ty>(*(*$self.env).add($idx))
    };
}

impl Jni {
    /// # Safety
    /// `env` must be a valid JNIEnv* of the current thread.
    pub unsafe fn new(env: EnvPtr) -> Self {
        Jni { env }
    }

    pub unsafe fn find_class(&self, name: &str) -> JClass {
        let n = CString::new(name).unwrap();
        f!(self, 6, unsafe extern "system" fn(EnvPtr, *const c_char) -> JClass)(self.env, n.as_ptr())
    }
    pub unsafe fn exception_occurred(&self) -> JObject {
        f!(self, 15, unsafe extern "system" fn(EnvPtr) -> JObject)(self.env)
    }
    pub unsafe fn exception_clear(&self) {
        f!(self, 17, unsafe extern "system" fn(EnvPtr))(self.env)
    }
    pub unsafe fn new_global_ref(&self, o: JObject) -> JObject {
        f!(self, 21, unsafe extern "system" fn(EnvPtr, JObject) -> JObject)(self.env, o)
    }
    pub unsafe fn is_same_object(&self, a: JObject, b: JObject) -> bool {
        f!(self, 24, unsafe extern "system" fn(EnvPtr, JObject, JObject) -> JBool)(self.env, a, b) != 0
    }
    pub unsafe fn get_object_class(&self, o: JObject) -> JClass {
        f!(self, 31, unsafe extern "system" fn(EnvPtr, JObject) -> JClass)(self.env, o)
    }
    pub unsafe fn get_method_id(&self, c: JClass, name: &str, sig: &str) -> JMethodId {
        let (n, s) = (CString::new(name).unwrap(), CString::new(sig).unwrap());
        f!(self, 33, unsafe extern "system" fn(EnvPtr, JClass, *const c_char, *const c_char) -> JMethodId)(self.env, c, n.as_ptr(), s.as_ptr())
    }
    pub unsafe fn new_object(&self, c: JClass, ctor: JMethodId, args: &[JValue]) -> JObject {
        f!(self, 30, unsafe extern "system" fn(EnvPtr, JClass, JMethodId, *const JValue) -> JObject)(self.env, c, ctor, args.as_ptr())
    }
    pub unsafe fn call_object(&self, o: JObject, m: JMethodId, args: &[JValue]) -> JObject {
        f!(self, 36, unsafe extern "system" fn(EnvPtr, JObject, JMethodId, *const JValue) -> JObject)(self.env, o, m, args.as_ptr())
    }
    pub unsafe fn call_void(&self, o: JObject, m: JMethodId, args: &[JValue]) {
        f!(self, 63, unsafe extern "system" fn(EnvPtr, JObject, JMethodId, *const JValue))(self.env, o, m, args.as_ptr())
    }
    pub unsafe fn get_static_method_id(&self, c: JClass, name: &str, sig: &str) -> JMethodId {
        let (n, s) = (CString::new(name).unwrap(), CString::new(sig).unwrap());
        f!(self, 113, unsafe extern "system" fn(EnvPtr, JClass, *const c_char, *const c_char) -> JMethodId)(self.env, c, n.as_ptr(), s.as_ptr())
    }
    pub unsafe fn call_static_object(&self, c: JClass, m: JMethodId, args: &[JValue]) -> JObject {
        f!(self, 116, unsafe extern "system" fn(EnvPtr, JClass, JMethodId, *const JValue) -> JObject)(self.env, c, m, args.as_ptr())
    }
    pub unsafe fn call_static_void(&self, c: JClass, m: JMethodId, args: &[JValue]) {
        f!(self, 143, unsafe extern "system" fn(EnvPtr, JClass, JMethodId, *const JValue))(self.env, c, m, args.as_ptr())
    }
    pub unsafe fn new_string(&self, s: &str) -> JObject {
        let c = CString::new(s).unwrap();
        f!(self, 167, unsafe extern "system" fn(EnvPtr, *const c_char) -> JObject)(self.env, c.as_ptr())
    }
    pub unsafe fn get_string_utf(&self, s: JObject) -> String {
        let chars = f!(self, 169, unsafe extern "system" fn(EnvPtr, JObject, *mut JBool) -> *const c_char)(self.env, s, std::ptr::null_mut());
        if chars.is_null() {
            return String::new();
        }
        let out = CStr::from_ptr(chars).to_string_lossy().into_owned();
        f!(self, 170, unsafe extern "system" fn(EnvPtr, JObject, *const c_char))(self.env, s, chars);
        out
    }
    pub unsafe fn get_array_length(&self, a: JObject) -> JInt {
        f!(self, 171, unsafe extern "system" fn(EnvPtr, JObject) -> JInt)(self.env, a)
    }
    pub unsafe fn get_object_array_element(&self, a: JObject, i: JInt) -> JObject {
        f!(self, 173, unsafe extern "system" fn(EnvPtr, JObject, JInt) -> JObject)(self.env, a, i)
    }
    pub unsafe fn set_object_array_element(&self, a: JObject, i: JInt, v: JObject) {
        f!(self, 174, unsafe extern "system" fn(EnvPtr, JObject, JInt, JObject))(self.env, a, i, v)
    }
    pub unsafe fn ensure_local_capacity(&self, capacity: JInt) -> JInt {
        f!(self, 26, unsafe extern "system" fn(EnvPtr, JInt) -> JInt)(self.env, capacity)
    }
    pub unsafe fn new_object_array(&self, len: JInt, c: JClass, init: JObject) -> JObject {
        f!(self, 172, unsafe extern "system" fn(EnvPtr, JInt, JClass, JObject) -> JObject)(self.env, len, c, init)
    }
    pub unsafe fn new_byte_array(&self, len: JInt) -> JObject {
        f!(self, 176, unsafe extern "system" fn(EnvPtr, JInt) -> JObject)(self.env, len)
    }
    pub unsafe fn get_byte_array_region(&self, a: JObject, start: JInt, len: JInt, buf: *mut u8) {
        f!(self, 200, unsafe extern "system" fn(EnvPtr, JObject, JInt, JInt, *mut u8))(self.env, a, start, len, buf)
    }
    pub unsafe fn set_byte_array_region(&self, a: JObject, start: JInt, len: JInt, buf: *const u8) {
        f!(self, 208, unsafe extern "system" fn(EnvPtr, JObject, JInt, JInt, *const u8))(self.env, a, start, len, buf)
    }
    pub unsafe fn register_natives(&self, c: JClass, methods: &[NativeMethod]) -> JInt {
        f!(self, 215, unsafe extern "system" fn(EnvPtr, JClass, *const NativeMethod, JInt) -> JInt)(self.env, c, methods.as_ptr(), methods.len() as JInt)
    }
    pub unsafe fn unregister_natives(&self, c: JClass) -> JInt {
        f!(self, 216, unsafe extern "system" fn(EnvPtr, JClass) -> JInt)(self.env, c)
    }

    /// Returns "ExceptionClass: message" of the pending exception and clears it.
    pub unsafe fn take_exception(&self) -> Option<String> {
        let ex = self.exception_occurred();
        if ex.is_null() {
            return None;
        }
        self.exception_clear();
        let cls = self.get_object_class(ex);
        let to_string = self.get_method_id(cls, "toString", "()Ljava/lang/String;");
        let text = if to_string.is_null() {
            "<exception>".to_string()
        } else {
            let s = self.call_object(ex, to_string, &[]);
            self.exception_clear();
            if s.is_null() { "<exception>".to_string() } else { self.get_string_utf(s) }
        };
        Some(text)
    }

    /// Converts a pending Java exception into an Err carrying `context`.
    pub unsafe fn check(&self, context: &str) -> Result<(), String> {
        match self.take_exception() {
            Some(e) => Err(format!("{context}: {e}")),
            None => Ok(()),
        }
    }
}

/// JavaVM*: pointer to a pointer to the invoke interface.
pub type VmPtr = *mut *const *const c_void;

pub unsafe fn attach_current_thread(vm: VmPtr) -> Result<EnvPtr, String> {
    let attach = std::mem::transmute::<*const c_void, unsafe extern "system" fn(VmPtr, *mut EnvPtr, *mut c_void) -> JInt>(*(*vm).add(4));
    let mut env: EnvPtr = std::ptr::null_mut();
    let rc = attach(vm, &mut env, std::ptr::null_mut());
    if rc != JNI_OK || env.is_null() {
        return Err(format!("AttachCurrentThread failed ({rc})"));
    }
    Ok(env)
}

/// Detaches the current thread (it must not hold Java frames); a thread attached from native code has to detach before it ends.
pub unsafe fn detach_current_thread(vm: VmPtr) {
    let detach = std::mem::transmute::<*const c_void, unsafe extern "system" fn(VmPtr) -> JInt>(*(*vm).add(5));
    detach(vm);
}

pub unsafe fn get_jvmti(vm: VmPtr) -> Result<JvmtiPtr, String> {
    let get_env = std::mem::transmute::<*const c_void, unsafe extern "system" fn(VmPtr, *mut *mut c_void, JInt) -> JInt>(*(*vm).add(6));
    let mut out: *mut c_void = std::ptr::null_mut();
    let rc = get_env(vm, &mut out, JVMTI_VERSION_1_2);
    if rc != JNI_OK || out.is_null() {
        return Err(format!("GetEnv(JVMTI) failed ({rc})"));
    }
    Ok(out as JvmtiPtr)
}

/// jvmtiEnv*: pointer to a pointer to the JVMTI function table (entry n of the spec is index n-1).
pub type JvmtiPtr = *mut *const *const c_void;

#[repr(C)]
pub struct ClassDefinition {
    pub klass: JClass,
    pub class_byte_count: JInt,
    pub class_bytes: *const u8,
}

#[repr(C)]
#[derive(Default, Clone, Copy)]
pub struct Capabilities {
    pub bits: [u32; 4],
}

impl Capabilities {
    pub fn set(&mut self, bit: u32) {
        self.bits[(bit / 32) as usize] |= 1 << (bit % 32);
    }
}

// capability bit positions, counted in jvmtiCapabilities declaration order
pub const CAN_REDEFINE_CLASSES: u32 = 9;
pub const CAN_REDEFINE_ANY_CLASS: u32 = 21;
pub const CAN_GENERATE_ALL_CLASS_HOOK_EVENTS: u32 = 26;
pub const CAN_RETRANSFORM_CLASSES: u32 = 37;
pub const CAN_RETRANSFORM_ANY_CLASS: u32 = 38;

pub const EVENT_CLASS_FILE_LOAD_HOOK: JInt = 54;

pub type ClassFileLoadHook = unsafe extern "system" fn(
    JvmtiPtr,
    EnvPtr,
    JClass,
    JObject,
    *const c_char,
    JObject,
    JInt,
    *const u8,
    *mut JInt,
    *mut *mut u8,
);

pub unsafe fn add_capabilities(j: JvmtiPtr, caps: &Capabilities) -> JInt {
    let f = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, *const Capabilities) -> JInt>(*(*j).add(141));
    f(j, caps)
}

/// Installs the ClassFileLoadHook callback (field 5 of jvmtiEventCallbacks); the event itself stays off until enabled.
pub unsafe fn install_class_file_load_hook(j: JvmtiPtr, hook: ClassFileLoadHook) -> Result<(), String> {
    let mut callbacks = [0usize; 40];
    callbacks[4] = hook as usize;
    let set_callbacks = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, *const usize, JInt) -> JInt>(*(*j).add(121));
    let rc = set_callbacks(j, callbacks.as_ptr(), std::mem::size_of_val(&callbacks) as JInt);
    if rc != 0 {
        return Err(format!("SetEventCallbacks failed ({rc})"));
    }
    Ok(())
}

/// Turns the ClassFileLoadHook event on or off. It is only on while class bytes are being captured: with the event enabled the
/// JVM hands the bytes of every class it loads to the agent, which slows class loading down for the whole game.
pub unsafe fn set_class_file_load_hook_enabled(j: JvmtiPtr, enabled: bool) -> Result<(), String> {
    let set_mode = std::mem::transmute::<*const c_void, unsafe extern "C" fn(JvmtiPtr, JInt, JInt, JObject, ...) -> JInt>(*(*j).add(1));
    let rc = set_mode(j, enabled as JInt, EVENT_CLASS_FILE_LOAD_HOOK, std::ptr::null_mut());
    if rc != 0 {
        return Err(format!("SetEventNotificationMode({enabled}) failed ({rc})"));
    }
    Ok(())
}

pub unsafe fn retransform_classes(j: JvmtiPtr, classes: &[JClass]) -> JInt {
    let f = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, JInt, *const JClass) -> JInt>(*(*j).add(151));
    f(j, classes.len() as JInt, classes.as_ptr())
}

pub unsafe fn redefine_classes(j: JvmtiPtr, defs: &[ClassDefinition]) -> JInt {
    let f = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, JInt, *const ClassDefinition) -> JInt>(*(*j).add(86));
    f(j, defs.len() as JInt, defs.as_ptr())
}

pub unsafe fn redefine_class(j: JvmtiPtr, def: &ClassDefinition) -> JInt {
    let f = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, JInt, *const ClassDefinition) -> JInt>(*(*j).add(86));
    f(j, 1, def)
}

/// Disables the ClassFileLoadHook event and clears the callback table, so the JVM no longer calls into this DLL.
pub unsafe fn disable_class_file_load_hook(j: JvmtiPtr) -> Result<(), String> {
    let set_mode = std::mem::transmute::<*const c_void, unsafe extern "C" fn(JvmtiPtr, JInt, JInt, JObject, ...) -> JInt>(*(*j).add(1));
    let rc = set_mode(j, 0, EVENT_CLASS_FILE_LOAD_HOOK, std::ptr::null_mut());
    if rc != 0 {
        return Err(format!("SetEventNotificationMode(disable) failed ({rc})"));
    }
    let callbacks = [0usize; 40];
    let set_callbacks = std::mem::transmute::<*const c_void, unsafe extern "system" fn(JvmtiPtr, *const usize, JInt) -> JInt>(*(*j).add(121));
    let rc = set_callbacks(j, callbacks.as_ptr(), std::mem::size_of_val(&callbacks) as JInt);
    if rc != 0 {
        return Err(format!("SetEventCallbacks(clear) failed ({rc})"));
    }
    Ok(())
}
