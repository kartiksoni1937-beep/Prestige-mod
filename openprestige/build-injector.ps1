# Builds the Prestige payload jar (remapped for the production Fabric runtime), then the Rust injector and loader DLL,
# and collects PrestigeInjector.exe + prestige_native.dll in dist\.
# native tools write progress/notes to stderr; failures are detected through $LASTEXITCODE instead
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
if (Test-Path 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot') { $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot' }
& .\gradlew.bat remapJar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'gradle remapJar failed' }
cargo build --release --manifest-path injector\Cargo.toml
if ($LASTEXITCODE -ne 0) { throw 'cargo build failed' }
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item injector\target\release\PrestigeInjector.exe, injector\target\release\prestige_native.dll dist\ -Force
Get-ChildItem build\libs\prestige-*.jar | Where-Object { $_.Name -notmatch '-(dev|sources)\.jar$' } | Copy-Item -Destination dist\ -Force
Write-Host 'Done: dist\PrestigeInjector.exe + dist\prestige_native.dll + dist\prestige-*.jar (Fabric mod)'
