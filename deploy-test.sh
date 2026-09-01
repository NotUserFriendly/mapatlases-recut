#!/usr/bin/env bash
#
# Builds and deploys the mod into a Prism Launcher instance for hands-on testing.
# Re-run after any change; it replaces the jar and leaves the world alone.
#
#   ./deploy-test.sh                       build and deploy
#   ./deploy-test.sh --no-build            deploy the existing jar
#   INSTANCE="/path/to/instance" ./deploy-test.sh
#
set -euo pipefail

SANDBOX="${SANDBOX:-$HOME/mc-build-sandbox}"
INSTANCE="${INSTANCE:-/home/locad/snap/prismlauncher-alpo/common/instances/1.21.1 Test Platform}"
BUILD=1
[ "${1:-}" = "--no-build" ] && BUILD=0

say(){ printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok(){ printf '    \033[32m✓\033[0m %s\n' "$*"; }
die(){ printf '\n\033[1;31mERROR:\033[0m %s\n\n' "$*" >&2; exit 1; }

[ -d "$INSTANCE" ] || die "no instance at $INSTANCE"
MODS="$INSTANCE/minecraft/mods"
CONFIG="$INSTANCE/minecraft/config"

if [ "$BUILD" -eq 1 ]; then
    say "Building"
    [ -f "$SANDBOX/env.sh" ] || die "no $SANDBOX/env.sh -- run ./setup-jdk.sh"
    # shellcheck disable=SC1090
    source "$SANDBOX/env.sh"
    ./gradlew build -q >/dev/null || die "build failed"
    ok "built"
fi

JAR=$(find neoforge/build/libs -name 'map_atlases_recut-*-neoforge.jar' ! -name '*sources*' | head -1)
[ -n "$JAR" ] || die "no neoforge jar found -- build first"

say "Deploying to: $INSTANCE"
mkdir -p "$MODS" "$CONFIG"

# our jar: clear old versions so two builds cannot both load
find "$MODS" -name 'map_atlases_recut-*.jar' -delete 2>/dev/null || true
cp "$JAR" "$MODS/"
ok "$(basename "$JAR")"

# required dependency, pulled from the gradle cache so versions always match the build
for dep in moonlight-neoforge codecui-neoforge; do
    src=$(find "$SANDBOX/gradle/caches/modules-2" -name "${dep}-*.jar" ! -name '*sources*' ! -name '*javadoc*' | head -1)
    if [ -n "$src" ]; then
        find "$MODS" -name "${dep}-*.jar" -delete 2>/dev/null || true
        cp "$src" "$MODS/"
        ok "$(basename "$src")"
    else
        echo "    ! $dep not in the gradle cache (build once to fetch it)"
    fi
done

# turn on the counters Gate 1 reads, without clobbering anything else
TOML="$CONFIG/map_atlases_recut-common.toml"
if [ -f "$TOML" ]; then
    if grep -q '^\s*debug_map_updates' "$TOML"; then
        sed -i 's/^\(\s*debug_map_updates\s*=\s*\).*/\1true/' "$TOML"
        ok "debug_map_updates = true"
    fi
else
    echo "    (config not generated yet; launch once, then re-run to flip debug_map_updates)"
fi

say "Ready"
echo "    Launch the '$(basename "$INSTANCE")' instance in Prism."
echo "    Gate 1 procedure is in BUILD.md; watch the log for:"
echo "      map scans in last 30s: N performed, M skipped"
