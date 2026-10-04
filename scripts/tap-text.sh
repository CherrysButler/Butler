#!/usr/bin/env bash
# Tap the first on-screen element whose text or content description contains a string.
#
#   scripts/tap-text.sh "Eva"            # tap it
#   scripts/tap-text.sh "Eva" --print    # only print its centre
#   scripts/tap-text.sh "Eva" --long     # long-press it
#   scripts/tap-text.sh "=Edit"          # exact text or description, not a substring
#
# Reads the accessibility tree through uiautomator, which sees Compose text, so test
# steps can name what they mean instead of hard-coding screen coordinates.
set -euo pipefail
# Git Bash on Windows rewrites "/sdcard/..." into a Windows path; adb needs it verbatim.
export MSYS_NO_PATHCONV=1

NEEDLE="$1"; MODE="${2:-}"
DUMP="$(mktemp -t butler-ui.XXXXXX.xml)"
trap 'rm -f "$DUMP"' EXIT
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb exec-out cat /sdcard/ui.xml > "$DUMP"

POINT=$(python - "$NEEDLE" "$(cygpath -w "$DUMP")" <<'PY'
import re, sys, html
needle, path = sys.argv[1].lower(), sys.argv[2]
exact = needle.startswith("=")
if exact: needle = needle[1:]
xml = open(path, encoding="utf-8", errors="replace").read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    t = re.search(r' text="([^"]*)"', node)
    d = re.search(r'content-desc="([^"]*)"', node)
    text = html.unescape(t.group(1)) if t else ""
    desc = html.unescape(d.group(1)) if d else ""
    hit = (needle in (text.lower().strip(), desc.lower().strip())) if exact else (needle in text.lower() or needle in desc.lower())
    if hit:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
[ -n "$POINT" ] || { echo "not found: $NEEDLE" >&2; exit 1; }
set -- $POINT
case "$MODE" in
  --print) echo "$1 $2" ;;
  --long) adb shell input swipe "$1" "$2" "$1" "$2" 700 ;;
  *) adb shell input tap "$1" "$2" ;;
esac
