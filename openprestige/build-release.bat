@echo off
rem Builds release zips for every platform into dist\release\ (run on Windows):
rem   prestige-<ver>-windows-x86_64.zip   PrestigeInjector.exe + prestige_native.dll + mod jar
rem   prestige-<ver>-linux-x86_64.zip     PrestigeInjector + libprestige_native.so + mod jar (glibc 2.17+, any distro)
rem   prestige-<ver>-macos-universal.zip  PrestigeInjector + libprestige_native.dylib + mod jar (Intel + Apple Silicon)
rem   prestige-<ver>.jar                  the Fabric mod on its own
rem   SHA256SUMS.txt
rem Linux and macOS are cross-compiled with cargo-zigbuild + zig; both are installed on first run if missing.
rem Requires: Rust (rustup), Python 3, JDK 21.
setlocal EnableExtensions
cd /d "%~dp0"
set "SELF=%~f0"

set "OUT=%CD%\dist\release"
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"mod_version=" gradle.properties') do set "VERSION=%%b"
if not defined VERSION set "VERSION=dev"
echo === Prestige %VERSION% release build

rem ---- tools -------------------------------------------------------------
where cargo >nul 2>&1 || (echo cargo not found: install Rust from https://rustup.rs & goto :fail)
where python >nul 2>&1 || (echo python not found: install Python 3 from https://python.org & goto :fail)
python -m ziglang version >nul 2>&1 || (
    echo === Installing zig ^(pip ziglang^)
    python -m pip install --user ziglang || goto :fail
)
where cargo-zigbuild >nul 2>&1 || (
    echo === Installing cargo-zigbuild
    cargo install --locked cargo-zigbuild || goto :fail
)
rustup target add x86_64-pc-windows-msvc x86_64-unknown-linux-gnu x86_64-apple-darwin aarch64-apple-darwin || goto :fail

rem zigbuild's linker wrappers break on paths with spaces, so keep its cache on the 8.3 short path
if not exist "%LOCALAPPDATA%\cargo-zigbuild" mkdir "%LOCALAPPDATA%\cargo-zigbuild"
for %%I in ("%LOCALAPPDATA%\cargo-zigbuild") do set "CARGO_ZIGBUILD_CACHE_DIR=%%~sI"

rem ---- payload jar -------------------------------------------------------
echo === Building payload jar
call "%CD%\gradlew.bat" remapJar --console=plain || goto :fail
set "JAR="
for /f "delims=" %%j in ('dir /b /o-d "build\libs\prestige-*.jar" ^| findstr /v /i /c:"-dev.jar" /c:"-sources.jar"') do if not defined JAR set "JAR=%CD%\build\libs\%%j"
if not defined JAR (echo no remapped jar in build\libs & goto :fail)
rem every target embeds this exact jar
set "PRESTIGE_PAYLOAD=%JAR%"

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%" || goto :fail

rem ---- native builds -----------------------------------------------------
echo === Windows x86_64
cargo build --release --manifest-path injector\Cargo.toml --target x86_64-pc-windows-msvc || goto :fail
call :package windows-x86_64 x86_64-pc-windows-msvc PrestigeInjector.exe prestige_native.dll || goto :fail

echo === Linux x86_64 (glibc 2.17+)
cargo zigbuild --release --manifest-path injector\Cargo.toml --target x86_64-unknown-linux-gnu.2.17 || goto :fail
call :package linux-x86_64 x86_64-unknown-linux-gnu PrestigeInjector libprestige_native.so || goto :fail

echo === macOS universal (x86_64 + arm64)
cargo zigbuild --release --manifest-path injector\Cargo.toml --target universal2-apple-darwin || goto :fail
call :package macos-universal universal2-apple-darwin PrestigeInjector libprestige_native.dylib || goto :fail

copy /y "%JAR%" "%OUT%\" >nul || goto :fail
python scripts\package_release.py checksums "%OUT%" || goto :fail

echo.
echo === Done: %OUT%
dir /b "%OUT%"
call :maybe_pause
exit /b 0

rem %1 platform name, %2 cargo target dir, %3 injector file, %4 library file
:package
python scripts\package_release.py zip "%OUT%" "%VERSION%" %1 "injector\target\%~2\release\%~3" "injector\target\%~2\release\%~4" "%JAR%"
exit /b %ERRORLEVEL%

:fail
echo.
echo *** Release build FAILED
call :maybe_pause
exit /b 1

rem keep the window open when started by double-click
:maybe_pause
echo %cmdcmdline% | find /i "%SELF%" >nul && pause
exit /b 0
