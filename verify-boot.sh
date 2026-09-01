#!/usr/bin/env bash
#
# Boots a headless dedicated server, waits for it to reach "Done", then stops it.
#
# Mixins only fail at RUNTIME -- a bad @Mixin target compiles perfectly and then blows up
# on load. This is the cheapest way to catch that without a human in the loop, so run it
# after touching anything under mixin/.
#
#   ./verify-boot.sh          boot, verify, stop
#
set -euo pipefail

SANDBOX="${SANDBOX:-$HOME/mc-build-sandbox}"
LOG=neoforge/run/server/logs/latest.log
TIMEOUT="${TIMEOUT:-420}"

[ -f "$SANDBOX/env.sh" ] || { echo "no $SANDBOX/env.sh -- run ./setup-jdk.sh first" >&2; exit 1; }
# shellcheck disable=SC1090
source "$SANDBOX/env.sh"

mkdir -p neoforge/run/server
printf 'eula=true\n' > neoforge/run/server/eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nmax-players=1\nview-distance=4\nsimulation-distance=4\n' \
    > neoforge/run/server/server.properties

rm -f "$LOG"
echo "==> booting dedicated server (timeout ${TIMEOUT}s)"
timeout "$TIMEOUT" ./gradlew :neoforge:runServer >/dev/null 2>&1 &
GRADLE=$!

deadline=$((SECONDS + TIMEOUT))
status=timeout
while [ $SECONDS -lt $deadline ]; do
    if [ -f "$LOG" ]; then
        if grep -q 'Done (' "$LOG" 2>/dev/null; then status=ok; break; fi
        if grep -qE 'Mixin (apply|prepare) failed|MixinApplyError|Failed to load|Caused by' "$LOG" 2>/dev/null; then
            status=mixin; break
        fi
    fi
    kill -0 "$GRADLE" 2>/dev/null || { status=died; break; }
    sleep 3
done

PID=$(ps -eo pid,args | grep '[d]evlaunch.Main' | awk '{print $1}' | head -1 || true)
[ -n "$PID" ] && { kill -TERM "$PID" 2>/dev/null || true; sleep 6; }
kill -TERM "$GRADLE" 2>/dev/null || true
wait "$GRADLE" 2>/dev/null || true

case "$status" in
  ok)
    echo "==> BOOT OK"
    grep -E 'Done \(' "$LOG" | tail -1
    echo "--- mixin warnings (twilightforest ones are expected, it is not installed) ---"
    grep -E '\[mixin/\]: (Error|Warn)' "$LOG" | sed 's/^/    /' | head -10 || echo "    none"
    ;;
  mixin)  echo "==> MIXIN FAILURE"; grep -nE 'Mixin|Caused by' "$LOG" | head -20; exit 1 ;;
  died)   echo "==> SERVER DIED BEFORE READY"; tail -30 "$LOG" 2>/dev/null; exit 1 ;;
  *)      echo "==> TIMED OUT after ${TIMEOUT}s"; tail -20 "$LOG" 2>/dev/null; exit 1 ;;
esac
