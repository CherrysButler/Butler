#!/usr/bin/env bash
# Pull the debug build's mirrored Supabase session off the device.
#
#   scripts/dev-token.sh                 # print the access token
#   scripts/dev-token.sh --json          # print the whole session
#   scripts/dev-token.sh --curl <path>   # authenticated GET, e.g. --curl /chats/list
#   scripts/dev-token.sh --save <file>   # save the session (for surviving a reinstall)
#   scripts/dev-token.sh --restore <file>
#
# Debug builds only — the release variant never writes this file.
#
# The access token lasts ~1 hour. Do NOT refresh it from here: GoTrue rotates
# refresh tokens, so refreshing off-device invalidates the copy the app holds and
# silently signs the phone out. Just re-run this to get a fresh one.
set -euo pipefail

PKG="${BUTLER_PKG:-com.cherry.butler.debug}"
REMOTE="files/dev-session.json"
BACKEND="https://janitorai.com/mb"
CONFIG="app/src/main/java/com/cherry/butler/core/config/JanitorConfig.kt"

# The anon key is split across concatenated string literals in JanitorConfig.kt.
anon_key() {
  python - "$CONFIG" <<'PY'
import re, sys
src = open(sys.argv[1], encoding='utf-8').read()
block = re.search(r'SUPABASE_ANON_KEY\s*=\s*((?:\s*"[^"]*"\s*\+?)+)', src)
if not block:
    sys.exit("could not find SUPABASE_ANON_KEY in " + sys.argv[1])
print(''.join(re.findall(r'"([^"]*)"', block.group(1))))
PY
}

read_session() {
  adb exec-out run-as "$PKG" cat "$REMOTE" 2>/dev/null || true
}

case "${1:-}" in
  --restore)
    [ $# -ge 2 ] || { echo "usage: $0 --restore <file>" >&2; exit 2; }
    adb exec-out run-as "$PKG" sh -c "cat > $REMOTE" < "$2"
    echo "restored $2 -> $PKG/$REMOTE (relaunch the app to pick it up)"
    exit 0
    ;;
esac

session="$(read_session)"
if [ -z "$session" ]; then
  echo "No session file on device." >&2
  echo "Is the debug build installed and logged in? (run-as $PKG $REMOTE)" >&2
  exit 1
fi

token="$(printf '%s' "$session" | python -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"

case "${1:-}" in
  --json) printf '%s\n' "$session" ;;
  --save)
    [ $# -ge 2 ] || { echo "usage: $0 --save <file>" >&2; exit 2; }
    printf '%s' "$session" > "$2"
    echo "saved -> $2"
    ;;
  --curl)
    [ $# -ge 2 ] || { echo "usage: $0 --curl <path-or-url>" >&2; exit 2; }
    url="$2"; case "$url" in http*) ;; *) url="$BACKEND$url" ;; esac
    curl -s "$url" \
      -H "Authorization: Bearer $token" \
      -H "apikey: $(anon_key)" \
      -H 'Content-Type: application/json'
    ;;
  *) printf '%s\n' "$token" ;;
esac
