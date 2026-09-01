#!/usr/bin/env bash
#
# Map Atlases Recut — Phase 0 environment setup.
#
# Installs TWO self-contained JDKs and corrals every Gradle/NeoForge cache into
# one sandbox directory. No sudo, no system changes, nothing written outside
# $SANDBOX except the git fetch on an existing clone.
#
#   JDK 25  runs Gradle. Upstream's build plugins (com.possible-triangle:*)
#           publish module metadata demanding org.gradle.jvm.version >= 25.
#   JDK 21  is the compile toolchain — Minecraft 1.21.1 targets Java 21.
#
#   ./setup-jdk.sh            set up both JDKs + env, verify, report
#   ./setup-jdk.sh --build    also run the baseline build (slow: 10-30 min)
#
# To undo absolutely everything:  rm -rf ~/mc-build-sandbox
#
set -euo pipefail

SANDBOX="${SANDBOX:-$HOME/mc-build-sandbox}"
REPO="${REPO:-$HOME/workingdir/gits/mapatlases-neoforge}"
GRADLE_JDK=25   # runs Gradle itself
TARGET_JDK=21   # compile toolchain for MC 1.21.1
RUN_BUILD=0

for a in "$@"; do
  case "$a" in
    --build) RUN_BUILD=1 ;;
    -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) printf 'unknown argument: %s (try --help)\n' "$a" >&2; exit 2 ;;
  esac
done

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '    \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '    \033[33m!\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31mERROR:\033[0m %s\n\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- preflight --
say "Preflight"

for c in curl tar git find awk; do
  command -v "$c" >/dev/null || die "'$c' not found on PATH"
done
ok "curl, tar, git, find, awk present"

case "$(uname -m)" in
  x86_64)  ARCH=x64 ;;
  aarch64) ARCH=aarch64 ;;
  *) die "unsupported architecture: $(uname -m)" ;;
esac
ok "architecture: $ARCH"

avail_kb=$(df -Pk "$HOME" | awk 'NR==2 {print $4}')
if [ "$avail_kb" -lt 15000000 ]; then
  warn "only $((avail_kb/1024/1024)) GB free in \$HOME — the decompile plus two JDKs wants ~12 GB"
else
  ok "disk: $((avail_kb/1024/1024)) GB free in \$HOME"
fi

mem_kb=$(awk '/MemAvailable/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)
if [ "$mem_kb" -gt 0 ] && [ "$mem_kb" -lt 6000000 ]; then
  warn "only $((mem_kb/1024/1024)) GB RAM available — the build asks for -Xmx5G"
elif [ "$mem_kb" -gt 0 ]; then
  ok "memory: $((mem_kb/1024/1024)) GB available"
fi

mkdir -p "$SANDBOX"

# ------------------------------------------------------------------- jdks ----
# install_jdk <feature-version>  ->  echoes the resulting directory
install_jdk() {
  local ver="$1" dir url tmp
  dir="$(find "$SANDBOX" -maxdepth 1 -type d -name "jdk-${ver}*" 2>/dev/null | sort | head -1 || true)"
  if [ -n "$dir" ] && [ -x "$dir/bin/java" ]; then
    printf '%s' "$dir"; return 0
  fi
  url="https://api.adoptium.net/v3/binary/latest/${ver}/ga/linux/${ARCH}/jdk/hotspot/normal/eclipse"
  tmp="$SANDBOX/.jdk${ver}.tar.gz"
  printf '    downloading Eclipse Temurin %s (%s)...\n' "$ver" "$ARCH" >&2
  curl -fL --progress-bar -o "$tmp" "$url" >&2 \
    || { printf 'download failed for JDK %s\n' "$ver" >&2; return 1; }
  tar xzf "$tmp" -C "$SANDBOX" >&2 || { printf 'extract failed for JDK %s\n' "$ver" >&2; return 1; }
  rm -f "$tmp"
  dir="$(find "$SANDBOX" -maxdepth 1 -type d -name "jdk-${ver}*" | sort | head -1 || true)"
  [ -n "$dir" ] && [ -x "$dir/bin/java" ] || { printf 'no usable jdk-%s* directory\n' "$ver" >&2; return 1; }
  printf '%s' "$dir"
}

say "JDK ${GRADLE_JDK} — Gradle runtime"
GRADLE_JDK_DIR="$(install_jdk "$GRADLE_JDK")" || die "could not install JDK ${GRADLE_JDK}"
ok "$GRADLE_JDK_DIR"

say "JDK ${TARGET_JDK} — compile toolchain"
TARGET_JDK_DIR="$(install_jdk "$TARGET_JDK")" || die "could not install JDK ${TARGET_JDK}"
ok "$TARGET_JDK_DIR"

# ---------------------------------------------------------------------- env --
say "Environment"

mkdir -p "$SANDBOX/gradle"

cat > "$SANDBOX/env.sh" <<ENVEOF
# Source before any Gradle work:  source $SANDBOX/env.sh
# JAVA_HOME points at the JDK that RUNS Gradle. The mod still compiles against
# Java $TARGET_JDK via a Gradle toolchain — see gradle/gradle.properties.
export JAVA_HOME="$GRADLE_JDK_DIR"
export GRADLE_USER_HOME="$SANDBOX/gradle"
export PATH="\$JAVA_HOME/bin:\$PATH"
ENVEOF
ok "wrote $SANDBOX/env.sh"

# Gradle will not find toolchains in a non-standard prefix unless told.
props="$SANDBOX/gradle/gradle.properties"
tmpprops="$(mktemp)"
[ -f "$props" ] && grep -v '^org\.gradle\.java\.installations\.paths=' "$props" > "$tmpprops" || true
{
  cat "$tmpprops"
  printf 'org.gradle.java.installations.paths=%s,%s\n' "$TARGET_JDK_DIR" "$GRADLE_JDK_DIR"
} > "$props"
rm -f "$tmpprops"
ok "declared toolchains in $props"

# shellcheck disable=SC1090
source "$SANDBOX/env.sh"

ver="$(java -version 2>&1 | head -1)"
case "$ver" in
  *"\"${GRADLE_JDK}"*) ok "gradle runtime: $ver" ;;
  *) die "expected Java ${GRADLE_JDK} on PATH, got: $ver" ;;
esac
tver="$("$TARGET_JDK_DIR/bin/java" -version 2>&1 | head -1)"
ok "toolchain:      $tver"
ok "GRADLE_USER_HOME=$GRADLE_USER_HOME"

# --------------------------------------------------------------------- repo --
say "Upstream clone"

if [ ! -d "$REPO/.git" ]; then
  warn "no clone at $REPO — skipping (set REPO=... to point elsewhere)"
else
  if [ "$(git -C "$REPO" rev-parse --is-shallow-repository)" = "true" ]; then
    printf '    deepening shallow clone...\n'
    git -C "$REPO" fetch --unshallow --quiet && ok "full history fetched"
  else
    ok "already a full clone"
  fi
  ok "HEAD: $(git -C "$REPO" log --oneline -1)"
fi

# -------------------------------------------------------------------- build --
if [ "$RUN_BUILD" -eq 1 ]; then
  say "Baseline build (unmodified tree)"
  [ -d "$REPO" ] || die "no clone at $REPO to build"
  warn "first run decompiles and remaps Minecraft — expect 10-30 minutes"
  cd "$REPO"
  ./gradlew build
  say "Baseline build GREEN"
  ok "Phase 0 build gate passed"
else
  say "Next"
  cat <<NEXT
    Run the baseline build when ready (10-30 min, first time only):

      source $SANDBOX/env.sh
      cd $REPO
      ./gradlew build

    Or re-run this script with --build to chain it.
NEXT
fi

say "Done"
cat <<DONE
    Everything lives under: $SANDBOX
      Gradle runtime  $GRADLE_JDK_DIR
      Compile target  $TARGET_JDK_DIR
      Gradle caches   $SANDBOX/gradle
      env             $SANDBOX/env.sh

    Remove all of it with:  rm -rf $SANDBOX

    Source the env in any new shell before Gradle work:
      source $SANDBOX/env.sh
DONE
