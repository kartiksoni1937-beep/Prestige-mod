#!/bin/sh
# Builds the Prestige payload jar (remapped for the production Fabric runtime), then the Rust injector and loader
# library, and collects PrestigeInjector + libprestige_native.{so,dylib} in dist/.
# Optional: set JAVA_HOME to pick the JDK used by Gradle.
set -eu

cd "$(dirname "$0")"

case "$(uname -s)" in
    Linux)  LIB=libprestige_native.so ;;
    Darwin) LIB=libprestige_native.dylib ;;
    *)      echo "unsupported OS: $(uname -s) (use build-injector.ps1 on Windows)" >&2; exit 1 ;;
esac

if [ -n "${JAVA_HOME:-}" ]; then
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi

./gradlew remapJar --console=plain
cargo build --release --manifest-path injector/Cargo.toml

mkdir -p dist
cp -f "injector/target/release/PrestigeInjector" "injector/target/release/$LIB" dist/
chmod +x dist/PrestigeInjector

# the same remapped jar is the Fabric mod (drop it into mods/)
for jar in build/libs/prestige-*.jar; do
    case "$jar" in *-dev.jar|*-sources.jar) continue ;; esac
    cp -f "$jar" dist/
done

echo "Done: dist/PrestigeInjector + dist/$LIB + dist/prestige-*.jar (Fabric mod)"
