#!/usr/bin/env bash
# Measure scroll smoothness of whatever list is on screen.
#
#   scripts/perf-scroll.sh            # 5 hard flings, print the frame summary
#   scripts/perf-scroll.sh 8          # 8 flings
#   scripts/perf-scroll.sh --aot 5    # force AOT (speed-profile) first — use on perf builds
#
# Build the honest variant first:  ./gradlew installPerf   (installs over the debug app)
# Debug builds are not AOT-compiled and carry Compose's runtime checks; their numbers
# are noise. Compare perf builds against perf builds.
set -euo pipefail

PKG="${BUTLER_PKG:-com.cherry.butler.debug}"
if [ "${1:-}" = "--aot" ]; then
  shift
  adb shell cmd package compile -m speed-profile -f "$PKG" >/dev/null
fi
N="${1:-5}"

read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/' | tr -d '\r')
X=$((W / 2)); Y1=$((H * 80 / 100)); Y2=$((H * 25 / 100))

adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
for _ in $(seq "$N"); do
  adb shell input swipe "$X" "$Y1" "$X" "$Y2" 60
  adb shell sleep 0.9
done
adb shell sleep 1

adb shell dumpsys gfxinfo "$PKG" \
  | sed -n '/^Stats since/,/HISTOGRAM/p' \
  | grep -E "Total frames|Janky|percentile|Number (Missed|Slow|High input|Frame deadline)" \
  | tr -d '\r'
