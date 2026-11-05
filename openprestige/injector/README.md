# Prestige injector (Rust)

`PrestigeInjector` loads the native loader library (`prestige_native.dll` / `libprestige_native.so` / `libprestige_native.dylib`)
into a running Minecraft **1.21.11 Fabric** JVM (64-bit, Fabric Loader 0.19.x, Fabric API installed). It uses the same overall
method as the openvape bundle, written from scratch in Rust with no external crates.

## Supported platforms

| OS      | Architectures      | Injector binary       | Loader library              |
|---------|--------------------|-----------------------|-----------------------------|
| Windows | x86_64             | `PrestigeInjector.exe`| `prestige_native.dll`       |
| Linux   | x86_64             | `PrestigeInjector`    | `libprestige_native.so`     |
| macOS   | x86_64, arm64      | `PrestigeInjector`    | `libprestige_native.dylib`  |

The injector and library must be built for the same architecture as the target JVM.

## How it works

1. **Injector** lists the running Fabric client JVMs, then loads the library into the chosen process.
   - **Windows:** lists visible `java.exe`/`javaw.exe` windows (refreshed every 750 ms), then runs `LoadLibraryW` in the chosen
     process through `CreateRemoteThread` and confirms the module is mapped.
   - **Linux / macOS:** uses the HotSpot attach protocol (the same mechanism as `jcmd <pid> JVMTI.agent_load`). The injector
     creates `.attach_pid<pid>` and sends `SIGQUIT` to the JVM, connects to its attach socket (`/tmp/.java_pid<pid>`, under
     `$TMPDIR` on macOS) and asks it to load the library. The JVM `dlopen`s `libprestige_native` and calls its
     `Agent_OnAttach` entry point. No ptrace, no root, no per-architecture shellcode.
     Processes are found via `/proc` (Linux) or `ps` (macOS); there are no window titles, so the picker shows the pid and
     instance name.
2. **The native library** only spawns a worker thread from its entry point (`DllMain` on Windows, `Agent_OnAttach` on
   unix, which also hands over the `JavaVM*`). The worker:
   - waits for the JVM (Windows: `jvm.dll`, `JNI_GetCreatedJavaVMs`), attaches, acquires the JVMTI redefine/retransform capabilities;
   - writes the embedded payload jar (the remapped `prestige-*.jar`) to the temp directory under `PrestigeLoader`;
   - `FabricLauncherBase.getLauncher().addToClassPath(jar)` so payload and game classes share the Knot class loader;
   - registers `NativeBridge.classBytes` / `NativeBridge.redefine` and calls `Bootstrap.start()`.
3. **`Bootstrap`** (Java) waits for the client window, then for every ClassTransform hook target gets the current class bytes
   (JVMTI retransform capture), runs the hooks through `InPlaceTransformer` and redefines the class. The bundled ClassTransform
   inlines hook handlers into the target methods, so normally nothing is added to the class and a plain redefine works. If a
   transformer ever does add methods (a running JVM cannot), they are moved into generated `dev.zprestige.prestige.hooks.H*`
   helper classes, call sites become `invokestatic`, and private member access goes through `Access` MethodHandles.
   Finally the Prestige and ClickGUI entrypoints run on the client thread.

## Build

The payload jar is embedded with `include_bytes!`, so each target needs its own build. Both scripts below build for the host OS only and run
`gradlew remapJar`, `cargo build --release`, and copy the results to `dist/`.

```powershell
.\build-injector.ps1      # Windows: dist\PrestigeInjector.exe + dist\prestige_native.dll
```

```sh
./build-injector.sh       # Linux/macOS: dist/PrestigeInjector + dist/libprestige_native.{so,dylib}
```

To build release zips for every platform from Windows, run `build-release.bat`. It cross-compiles Linux (x86_64,
glibc 2.17+, so one build covers every mainstream distro) and macOS (universal x86_64 + arm64) with `cargo-zigbuild`, installing
zig and cargo-zigbuild on first run, and writes `dist\release\prestige-<ver>-{windows-x86_64,linux-x86_64,macos-universal}.zip`,
the bare mod jar and `SHA256SUMS.txt`.

Set `JAVA_HOME` beforehand to choose the JDK for Gradle (`build-injector.sh` honours it). The GitHub Actions workflow
`.github/workflows/injector.yml` builds and tests all four targets (Windows x86_64, Linux x86_64, macOS x86_64, macOS arm64)
and uploads them as artifacts.

## Use

Start a Fabric 1.21.11 instance, then run:

```sh
PrestigeInjector                      # interactive: pick the game from the list
PrestigeInjector <pid> [lib path]     # inject into a specific pid, optionally with an explicit library path
```

On Windows this is `PrestigeInjector.exe`. By default the library is looked up beside the injector.

## Install as a mod instead

The payload jar is also a normal Fabric mod. Put `prestige-*.jar` (from `dist/` or the CI `payload-jar` artifact) in the
`mods/` folder of a Fabric Loader 0.19+ / Fabric API / Minecraft 1.21.11 instance; no injector is needed, on any OS.
Do not use both: if the mod is already loaded, the injector's bootstrap logs "already loaded as a Fabric mod" and skips.

## Requirements (Linux / macOS)

- Run the injector as the **same user** as the game.
- The attach mechanism must be enabled: the game must not be started with `-XX:+DisableAttachMechanism`.
- The JVM architecture must match the injector build. On Apple Silicon use an arm64 JVM, or use the x86_64 build for JVMs
  running under Rosetta.
- Sandboxed launchers (Flatpak, Snap) hide the attach socket and are not supported.

## Known limits

- On Linux/macOS the library stays loaded after self-destruct: hooks are removed and natives unregistered, but the library is
  not `dlclose`d (unmapping code the thread is still returning into would crash the JVM). On Windows the library is freed.
- Linux and macOS support is compile-checked in CI; test on a real host before relying on it.

## Log

`prestige-native.log` is written beside the loader library, plus `[Prestige-Inject]` lines in the game log.
