# Performance

Measured 2026-10-03 on the owner's phone (Android 15, 1080×2436), Butler's `perf` build
(R8, speed-profile compiled) against the official app, com.janitor.ai 2.6.0. Same
gestures for both: `scripts/perf-scroll.sh 10` (ten hard flings) on the home grid.

| | Butler | Official |
|---|---|---|
| Home grid, janky frames | 3.6–4.9 % | 7.4 % |
| Home grid, 99th percentile frame | 48 ms | 109 ms |
| Long chat (106 messages), janky frames | 1.3 % (p50 9 ms) | — |
| Cold start to first frame | 226–253 ms | 357–395 ms |
| Memory after browsing (PSS) | 235–280 MB | 629 MB |

Where Butler's memory goes, from `dumpsys gfxinfo`: about 61 MB is the system's window
buffers (six 10 MB frames, outside the app's control), 30 MB is decoded pictures, 13 MB
GPU caches. Pictures are decoded at exactly their on-screen pixel size and held in a
memory cache of a tenth of the heap; older ones come back from a 128 MB disk cache.

What keeps it fast:

- Every image request is sized to its slot; NSFW blur is a 10-pixel decode, not a shader.
- Lists prefetch whole rows ahead in the fling direction (`AheadPrefetch.kt`).
- Card previews (`Character.blurb`) are worked out off the main thread when a page loads.
- The Browse card skips a rounded clip it doesn't need (a clip costs a mask per frame).
- `baseline-prof.txt` compiles all of Butler's own code ahead of time on install.
- Chat streaming writes to Room at most every few hundred ms; the screen reads tokens
  from memory.

Re-measure on `perf` builds only: debug builds are interpreted and carry Compose's
runtime checks, so their numbers say nothing.
