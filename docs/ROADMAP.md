# What's next

Not a plan with dates. The things that are known to be missing or unproven, roughly in the
order they matter.

## Before anyone else installs it

- A release keystore and a `release` signing config. The key is forever; choose it once.
- Discord sign-in exercised end to end. It's wired exactly like Google, but nobody has
  tapped it.
- The e-mailed-code sign-in run once. Same Turnstile step as the password path, untested.
- A privacy page a store listing can link to. `TRUST.md` is it; it needs a URL.

## Known gaps

- **Janitor Router.** Reads work; every write needs Janitor Plus. The screen is built from
  the API's validation messages, not a successful run. Needs a Plus account to finish.
- **Apple sign-in.** Needs an Apple developer account on the Janitor side; nothing to do
  here until then.
- **Replies to comments on published chats.** The thread shape is known (`parent_id`),
  the write isn't.
- **Reasoning deltas.** Where `enable_reasoning` output appears in the stream was never
  captured; the parser handles `<think>` tags, which is what proxies send.
- **Tablets.** The phone is the shipped class; a tablet gets a stretched phone.
- **Daylight.** It works and it's checked, but less time has gone into it than the dark looks.

## Would be good

- Reproducible builds and an F-Droid listing. That's the strongest answer to "can I trust
  this APK" there is.
- A baseline profile shipped with the release, for the first-launch jank on cold phones.
- Search inside a chat.

## Declined, with reasons

- Author's note: the system prompt covers it.
- Bookmarks inside chats: the archive and folders do the job at the list level.
- Syncing the Customize text settings with Janitor: they stay on the phone; Janitor's
  values are web CSS and don't map.
- Any telemetry, ever. See `TRUST.md`.

## Working rules

- Compile after every file group; install at checkpoints; verify on the device before
  calling anything done.
- Shapes come from `docs/JANITOR_API.md` ✅ sections or from a probe with
  `scripts/dev-token.sh --curl`. Never invent a field.
- Anything that touches a proxy key is reviewed for logging and mirroring before commit.
- Measure on `perf`, never on `debug`.
- Commit messages say what was verified, not what was typed.
