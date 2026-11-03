"""Packs one platform's release zip, or writes SHA256SUMS.txt for dist/release. Called by release.bat.

  package_release.py zip <out_dir> <version> <platform> <injector> <native_lib> <payload_jar>
  package_release.py checksums <out_dir>

Zips are written with unix permissions so PrestigeInjector stays executable after unzip on Linux/macOS.
"""
import hashlib
import os
import sys
import time
import zipfile

README = {
    "windows": """Prestige {version} - Windows (x86_64)

Contents
  PrestigeInjector.exe   injector
  prestige_native.dll    loader library (keep it next to the injector)
  {jar}   the same client as a Fabric mod (optional, see below)

Inject
  1. Start Minecraft 1.21.11 with Fabric Loader 0.19+ and Fabric API.
  2. Run PrestigeInjector.exe and pick the game from the list.

If SmartScreen blocks the exe: More info -> Run anyway.
""",
    "linux": """Prestige {version} - Linux (x86_64)

Works on any glibc distro from 2012 on (glibc 2.17+): Ubuntu, Debian, Fedora, Arch, Mint, openSUSE, ...
Not for musl distros (Alpine) or ARM machines.

Contents
  PrestigeInjector         injector
  libprestige_native.so    loader library (keep it next to the injector)
  {jar}   the same client as a Fabric mod (optional, see below)

Inject
  1. Start Minecraft 1.21.11 with Fabric Loader 0.19+ and Fabric API.
  2. In a terminal in this folder:  ./PrestigeInjector
     Run it as the same user as the game. The game must not use -XX:+DisableAttachMechanism.
""",
    "macos": """Prestige {version} - macOS (universal: Intel + Apple Silicon)

Contents
  PrestigeInjector           injector
  libprestige_native.dylib   loader library (keep it next to the injector)
  {jar}   the same client as a Fabric mod (optional, see below)

Inject
  1. Start Minecraft 1.21.11 with Fabric Loader 0.19+ and Fabric API.
  2. In Terminal, in this folder, clear the download quarantine once:
       xattr -dr com.apple.quarantine .
  3. Run:  ./PrestigeInjector
     Run it as the same user as the game. The game must not use -XX:+DisableAttachMechanism.
""",
}

MOD_NOTE = """
Install as a mod instead
  Put {jar} into the mods/ folder of a Fabric 1.21.11 instance; no injector needed.
  Do not use both at once.
"""

TESTED = "Tested on Prism Launcher with Minecraft 1.21.11 + Fabric.\n"

FOOTER = """
Credits
  Cracked by Skid Solutions
  technix                       sourcing / Frida scripts
  stutheguyy                    fixing
  bynbang / nitrogenoliquido    ZKM transformers
  dario                         AI models

Discord: https://discord.gg/x2FJyGrDNg
"""


def add(zf, arcname, data, mode):
    info = zipfile.ZipInfo(arcname, time.localtime()[:6])
    info.create_system = 3  # unix, so external_attr carries the permission bits
    info.external_attr = (0o100000 | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    zf.writestr(info, data)


def make_zip(out_dir, version, platform, injector, lib, jar):
    family = platform.split("-")[0]
    name = f"prestige-{version}-{platform}"
    jar_name = os.path.basename(jar)
    title, body = README[family].format(version=version, jar=jar_name).split("\n", 1)
    readme = title + "\n" + TESTED + body + MOD_NOTE.format(jar=jar_name) + FOOTER
    if family == "windows":
        readme = readme.replace("\n", "\r\n")
    out = os.path.join(out_dir, name + ".zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path, mode in ((injector, 0o755), (lib, 0o644), (jar, 0o644)):
            with open(path, "rb") as f:
                add(zf, f"{name}/{os.path.basename(path)}", f.read(), mode)
        add(zf, f"{name}/README.txt", readme.encode(), 0o644)
    print(f"  wrote {out} ({os.path.getsize(out) // 1024} KiB)")


def checksums(out_dir):
    lines = []
    for entry in sorted(os.listdir(out_dir)):
        path = os.path.join(out_dir, entry)
        if not os.path.isfile(path) or entry == "SHA256SUMS.txt":
            continue
        with open(path, "rb") as f:
            lines.append(f"{hashlib.sha256(f.read()).hexdigest()}  {entry}\n")
    with open(os.path.join(out_dir, "SHA256SUMS.txt"), "w", newline="\n") as f:
        f.writelines(lines)


if __name__ == "__main__":
    if len(sys.argv) == 8 and sys.argv[1] == "zip":
        make_zip(*sys.argv[2:])
    elif len(sys.argv) == 3 and sys.argv[1] == "checksums":
        checksums(sys.argv[2])
    else:
        sys.exit(__doc__)
